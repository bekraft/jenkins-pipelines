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
   def Utils = load "Utils.groovy"
   def buildVersion
   def packageVersion   
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       } else {
         Utils.git('reset --hard')
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.xbimBranch}", url: "${params.xbimRepository}"
      
      if('Release' == params.buildConfig) {
         buildVersion = Utils.generateBuildVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
      } else {
         buildVersion = Utils.generateSnapshotVersion(params.buildMajor, params.buildMinor, params.buildPreQualifier)
      }

      packageVersion = Utils.generaterPackageVersion(buildVersion)
      echo "Building package version ${packageVersion}"
      currentBuild.displayName = "#${BUILD_NUMBER} (${packageVersion})"      
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      Utils.cleanUpNupkgs()

      // Cleaning nupkg builds
      //powershell "dotnet clean Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig}"
      //powershell "dotnet clean Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig}"

      // Restore & update via nuget
      Utils.initEnv()
      if(params.useLocalArtifacts)
         Utils.enableNugetCache(Utils.localNugetCache)
      else
         Utils.disableNugetCache(Utils.localNugetCache)
                  
      Utils.nuget('sources list')
      
      // Remove project not needed
      powershell "dotnet sln ./Xbim.Geometry.Engine.sln remove ./Xbim.Geometry.Regression/XbimRegression.csproj"
      
      if(params.doUpdatePackages) {
          // Update all packages
          Utils.nuget('update ./Xbim.Geometry.Engine.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      Utils.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      //Utils.nuget('restore Xbim.Geometry.Engine.sln')
      Utils.msbuild("./Xbim.Geometry.Engine.sln /t:restore /p:RestoreSources=${LOCAL_NUGET_CACHE}")

      // Replace versions native engine version identifiers
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.0.0\"','\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}.${buildVersion.build}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace 'FILEVERSION ${buildVersion.major},${buildVersion.minor},0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
   }

   stage('Build') {
       // Build for both platforms
       for(platform in ['x86','x64']) {
          for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
             Utils.msbuild("./Xbim.Geometry.Engine.sln /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")             
             //Utils.msbuild("./Xbim.Geometry.Engine/Xbim.Geometry.Engine.vcxproj /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
             //Utils.msbuild("./Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
             //Utils.msbuild("./Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj /r /t:${target} /p:Configuration=${params.buildConfig} /p:Platform=${platform}")
          }
       }
       
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
   }
}
