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

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildVersion = XbimStages.generateBuildVersion(5,1,new Date())
   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   println("Building package version ${packageVersion}")
   
   def builtNugets = "${WORKSPACE}/nupkgs"
   
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
          XbimStages.nuget('update')
      }
      // Replace versions
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"5.1.0.0\"','\"FileVersion\", \"${packageVersion}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION 5,1,0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
   }

   stage('Build') {
       // Build Engine for x86 and x64 mode
       XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:build /p:Configuration=${buildConfig} /p:Platform=x64")
       XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:build /p:Configuration=${buildConfig} /p:Platform=x86")
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${buildConfig} -o Xbim.Geometry.Engine.Interop/bin/${buildConfig} /p:PackageVersion=${packageVersion}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${buildConfig} -o Xbim.ModelGeometry.Scene/bin/${buildConfig} /p:PackageVersion=${packageVersion}"
   }
}