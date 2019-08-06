// Xbim.WindowsUI Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimWindowsUI/blob/master/azure-pipelines.yml)

// Parameters:
// - doCleanUpWs (boolean)
// - doUpdatePackages (boolean)
// - localNugetStore (local path)
// - xbimRepository (URL)
// - xbimBranch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildIdentifier (string or empty)

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildTime = params.buildIdentifier.empty ? new Date() : params.buildIdentifier
   def buildVersion = XbimStages.generateBuildVersion(params.buildMajor, params.buildMinor, buildTime)
   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   echo "Building package version ${packageVersion}"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.xbimBranch}", url: "${params.xbimRepository}" 
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      XbimStages.cleanUpNupkgs()
      // Restore & update via nuget
      XbimStages.addLocalNugetCache(params.localNugetStore)
      XbimStages.nuget("config -set repositoryPath=${params.localNugetStore}")
      XbimStages.nuget('sources list')

      if(params.doUpdatePackages) {
          // Update all packages
          XbimStages.nuget('update ./Xbim.WindowsUI.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      XbimStages.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      XbimStages.nuget('restore Xbim.WindowsUI.sln')
      XbimStages.msbuild("./Xbim.WindowsUI.sln /t:restore /p:RestoreSources=${params.localNugetStore}")
   }

   stage('Build') {
       def prebuiltPckgPath = "${WORKSPACE}/${params.buildConfig}"
       // Pack nuget packages
       powershell "dotnet pack Xbim.Presentation/Xbim.Presentation.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
       powershell "dotnet remove XbimXplorer/XbimXplorer.csproj reference ../Xbim.Presentation/Xbim.Presentation.csproj"
       powershell "dotnet add XbimXplorer/XbimXplorer.csproj package Xbim.WindowsUI -s ${prebuiltPckgPath} -v ${packageVersion}"
       powershell "dotnet msbuild XbimXplorer/XbimXplorer.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
   }

   stage('Locally publishing') {
      echo 'Congratulation! All binaries have been built!'
      XbimStages.deployLocally(params.localNugetStore)
   }

   stage('Archive XbimXplorer') {
      archiveArtifacts artifacts: "XbimXplorer/${params.buildConfig}"
   }
}