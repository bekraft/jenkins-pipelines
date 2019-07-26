// Xbim.Stages.groovy
import java.text.SimpleDateFormat

def generateBuildVersion(majorVersion,minorVersion,buildTime = null) {
   def buildDate = Calendar.getInstance()
   if(buildTime) {
        buildDate.setTime(buildTime)
   }
   
   def buildNo = (int)((buildDate.get(Calendar.HOUR_OF_DAY)*60 + buildDate.get(Calendar.MINUTE))/2)

   return [
       major: "${majorVersion}",
       minor: "${minorVersion}",
       release: new SimpleDateFormat("yyMM").format(buildDate.getTime()),
       build: "${buildDate.get(Calendar.DAY_OF_MONTH)}${buildNo}"
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

return this
