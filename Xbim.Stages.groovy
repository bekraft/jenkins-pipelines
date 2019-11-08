// Xbim.Stages.groovy
import java.text.SimpleDateFormat
import hudson.plugins.git.GitTool

def generateSnapshotVersion(majorVersion, minorVersion, buildQualifier = null) {
    def shortversion = git('rev-parse --short HEAD')
    return generateBuildVersion(majorVersion, minorVersion, "${null!=buildQualifier? buildQualifier:''}${shortversion}")
}

def generateBuildVersion(majorVersion, minorVersion, buildQualifier = null) {   
   def buildDate = Calendar.getInstance()
   def releaseQualifier = ''
   if(buildQualifier in Date) {        
       buildDate.setTime(buildQualifier)
   } else {
       if(null!=buildQualifier && buildQualifier.trim()) {
            releaseQualifier = "-${buildQualifier}"
       }
   }

   def halfMinutePerDay = (int)((buildDate.get(Calendar.HOUR_OF_DAY)*60 + buildDate.get(Calendar.MINUTE))/2)
   
   return [
       major: "${majorVersion}",
       minor: "${minorVersion}",
       release: "${new SimpleDateFormat("yyD").format(buildDate.getTime())}",
       qualifier: releaseQualifier,
       build: "${buildDate.get(Calendar.DAY_OF_MONTH)}${halfMinutePerDay}"
   ]
}

def git(command) {
    def t = GitTool.getDefaultInstallation()
    if(null==t) {
        error 'Default Git installation missing!'
    }
    return powershell(returnStdout:true, script:"${t.getGitExe()} ${command}").trim()
}

def generaterPackageVersion(v) {
    return "${v.major}.${v.minor}.${v.release}${v.qualifier}.${v.build}"
}

def generaterAssemblyVersion(v) {
    return "${v.major}.${v.minor}.${v.release}.${v.build}"
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

def deployLocally(nugetCachePath) {
      echo "Deploying Nupkgs to ${params.localNugetStore} ..."
      
      findFiles(glob:'**/*.nupkg').each { f ->
         echo " Found pre-built package [${f}]"
         nuget("add ${WORKSPACE}/${f} -Source ${nugetCachePath} -Verbosity detailed")
      }
}

def removeLocalNugetCache(nugetCachePath) {
    nuget("sources remove -Name \"localCache\" -Source \"${nugetCachePath}\"")
}
    
def disableLocalNugetCache() {
    nuget("sources disable -Name \"localCache\"")
}
    
def enableLocalNugetCache() {
    nuget("sources enable -Name \"localCache\"")
}

def addLocalNugetCache(nugetCachePath) {
    if(0 != nuget("sources update -Name \"localCache\" -Source \"${nugetCachePath}\"")) {
        if(0 != nuget("sources add -Name \"localCache\" -Source \"${nugetCachePath}\"")) {
            error "Could not add ${nugetCachePath} to nuget repository configuration!"
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
