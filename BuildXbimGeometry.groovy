// Xbim.Geometry Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimGeometry/blob/master/azure-pipelines.yml)

// Env:
// - LOCAL_NUGET_CACHE

// Parameters:
// - doCleanUpWs (boolean)
// - doUpdatePackages (boolean)
// - xbimRepository (URL)
// - xbimBranch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - doCleanBuild (boolean)
// - buildPreQualifier (string)
// - useLocalArtifacts (boolean)

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildVersion 
   if('Release' == params.buildConfig) {
      buildVersion = XbimStages.generateBuildVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
   } else {
      buildVersion = XbimStages.generateSnapshotVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
   }

   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   echo "Building package version ${packageVersion}"
   currentBuild.displayName = "#${BUILD_NUMBER} (${packageVersion})"

   def prebuiltPckgPath = "${LOCAL_NUGET_CACHE}"
   
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

      // Cleaning nupkg builds
      //powershell "dotnet clean Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig}"
      //powershell "dotnet clean Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig}"

      // Restore & update via nuget
      XbimStages.addLocalNugetCache(LOCAL_NUGET_CACHE)
      if(params.useLocalArtifacts)
         XbimStages.enableLocalNugetCache()
      else
         XbimStages.disableLocalNugetCache()
      
      // Set nuget cache path
      XbimStages.nuget("config -set repositoryPath=${LOCAL_NUGET_CACHE}")
      XbimStages.nuget('sources list')
      
      // Remove project not needed
      powershell "dotnet sln ./Xbim.Geometry.Engine.sln remove ./Xbim.Geometry.Regression/XbimRegression.csproj"
      
      if(params.doUpdatePackages) {
          // Update all packages
          XbimStages.nuget('update ./Xbim.Geometry.Engine.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      XbimStages.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      XbimStages.nuget('restore Xbim.Geometry.Engine.sln')
      XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:restore") // /p:RestoreSources=${LOCAL_NUGET_CACHE}")

      // Replace versions native engine version identifiers
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.0.0\"','\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}.${buildVersion.build}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION ${buildVersion.major},${buildVersion.minor},0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
   }

   stage('Build') {
       // Build for both platforms
       for(platform in ['x86','x64']) {
          for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
             XbimStages.msbuild("./Xbim.Geometry.Engine.sln /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")             
             //XbimStages.msbuild("./Xbim.Geometry.Engine/Xbim.Geometry.Engine.vcxproj /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
             //XbimStages.msbuild("./Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
             //XbimStages.msbuild("./Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
          }
       }
       
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
   }
}
