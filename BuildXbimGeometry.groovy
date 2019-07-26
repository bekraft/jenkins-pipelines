// Xbim.Geometry Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimGeometry/blob/master/azure-pipelines.yml)

// Parameters:
// - doCleanUpWs (boolean)
// - doUpdatePackages (boolean)
// - localNugetStore (local path)
// - xbimRepository (URL)
// - xbimBranch (name)

// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildVersion = XbimStages.generateBuildVersion(5,1,new Date())
   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   println("Building package version ${packageVersion}")
   
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
      XbimStages.nuget('sources list')
      XbimStages.nuget('restore Xbim.Geometry.Engine.sln')
      if(params.doUpdatePackages) {
          XbimStages.nuget('update')
      }
      // TODO Replace versions 
   }

   stage('Build') {
       // Build Engine
       XbimStages.msbuild('Xbim.Geometry.Engine.sln /t:build')
   }
}