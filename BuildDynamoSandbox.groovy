// DynamoSandbox Build

// Env:
// - LOCAL_NUGET_CACHE

// Parameters:
// - doCleanUpWs (boolean)
// - repository (URL)
// - branch (name)
// - buildConfig (Release, Debug)
// - doCleanBuild (boolean)

node {
   checkout scm
   def Utils = load "Utils.groovy"
   def prebuiltPckgPath = "${LOCAL_NUGET_CACHE}"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         Utils.git('reset --hard')
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.branch}", url: "${params.repository}"
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      Utils.cleanUpNupkgs()

      // Restore & update via nuget
      Utils.addLocalNugetCache(LOCAL_NUGET_CACHE)
      
      // Set nuget cache path
      Utils.nuget("config -set repositoryPath=${LOCAL_NUGET_CACHE}")
      Utils.nuget('sources list')
            
      // Restore entire solution dependencies invoking nuget and msbuild
      Utils.msbuild("./src/Dynamo.All.sln /t:restore /p:RestoreSources=${LOCAL_NUGET_CACHE}")
   }

   stage('Build') {
       for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
          Utils.msbuild("""./src/Dynamo.All.sln /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform="Any CPU"""")
       }
   }
}
