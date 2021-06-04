// Xbim.Geometry Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimGeometry/blob/master/azure-pipelines.yml)

// Env:
// - NUGET_PRIVATE_URL
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
// - deployArtifacts (boolean)

node {
   checkout scm
   def Utils = load "Utils.groovy"
   def localPackageFolder = "${WORKSPACE}/deployedpackages"
   def buildPropsAdditionals
   def buildVersion
   def packageVersion

   if ('Release' != params.buildConfig)
		buildPropsAdditionals = "-p:IncludeSymbols=true -p:SymbolPackageFormat=snupkg"
	else
		buildPropsAdditionals = ""

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

      packageVersion = Utils.generatePackageVersion(buildVersion)
      echo "Building package version ${packageVersion}"
      currentBuild.displayName = "#${BUILD_NUMBER} (${packageVersion})"      
   }
   
   stage('Preparation') {
      // Clean up old binary packages
      Utils.cleanUpNupkgs()

		// Restore & update via nuget
		Utils.initEnv("nuget.config")
		if (params.useLocalArtifacts) {
			Utils.enableNugetCache(Utils.nugetDeployServerName())
         Utils.enableNugetCache(Utils.nugetDeployServerName(), "nuget.config")
		} else {	
			Utils.disableNugetCache(Utils.nugetDeployServerName())
		}
                  
      Utils.nuget('sources list -ConfigFile nuget.config')
      
      // Remove project not needed
      powershell "dotnet sln ./Xbim.Geometry.Engine.sln remove ./Xbim.Geometry.Regression/XbimRegression.csproj"
      powershell "dotnet sln ./Xbim.Geometry.Engine.sln remove ./Xbim.Geometry.Engine.Interop.Tests/Xbim.Geometry.Engine.Interop.Tests.csproj"
      def prjs = Utils.readSolutionProjects('./Xbim.Geometry.Engine.sln')
      for (prj in prjs) {
         echo prj.folder
      }

      if(params.doUpdatePackages) {
          // Update all packages
          Utils.nuget('update ./Xbim.Geometry.Engine.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      Utils.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      Utils.msbuild("./Xbim.Geometry.Engine.sln /t:restore")

      // Replace versions native engine version identifiers
      for(attr in ['FileVersion', 'FILEVERSION', 'ProductVersion', 'PRODUCTVERSION']) {
         powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '\"${attr}\", \"${buildVersion.major}.${buildVersion.minor}.0.0\"','\"FileVersion\", \"${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}.${buildVersion.build}\"') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
         powershell "((Get-Content -path Xbim.Geometry.Engine\\app.rc -Raw) -replace '${attr} ${buildVersion.major},${buildVersion.minor},0,0','FILEVERSION ${buildVersion.major},${buildVersion.minor},${buildVersion.release},${buildVersion.build}') | Set-Content -Path Xbim.Geometry.Engine\\app.rc" 
      }
   }

   stage('Build') {
       def assemblyVersion = Utils.generaterAssemblyVersion(buildVersion)
       def buildProps = "/p:PackageVersion=${packageVersion} /p:Version=${assemblyVersion} /p:AssemblyVersion=${assemblyVersion} /p:GeneratePackageOnBuild=false ${buildPropsAdditionals}"

       // Build for both platforms
       for(platform in ['Any CPU']) {
          for(target in (params.doCleanBuild ? ['clean', 'build'] : ['build'])) {
             Utils.msbuild("./Xbim.Geometry.Engine.sln /r /t:${target} /p:Configuration=\"${params.buildConfig}\" /p:Platform=\"${platform}\" ${buildProps}")
          }
       }
       
       // Pack nuget packages
       powershell "dotnet pack Xbim.Geometry.Engine.Interop/Xbim.Geometry.Engine.Interop.csproj -c ${params.buildConfig} -o ${localPackageFolder} ${buildProps}"
       powershell "dotnet pack Xbim.ModelGeometry.Scene/Xbim.ModelGeometry.Scene.csproj -c ${params.buildConfig} -o ${localPackageFolder} ${buildProps}"
   }

	stage('Publish & archive') {
		Utils.enableNugetCache(Utils.nugetDeployServerName())
		if (params.deployArtifacts) {
			Utils.deploy(NUGET_PRIVATE_URL, 'NugetPrivateApiKey')
      }
		archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true
	}
}
