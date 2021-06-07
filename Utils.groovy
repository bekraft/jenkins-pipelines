// Utils.groovy
import java.text.SimpleDateFormat
import hudson.plugins.git.GitTool
import java.nio.file.Paths

// Env
// - NUGET_PRIVATE_URL .. URL to private Nuget deploy server
// - NUGET_PUBLIC_URL .. URL to public Nuget deploy server
// Credentials & secrets
// - NugetPrivateApiKey .. API key to private Nuget deploy server
// - NugetPublicApiKey .. API key to public Nuget deploy server

def localNugetCacheName() { 
	return "jenkinsCache"
}

def nugetDeployServerName() {
	return "nugetDeployServer"
}

def initEnv(configFile = null) {
	def cfOption = ""
	if (configFile)
		cfOption = " -ConfigFile \"${configFile}\""

	addNugetCache(nugetDeployServerName(), "${NUGET_PRIVATE_URL}", configFile)
	nuget("config -set repositoryPath=${LOCAL_NUGET_CACHE} ${cfOption}")
}

def generateSnapshotVersion(majorVersion, minorVersion, buildQualifier = null) {
	def shortversion = git('rev-parse --short HEAD')
	def qualifier
	if (buildQualifier?.trim())
		qualifier = "${buildQualifier}-"
	else
		qualifier = ""
	return generateBuildVersion(majorVersion, minorVersion, "${qualifier}${shortversion}")
}

def generateBuildVersion(majorVersion, minorVersion, buildQualifier = null) {   
   def buildDate = Calendar.getInstance()
   def releaseQualifier = ''
   if(buildQualifier in Date) {        
	   buildDate.setTime(buildQualifier)
   } else {
	   if(null!=buildQualifier && buildQualifier.trim()) {
			releaseQualifier = "${buildQualifier}"
	   }
   }

   def minuteOfDay = (buildDate.get(Calendar.DAY_OF_MONTH)*24*60 + buildDate.get(Calendar.HOUR_OF_DAY)*60 + buildDate.get(Calendar.MINUTE))
   def shortYear = buildDate.get(Calendar.YEAR) % 100
   def dayOfYear = String.format("%03d", buildDate.get(Calendar.DAY_OF_YEAR))
	
   return [
	   major: "${majorVersion}",
	   minor: "${minorVersion}",
	   release: "${shortYear}${dayOfYear}",
	   qualifier: releaseQualifier,
	   build: "${minuteOfDay}"
   ]
}

def runDotNet(command, config, buildVersion, additionalProps) {
	def buildProps = buildVersionToDotNetProp(buildVersion)
	powershell """dotnet ${command} -c ${config} ${buildProps} ${additionalProps}"""
}

def buildVersionToDotNetProp(buildVersion) {
	def qualifier 
	if (buildVersion.qualifier?.trim())
		qualifier = "-${buildVersion.qualifier}"
	else
		qualifier = ""

	return "/p:BuildMajor=${buildVersion.major} /p:BuildMinor=${buildVersion.minor} /p:BuildRelease=${buildVersion.release} /p:BuildQualifier=${qualifier} /p:Build=${buildVersion.build}"
}

def git(command) {
	def t = GitTool.getDefaultInstallation()
	if(null==t) {
		error 'Default Git installation missing!'
	}
	return powershell(returnStdout:true, script:"${t.getGitExe()} ${command}").trim()
}

def generatePackageVersion(buildVersion) {
	def q 
	if (null != buildVersion.qualifier && buildVersion.qualifier.trim())
		q = "-${buildVersion.qualifier}"
	else
		q = ""

	return "${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}${q}.${buildVersion.build}"
}

def generaterAssemblyVersion(buildVersion) {
	return "${buildVersion.major}.${buildVersion.minor}.${buildVersion.release}.${buildVersion.build}"
}

def nuget(command) {
	if(!fileExists("${WORKSPACE}/nuget.exe")) {
		dir('./.') {
			powershell 'Invoke-WebRequest https://dist.nuget.org/win-x86-commandline/latest/nuget.exe -OutFile nuget.exe -Verbose'
		}
	}
	return powershell(returnStatus: true, script: "${WORKSPACE}/nuget.exe ${command}")    
}

def msbuild(command) {
	return bat(returnStatus: true, script: "\"${tool 'MSBuild'}\" ${command}")
}

def cleanUpNupkgs() {
	  findFiles(glob:'**/*.*nupkg').each { f ->
		 if(0 != powershell(returnStatus: true, script: "rm ${WORKSPACE}/${f}")) {
			 echo "! Could not remove [${f}] from workspace !"
		 }
	  }
}

def deploy(nugetUrl, apiKeyIdentity) {
	withCredentials([string(credentialsId: apiKeyIdentity, variable: 'APIKEY')]) {
		findFiles(glob:'**/*.*nupkg').each { f ->
			echo "Deploying '${f}' to ${nugetUrl}"
			if (0 != nuget("push -Source ${nugetUrl} -ApiKey ${APIKEY} ${WORKSPACE}/${f}"))
				error "Failure while pushing '${f}' to '${nugetUrl}'."
		}
	}
}

def deployToNugetLocalCache(nugetCachePath) {
	findFiles(glob:'**/*.*nupkg').each { f ->
		echo "Deploying '${f}' to ${nugetCachePath}"
		if (0 != nuget("add ${WORKSPACE}/${f} -Source ${nugetCachePath} -Verbosity detailed"))
			error "Failure while adding '${f}' to '${nugetCachePath}'."
	}
}

def removeNugetCache(cacheName, nugetCacheUri, configFile = null) {
	def cfOption = ""
	if (configFile)
		cfOption = " -ConfigFile \"${configFile}\""

	nuget("sources remove -Name \"${cacheName}\" -Source \"${nugetCacheUri}\" ${cfOption}")
}
	
def disableNugetCache(cacheName, configFile = null) {
	def cfOption = ""
	if (configFile)
		cfOption = " -ConfigFile \"${configFile}\""

	nuget("sources disable -Name \"${cacheName}\" ${cfOption}")
	echo "Nuget source '${cacheName}' is disabled anyway."
}
	
def enableNugetCache(cacheName, configFile = null) {
	def cfOption = ""
	if (configFile)
		cfOption = " -ConfigFile \"${configFile}\""

	if (0 != nuget("sources enable -Name \"${cacheName}\" ${cfOption}")) {
		error "Nuget source '${cacheName}' is yet unknown! Try to add it manually!"
	} else {
		echo "Nuget source '${cacheName}' is enabled."
	}
}

def addNugetCache(cacheName, nugetCacheUri, configFile = null) {
	def cfOption = ""
	if (configFile)
		cfOption = "-ConfigFile \"${configFile}\""
	if (0 != nuget("sources update -Name \"${cacheName}\" -Source \"${nugetCacheUri}\" ${cfOption}")) {
		echo "Trying to add '${cacheName}' to nuget sources."
		if(0 != nuget("sources add -Name \"${cacheName}\" -Source \"${nugetCacheUri}\" ${cfOption}")) {
			error "Could not add ${nugetCacheUri} to nuget repository configuration!"
		} else {
			echo "Added successfully '${cacheName}' => '${nugetCacheUri}'."
		}
	}
}

def readSolutionProjects(f) {
	def references = readFile("${f}") =~ '(?!Project)\\("([^"]*)"\\)\\s*=\\s*"([^"]*)"\\s*,\\s*"([^"]*)"'
	def projects = references.collect {
		[
			id: it[1],
			name: it[2],
			folder: it[3]
		]
	}
	// Avoid CPS exception
	references = null
	return projects
}

def readPackageVersion(f) {
	def packages = readFile("${f}") =~ 'PackageReference Include="([^"]*)" Version="([^"]*)"'
	def packageVersion = packages.collect {
		[ 
			id: it[1], 
			version: it[2] 
		]
	}
	// Avoid CPS exception
	packages = null
	return packageVersion
}

// Starts searching for project files at the current working directory
def updatePackagesFromDirectory(packageIdentifiers, regexPackageId = '.*') {
	update(findFiles(glob:'**/*.*proj'), packageIdentifiers, regexPackageId)
}

// Updates all packages from reachable projects of solution
def updatePackagesFromSolution(slnFileName, packageIdentifiers, regexPackageId = '.*') {
	def slnDirectory = new File(slnFileName).getParentFile().getName()
	updatePackages(
		readSolutionProjects(slnFileName).collect { it.folder.trim() }.findAll { it.endsWith('proj') }.collect { Paths.get(slnDirectory, it) },
		packageIdentifiers,
		regexPackageId)
}

// See https://gist.github.com/JonCanning/a083e80c53eb68fac32fe1bfe8e63c48
def updatePackages(projectFiles, packageIdentifiers, regexPackageId = '.*') {
	echo "Start package updates using ${packageIdentifiers} and matching expression [${regexPackageId}]."
	def idset = packageIdentifiers.toSet()

	// Visit all project files
	projectFiles.each { f ->
		def packages = readPackageVersion(f)
		echo "Project \"${f}\": ${packages.size()} referenced package(s)."
		packages.each { pkg ->
			// Test ID match
			def m = (pkg.id =~ regexPackageId)
			def isIdMatch = m.count > 0
			// Avoid CPS
			m = null
			if(idset.contains(pkg.id) || isIdMatch) {
				echo "Updating reference \"${pkg.id}\" [${pkg.version.empty ? 'latest' : pkg.version}]."
				if(!pkg.version.empty) {
					// Only if a given version exists
					powershell "dotnet add ${f} package ${pkg.id}"
				}
			}
		}
	}
	echo 'Finalized package updates.'
}

return this
