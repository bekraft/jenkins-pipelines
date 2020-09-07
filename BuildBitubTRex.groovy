import java.text.SimpleDateFormat

// BitubTRex Build

// Env:
// - LOCAL_NUGET_CACHE .. local Nuget cache
// - PROTOBUF_SRC .. protobuf src directory (for additional proto includes)

// Parameters:
// - doCleanUpWs (boolean)
// - branch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildPreQualifier (string)
// - useLocalArtifacts (boolean)
// - runTests (boolean)

node {
   checkout scm
   def Utils = load "Utils.groovy"
   def prebuiltPckgPath = "${LOCAL_NUGET_CACHE}"
   def buildVersion 
   def packageVersion   
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         Utils.git('reset --hard')
         Utils.git('clean -fd')
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.branch}", url: "https://github.com/bekraft/BitubTRex.git"
      
      if('Release' == params.buildConfig) {
         buildVersion = Utils.generateBuildVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
      } else {
         buildVersion = Utils.generateSnapshotVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
      }

      packageVersion = Utils.generatePackageVersion(buildVersion)
      echo "Building package version ${packageVersion}"
      currentBuild.displayName = "#${BUILD_NUMBER} (${packageVersion})"
   }
      
   stage('Preparation') {      
      Utils.cleanUpNupkgs()
      Utils.initEnv()
      Utils.enableNugetCache(Utils.localNugetCacheName())
      Utils.nuget('sources list')

      // Cleaning nupkg builds
      powershell "dotnet clean BitubTRex.sln -c ${params.buildConfig}"
   }

   if (params.runTests) {
      stage('Test') {      
         powershell "dotnet test BitubTRex.sln -c ${params.buildConfig} -s BitubTRex.runsettings"
      }
   } else {
      stage('No test') {
         echo "Skipped testing ..."
      }
   }
   
   stage('Build') { 
      def propsAdditional    
      if ('Debug' == params.buildConfig)
         propsAdditional = "-p:IncludeSymbols=true -p:SymbolPackageFormat=snupkg"
      else
         propsAdditional = ""

      def propsBuildVersion = Utils.buildVersionToDotNetProp(buildVersion)
      powershell "dotnet build BitubTRex.sln -c ${params.buildConfig} ${propsBuildVersion} ${propsAdditional}"
   }

   stage('Publish & archive') {
      powershell "dotnet pack BitubTRex.sln -c ${params.buildConfig}"
      archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true
   }
}