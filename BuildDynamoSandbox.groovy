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
      Uitls.initEnv()
      Utils.nuget('sources list')
            
      // Restore entire solution dependencies invoking nuget and msbuild
      Utils.msbuild("./src/Dynamo.All.sln /t:restore")
   }

   stage('Build') {
       for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
          Utils.msbuild("""./src/Dynamo.All.sln /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform="Any CPU" """)
       }
   }
}
