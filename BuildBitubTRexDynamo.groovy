// BitubTRexDynamo Build

// Env:

// Parameters:
// - cleanWorkspace (boolean)
// - branch (name)
// - buildConfig (Release, Beta)
// - buildMajor (int)
// - buildMinor (int)
// - buildPreQualifier (string)
// - runTests (boolean)

node {
	checkout scm
	def Utils = load "Utils.groovy"
	def deployLocalFolder = 'DeployPackages'
	def buildVersion 
	def packageVersion   
   
	stage('Clean up') {
		if(params.cleanWorkspace) {
			cleanWs()
		} else {
			Utils.git('reset --hard')
			Utils.git('clean -fd')
		}
	}
   
	stage('Git Checkout') { // for display purposes
		git branch: "${params.branch}", url: "https://github.com/bekraft/BitubTRexDynamo.git"
		
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
		Utils.initEnv()
		Utils.nuget('sources list')

		// Cleaning nupkg builds
		Utils.msbuild("./BitubTRexDynamo.sln /t:clean")
	}

	def propsBuildVersion = Utils.buildVersionToDotNetProp(buildVersion)

	def buildPropsAdditional    
	if ('Release' != params.buildConfig)
		buildPropsAdditional = "/p:IncludeSymbols=true /p:SymbolPackageFormat=snupkg"
	else
		buildPropsAdditional = ""

	stage('Build') {
		Utils.msbuild("./BitubTRexDynamo.sln /r /t:build /p:Configuration=${params.buildConfig} ${propsBuildVersion} ${buildPropsAdditional}")
   	}

	if (params.runTests) {
		stage('Test') {
			Utils.msbuild("./BitubTRexDynamo.sln /r /t:test /p:Configuration=${params.buildConfig} ${propsBuildVersion}")
		}
	}   	

	stage('Publish & archive') {
		powershell "dotnet pack TRexIfc/TRexIfc.csproj -c ${params.buildConfig} ${propsBuildVersion} ${buildPropsAdditional} /p:DeployPath=${deployLocalFolder}"
		zip zipFile: "TRexDynamo-${packageVersion}.zip", archive : true, dir: deployLocalFolder
		archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true
	}
}