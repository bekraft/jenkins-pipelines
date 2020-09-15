// BitubTRex Build

// Env:
// - NUGET_PRIVATE_URL .. private Nuget deployment server
// - NugetPrivateApiKey .. API key from credentials manager
// - PROTOBUF_SRC .. protobuf src directory (for additional proto includes)

// Parameters:
// - cleanWorkspace (boolean)
// - branch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildPreQualifier (string)
// - deployArtifacts (boolean)
// - runTests (boolean)

node {
	checkout scm
	def Utils = load "Utils.groovy"
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
		git branch: "${params.branch}", url: "https://github.com/bekraft/BitubTRex.git"
		
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
		Utils.initEnv()
		Utils.enableNugetCache(Utils.localNugetCacheName())
		Utils.nuget('sources list')

		// Cleaning nupkg builds
		powershell "dotnet clean BitubTRex.sln -c ${params.buildConfig}"
	}

	if (params.runTests) {
		stage('Test') {      
			powershell "dotnet test BitubTRex.sln -c ${params.buildConfig} -s BitubTRex.runsettings"
		}
	}

	def propsBuildVersion = Utils.buildVersionToDotNetProp(buildVersion)
	def buildPropsAdditional    
	if ('Debug' == params.buildConfig)
		buildPropsAdditional = "-p:IncludeSymbols=true -p:SymbolPackageFormat=snupkg"
	else
		buildPropsAdditional = ""

	stage('Build') {       
		powershell "dotnet build BitubTRex.sln -c ${params.buildConfig} ${propsBuildVersion} ${buildPropsAdditional}"
	}

	stage('Publish & archive') {
		powershell "dotnet pack BitubTRex.sln -c ${params.buildConfig} ${propsBuildVersion} ${buildPropsAdditional}"
		archiveArtifacts artifacts: '**/*.nupkg, **/*.snupkg', onlyIfSuccessful: true

		if (params.deployArtifacts)
			Utils.deploy(NUGET_PRIVATE_URL, 'NugetPrivateApiKey')
	}
}