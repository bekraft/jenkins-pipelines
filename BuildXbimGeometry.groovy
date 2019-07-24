// Xbim.Geometry Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimGeometry/blob/master/azure-pipelines.yml)

// Parameters:
// - doCleanUpWs (boolean)
// - localNugetStore (local path)
// - xbimRepository (URL)
// - xbimBranch (name)

// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def package = XbimStages.generatePackageVersion(5,1,new Date())
   println("Building package version ${package.version}")
   
   def builtNugets = "${WORKSPACE}/nupkgs"
   def nugetBin = "${WORKSPACE}/nuget.exe"
   
   stage('Clean up') {
       if(params.doCleanUpWs) {
         cleanWs()
       }
   }
   
   stage('Git Checkout') { // for display purposes
      git branch: "${params.xbimBranch}", url: "${params.xbimRepository}" 
   }
   
   stage('Preparation') {
      powershell 'Invoke-WebRequest https://dist.nuget.org/win-x86-commandline/latest/nuget.exe -OutFile nuget.exe -Verbose'
      powershell "${nugetBin} sources list"
   }

   stage('Build') {
       
   }
}