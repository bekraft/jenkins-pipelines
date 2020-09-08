// Utils.groovy
import java.text.SimpleDateFormat
import hudson.plugins.git.GitTool

def localNugetCacheName() { 
    return "jenkinsCache"
}

def initEnv() {
    addNugetCache(localNugetCacheName(), "${LOCAL_NUGET_CACHE}")
    nuget("config -set repositoryPath=${LOCAL_NUGET_CACHE}")
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
      findFiles(glob:'**/*.nupkg').each { f ->
         if(0 != powershell(returnStatus: true, script: "rm ${WORKSPACE}/${f}")) {
             echo "! Could not remove [${f}] from workspace !"
         }
      }
}

def deployToNugetLocalCache(nugetCachePath) {
      echo "Deploying Nupkgs to ${nugetCachePath} ..."
      
      findFiles(glob:'**/*.?nupkg').each { f ->
         echo " Found pre-built package [${f}]"
         nuget("add ${WORKSPACE}/${f} -Source ${nugetCachePath} -Verbosity detailed")
      }
}

def removeNugetCache(cacheName, nugetCacheUri) {
    nuget("sources remove -Name \"${cacheName}\" -Source \"${nugetCacheUri}\"")
}
    
def disableNugetCache(cacheName) {
    nuget("sources disable -Name \"${cacheName}\"")
}
    
def enableNugetCache(cacheName) {
    nuget("sources enable -Name \"${cacheName}\"")
}

def addNugetCache(cacheName, nugetCacheUri) {
    if(0 != nuget("sources update -Name \"${cacheName}\" -Source \"${nugetCacheUri}\"")) {
        if(0 != nuget("sources add -Name \"${cacheName}\" -Source \"${nugetCacheUri}\"")) {
            error "Could not add ${nugetCacheUri} to nuget repository configuration!"
        }
    }
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

// See https://gist.github.com/JonCanning/a083e80c53eb68fac32fe1bfe8e63c48
def updatePackages(packageIdentifiers, regexPackageId = '.*') {
    echo "Start package updates using ${packageIdentifiers} and matching expression [${regexPackageId}]"
    def idset = packageIdentifiers.toSet()

    // Visit all project files
    findFiles(glob:'**/*.*proj').each { f ->
        def packages = readPackageVersion(f)
        echo "Found project [${f}] having ${packages.size()} reference(s) in total."
        packages.each { pkg ->
            // Test ID match
            def m = (pkg.id =~ regexPackageId)
            def isIdMatch = m.count > 0
            // Avoid CPS
            m = null
            if(idset.contains(pkg.id) || isIdMatch) {
                echo "- Found match for package  ${pkg.id} (${pkg.version.empty ? 'latest' : pkg.version})"
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
