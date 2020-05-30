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
   def XbimStages = load "Xbim.Stages.groovy"
   def prebuiltPckgPath = "${LOCAL_NUGET_CACHE}"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         XbimStages.git('reset --hard')
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.branch}", url: "${params.repository}"
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      XbimStages.cleanUpNupkgs()

      // Restore & update via nuget
      XbimStages.addLocalNugetCache(LOCAL_NUGET_CACHE)
      
      // Set nuget cache path
      XbimStages.nuget("config -set repositoryPath=${LOCAL_NUGET_CACHE}")
      XbimStages.nuget('sources list')
            
      // Restore entire solution dependencies invoking nuget and msbuild
      XbimStages.msbuild("./src/Dynamo.All.sln /t:restore /p:RestoreSources=${LOCAL_NUGET_CACHE}")
   }

   stage('Build') {
       for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
          XbimStages.msbuild("""./src/Dynamo.All.sln /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform="Any CPU"""")
       }
   }
}
