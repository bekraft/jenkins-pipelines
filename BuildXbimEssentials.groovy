import java.text.SimpleDateFormat

// Xbim.Essentials Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimEssentials/blob/master/azure-pipelines.yml)

// Env:
// - LOCAL_NUGET_CACHE

// Parameters:
// - doCleanUpWs (boolean)
// - xbimRepository (URL)
// - xbimBranch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildPreQualifier (string)
// - useLocalArtifacts (boolean)

node {
	checkout scm
	def Utils = load "Utils.groovy"
	def localNugetSource = Utils.nugetDeployServerName()
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
		Utils.cleanUpNupkgs()
		
		// Restore & update via nuget
		Utils.initEnv()
		if(params.useLocalArtifacts)
			Utils.enableNugetCache(localNugetSource)
		else
			Utils.disableNugetCache(localNugetSource)
		
		Utils.nuget('sources list')

		// Cleaning nupkg builds
		powershell "dotnet clean Xbim.Common/Xbim.Common.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.Ifc4/Xbim.Ifc4.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.Ifc2x3/Xbim.Ifc2x3.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.IO.Esent/Xbim.IO.Esent.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.Ifc/Xbim.Ifc.csproj -c ${params.buildConfig}"
		powershell "dotnet clean Xbim.Tessellator/Xbim.Tessellator.csproj -c ${params.buildConfig}"
	}
   
	stage('Build') {      
		powershell "dotnet pack Xbim.Common/Xbim.Common.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// IFC4
		powershell "dotnet remove Xbim.Ifc4/Xbim.Ifc4.csproj reference ../Xbim.Common/Xbim.Common.csproj"
		powershell "dotnet add Xbim.Ifc4/Xbim.Ifc4.csproj package Xbim.Common -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.Ifc4/Xbim.Ifc4.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// IFC2x3
		powershell "dotnet remove Xbim.Ifc2x3/Xbim.Ifc2x3.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
		powershell "dotnet add Xbim.Ifc2x3/Xbim.Ifc2x3.csproj package Xbim.Ifc4 -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.Ifc2x3/Xbim.Ifc2x3.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// MemoryModel
		powershell "dotnet remove Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
		powershell "dotnet add Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj package Xbim.Ifc2x3 -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// Esent
		powershell "dotnet remove Xbim.IO.Esent/Xbim.IO.Esent.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
		powershell "dotnet add Xbim.IO.Esent/Xbim.IO.Esent.csproj package Xbim.IO.MemoryModel -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.IO.Esent/Xbim.IO.Esent.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// Ifc
		powershell "dotnet remove Xbim.Ifc/Xbim.Ifc.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
		powershell "dotnet add Xbim.Ifc/Xbim.Ifc.csproj package Xbim.IO.MemoryModel -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.Ifc/Xbim.Ifc.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
		// Tesselator
		powershell "dotnet remove Xbim.Tessellator/Xbim.Tessellator.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
		powershell "dotnet add Xbim.Tessellator/Xbim.Tessellator.csproj package Xbim.Ifc2x3 -s ${localNugetSource} -v ${packageVersion}"
		powershell "dotnet pack Xbim.Tessellator/Xbim.Tessellator.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} ${buildProps}"
	}

	stage('Publish & archive') {
		if (params.deployArtifacts)
			Utils.deploy(NUGET_PRIVATE_URL, 'NugetPrivateApiKey')

		archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true
	}
}
