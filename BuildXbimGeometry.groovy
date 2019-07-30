// Xbim.Geometry Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimGeometry/blob/master/azure-pipelines.yml)

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
      // Clean up
      XbimStages.cleanUpNupkgs()
      // Restore & update
      XbimStages.nuget('sources list')
      XbimStages.nuget('restore Xbim.Geometry.Engine.sln')
      if(params.doUpdatePackages) {
          XbimStages.addLocalNugetCache(params.localNugetStore)
          XbimStages.nuget("config -set repositoryPath=${params.localNugetStore}")
          XbimStages.nuget('update ./Xbim.Geometry.Engine.sln')
      }

      powershell 'dotnet add ./Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj package Xbim.Tessellator'

      // Replace versions
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"5.1.0.0\"','\"FileVersion\", \"${packageVersion}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION 5,1,0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
   }

   stage('Build') {
       // Try build 2 times; it will likely report errors 1st time, but seem to be needed for the succes of the 2nd build
       for(platform in ['x86','x64']) {
          for(target in ['clean', 'build', 'build']) {
             XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
          }
       }
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
   }

   stage('Locally publishing') {
      echo 'Congratulation! All binaries have been built!'
      XbimStages.deployLocally(params.localNugetStore)
   }
}