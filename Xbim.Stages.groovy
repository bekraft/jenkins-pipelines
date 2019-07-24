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

return this
