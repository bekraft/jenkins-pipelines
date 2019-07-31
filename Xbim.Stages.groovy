// Xbim.Stages.groovy
import java.text.SimpleDateFormat
import java.util.regex.Pattern

def generateBuildVersion(majorVersion, minorVersion, buildTime = null) {   
   def buildClassifier
   def buildDate = Calendar.getInstance()
   if(buildTime in Date) {        
        buildDate.setTime(buildTime)
        def buildNo = (int)((buildDate.get(Calendar.HOUR_OF_DAY)*60 + buildDate.get(Calendar.MINUTE))/2)
        buildClassifier = "${buildDate.get(Calendar.DAY_OF_MONTH)}${buildNo}"
   } else {
       buildClassifier = "${buildTime}"
   }

   return [
       major: "${majorVersion}",
       minor: "${minorVersion}",
       release: new SimpleDateFormat("yyMM").format(buildDate.getTime()),
       build: buildClassifier
   ]
}

def generaterPackageVersion(buildVersion) {
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

def cleanUpNupkgs(rootPath = '.') {
      findFiles(glob:"${rootPath}/**/*.nupkg").each { f ->
         if(0 != powershell(returnStatus: true, script: "rm ${WORKSPACE}/${f}")) {
             echo "Could not remove ${f} from workspace"
         }
      }
}

def deployLocally(nugetCachePath, rootPath = '.') {
      echo "Deploying Nupkgs to ${params.localNugetStore}"
      
      findFiles(glob:"${rootPath}/**/*.nupkg").each { f ->
         echo "Found ${f}"
         XbimStages.nuget("add ${WORKSPACE}/${f} -Source ${nugetCachePath} -Verbosity detailed")
      }
}

def addLocalNugetCache(nugetCachePath) {
    if(0 != nuget("sources update -Name localCache -Source \"${nugetCachePath}\"")) {
        if(0 != nuget("sources add -Name localCache -Source \"${nugetCachePath}\"")) {
            error "Could not add ${nugetCachePath} to nuget repository configuration!"
        }
    }
}

// See https://gist.github.com/JonCanning/a083e80c53eb68fac32fe1bfe8e63c48
def updatePackages(packageIdentifiers, regexPackageId = '.*') {
    echo "Start package updates using ${packageIdentifiers} and matching expression [${regexPackageId}]"
    def idset = packageIdentifiers.toSet()

    // Visit all project files
    findFiles(glob:'**/*.*proj').each { f ->
        def packages = readFile("${f}") =~ 'PackageReference Include="([^"]*)" Version="([^"]*)"'        
        echo "Found project [${f}] with ${packages.count == 0? 'no': packages.count} matches."
        packages.each { pkg ->
            def idmatches = (pkg[1] =~ regexPackageId)
            if(idset.contains(pkg[1]) || idmatches.count > 0) {
                echo "- [${f}] with package ${pkg[1]} (${pkg[2].empty ? 'latest' : pkg[2]})"
                if(!pkg[2].empty) {
                    // Only if a given version exists
                    powershell "dotnet add ${f} package ${pkg[1]}"
                }
            }            
        }
    }
    echo 'Finalized package updates.'
}

return this
