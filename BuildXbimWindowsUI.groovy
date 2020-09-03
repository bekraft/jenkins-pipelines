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
// - buildPreQualifier (string)

node {
   checkout scm
   def Utils = load "Utils.groovy"
   def buildVersion 
   if('Release' == params.buildConfig) {
      buildVersion = Utils.generateBuildVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
   } else {
      buildVersion = Utils.generateSnapshotVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
   }

   def packageVersion = Utils.generaterPackageVersion(buildVersion)
   echo "Building package version ${packageVersion}"
   currentBuild.displayName = "#${BUILD_NUMBER} (${packageVersion})"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         Utils.git('reset --hard')
         Utils.git('clean -fd')
       }       
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.xbimBranch}", url: "${params.xbimRepository}" 
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      Utils.cleanUpNupkgs()

      // Cleaning nupkg builds
      powershell "dotnet clean Xbim.Presentation/Xbim.Presentation.csproj -c ${params.buildConfig}"
      powershell "dotnet clean XbimXplorer/XbimXplorer.csproj -c ${params.buildConfig}"

      // Restore & update via nuget
      Utils.addLocalNugetCache(params.localNugetStore)
      Utils.nuget("config -set repositoryPath=${params.localNugetStore}")
      Utils.nuget('sources list')

      if(params.doUpdatePackages) {
          // Update all packages
          Utils.nuget('update ./Xbim.WindowsUI.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      Utils.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      Utils.nuget('restore Xbim.WindowsUI.sln')
      Utils.msbuild("./Xbim.WindowsUI.sln /t:restore /p:RestoreSources=${params.localNugetStore}")
   }

   stage('Build') {
       def prebuiltPckgPath = "${WORKSPACE}/${params.buildConfig}"
       // Pack nuget packages
       powershell "dotnet pack Xbim.Presentation/Xbim.Presentation.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
       powershell "dotnet remove XbimXplorer/XbimXplorer.csproj reference ../Xbim.Presentation/Xbim.Presentation.csproj"
       powershell "dotnet add XbimXplorer/XbimXplorer.csproj package Xbim.WindowsUI -s ${prebuiltPckgPath} -v ${packageVersion}"
       powershell "dotnet msbuild XbimXplorer/XbimXplorer.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
   }

   stage('Archive XbimXplorer') {
      archiveArtifacts artifacts: "XbimXplorer/${params.buildConfig}"
   }
}