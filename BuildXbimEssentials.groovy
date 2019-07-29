import java.text.SimpleDateFormat

// Xbim.Essentials Build
// based on azure-pipelines.yml configuration (https://github.com/xBimTeam/XbimEssentials/blob/master/azure-pipelines.yml)

// Parameters:
// - doCleanUpWs (boolean)
// - localNugetStore (local path)
// - xbimRepository (URL)
// - xbimBranch (name)
// - buildConfig (Release, Debug)
// - buildMajor (int)
// - buildMinor (int)
// - buildIdentifier (string or empty)

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"   
   def buildTime = params.buildIdentifier.empty ? new Date() : params.buildIdentifier
   def buildVersion = XbimStages.generateBuildVersion(params.buildMajor, params.buildMinor, buildTime)
   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   echo "Building package version ${packageVersion}"
   
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
      XbimStages.cleanUpNupkgs()
   }
   
   stage('Build') {
      powershell "dotnet pack Xbim.Common/Xbim.Common.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // IFC4
      powershell "dotnet remove Xbim.Ifc4/Xbim.Ifc4.csproj reference ../Xbim.Common/Xbim.Common.csproj"
      powershell "dotnet add Xbim.Ifc4/Xbim.Ifc4.csproj package Xbim.Common -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc4/Xbim.Ifc4.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // IFC2x3
      powershell "dotnet remove Xbim.Ifc2x3/Xbim.Ifc2x3.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.Ifc2x3/Xbim.Ifc2x3.csproj package Xbim.Ifc4 -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc2x3/Xbim.Ifc2x3.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // MemoryModel
      powershell "dotnet remove Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj package Xbim.Ifc2x3 -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // Esent
      powershell "dotnet remove Xbim.IO.Esent/Xbim.IO.Esent.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
      powershell "dotnet add Xbim.IO.Esent/Xbim.IO.Esent.csproj package Xbim.IO.MemoryModel -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.IO.Esent/Xbim.IO.Esent.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // Ifc
      powershell "dotnet remove Xbim.Ifc/Xbim.Ifc.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
      powershell "dotnet add Xbim.Ifc/Xbim.Ifc.csproj package Xbim.IO.MemoryModel -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc/Xbim.Ifc.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
      // Tesselator
      powershell "dotnet remove Xbim.Tessellator/Xbim.Tessellator.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.Tessellator/Xbim.Tessellator.csproj package Xbim.Ifc2x3 -s ${params.buildConfig} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Tessellator/Xbim.Tessellator.csproj -c ${params.buildConfig} /p:PackageVersion=${packageVersion} -o ${params.buildConfig}"
   }
   
   stage('Locally publishing') {
      echo 'Congratulation! All binaries have been built!'
      XbimStages.deployLocally(params.localNugetStore)
   }
}