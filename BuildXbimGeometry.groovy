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
// - doCleanBuild

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildVersion 
   if('Release' == params.buildConfig) {
      buildVersion = XbimStages.generateBuildVersion(params.buildMajor, params.buildMinor)
   } else {
      buildVersion = XbimStages.generateSnapshotVersion(params.buildMajor, params.buildMinor)
   }

   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   echo "Building package version ${packageVersion}"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         XbimStages.git('reset --hard')
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
          XbimStages.nuget('update ./Xbim.Geometry.Engine.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      XbimStages.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      XbimStages.nuget('restore Xbim.Geometry.Engine.sln')
      XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:restore /p:RestoreSources=${params.localNugetStore}")

      // Replace versions native engine version identifiers
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.0.0\"','\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}.${buildVersion.build}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION ${buildVersion.major},${buildVersion.minor},0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
   }

   stage('Build') {
       // Build for both platforms
       for(platform in ['x86','x64']) {
          for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
             XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
          }
       }
       // Pack nuget packages
       powershell "dotnet clean Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig}"
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${params.localNugetStore} /p:PackageVersion=${packageVersion}"
       powershell "dotnet clean Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${params.localNugetStore} /p:PackageVersion=${packageVersion}"
   }
}