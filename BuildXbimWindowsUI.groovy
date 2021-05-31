// Xbim.WindowsUI Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimWindowsUI/blob/master/azure-pipelines.yml)

// Parameters:
// - doCleanUpWs (boolean)
// - doUpdatePackages (boolean)
// - xbimRepository (URL)
// - xbimBranch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildPreQualifier (string)

node {
   checkout scm
   def Utils = load "Utils.groovy"
   def prebuiltPckgPath = "${WORKSPACE}/${params.buildConfig}/nuget"
   def buildProps
   def buildVersion
   def packageVersion

   if ('Release' != params.buildConfig)
		buildProps = "-p:IncludeSymbols=true -p:SymbolPackageFormat=snupkg"
	else
		buildProps = ""
   
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

      // Cleaning nupkg builds
      powershell "dotnet clean Xbim.Presentation/Xbim.Presentation.csproj -c ${params.buildConfig}"
      powershell "dotnet clean XbimXplorer/XbimXplorer.csproj -c ${params.buildConfig}"

		// Restore & update via nuget
		Utils.initEnv("nuget.config")
		if (params.useLocalArtifacts) {
			Utils.enableNugetCache(Utils.nugetDeployServerName())
         Utils.enableNugetCache(Utils.nugetDeployServerName(), "nuget.config")
		} else {	
			Utils.disableNugetCache(Utils.nugetDeployServerName())
		}
                  
      Utils.nuget('sources list -ConfigFile nuget.config')

      if(params.doUpdatePackages) {
          // Update all packages
          Utils.nuget('update ./Xbim.WindowsUI.sln')
      }

      // Update Xbim packages (Xbim.Ifc, Xbim.Tesselator, ...?)
      Utils.updatePackages([], '^(Xbim).*')

      // Restore entire solution dependencies invoking nuget and msbuild
      Utils.nuget('restore Xbim.WindowsUI.sln')
      Utils.msbuild("./Xbim.WindowsUI.sln /t:restore")
   }

   stage('Build') {
       // Pack nuget packages
       powershell "dotnet pack Xbim.Presentation/Xbim.Presentation.csproj -c ${params.buildConfig} -o ${prebuiltPckgPath} /p:PackageVersion=${packageVersion}"
       powershell "dotnet remove XbimXplorer/XbimXplorer.csproj reference ../Xbim.Presentation/Xbim.Presentation.csproj"
       powershell "dotnet add XbimXplorer/XbimXplorer.csproj package Xbim.WindowsUI -s ${prebuiltPckgPath} -v ${packageVersion}"
       powershell "dotnet msbuild XbimXplorer/XbimXplorer.csproj -c ${params.buildConfig} -o ${params.buildConfig} /p:PackageVersion=${packageVersion}"
   }

	stage('Publish & archive packages') {
		Utils.enableNugetCache(Utils.nugetDeployServerName())
		if (params.deployArtifacts) {
			Utils.deploy(NUGET_PRIVATE_URL, 'NugetPrivateApiKey')
      }
		archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true
	}

   stage('Archive XbimXplorer build') {
      archiveArtifacts artifacts: "XbimXplorer/${params.buildConfig}"
   }
}