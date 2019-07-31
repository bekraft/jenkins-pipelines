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
    echo 'Start package updates...'
    packageIdentifiers.each { s -> 
        echo "- ${s}" 
    }
    echo " matching expression [${regexPackageId}]"

    def pckgPattern = Pattern.compile('PackageReference Include="([^"]*)" Version="([^"]*)"')
    def idPattern = Pattern.compile(regexPackageId, Pattern.DOTALL)
    def idSet = packageIdentifiers.toSet()

    // Visit all project files
    findFiles(glob:'**/*.*proj').each { f ->
        def contents = readFile "${f}"
        def pckgMatcher = pckgPattern.matcher(contents)
        echo " Found project file [${f}]:"
        while(pckgMatcher.find()) {
            def id = pckgMatcher.group(1) 
            def idMatcher = idPattern.matcher(id)
            if(idSet.contains(id) || idMatcher.matches()) {
                def version = pckgMatcher.group(2)
                echo " - matching package ${id} (${version.empty ? 'latest' : version})"
                if(!version.empty) {
                    // Only if a given version exists
                    powershell "dotnet add ${f} package ${id}"
                }
                idMatcher = null
            }            
        }
        pckgMatcher = null
    }
    echo 'Finalized package updates...'
}

return this
