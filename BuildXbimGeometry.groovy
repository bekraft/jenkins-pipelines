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
   def buildTime = params.buildIdentifier.trim.empty ? new Date() : params.buildIdentifier
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
      XbimStages.nuget('sources list')
      XbimStages.nuget('restore Xbim.Geometry.Engine.sln')
      if(params.doUpdatePackages) {
          XbimStages.addLocalNugetCache(params.localNugetStore)
          XbimStages.nuget('update')
      }
      // Replace versions
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"5.1.0.0\"','\"FileVersion\", \"${packageVersion}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION 5,1,0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      // Clean up
      XbimStages.cleanUpNupkgs()
   }

   stage('Build') {
       // Build Engine for x86 and x64 mode
       XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:build /p:Configuration=${params.buildConfig} /p:Platform=x64")
       XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:build /p:Configuration=${params.buildConfig} /p:Platform=x86")
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
   }

   stage('Locally publishing') {
      echo 'Congratulation! All binaries have been built!'
      XbimStages.deployLocally(params.localNugetStore)
   }
}