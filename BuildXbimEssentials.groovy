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

node {
   checkout scm
   def XbimStages = load "Xbim.Stages.groovy"
   def buildVersion = XbimStages.generateBuildVersion(params.buildMajor,params.buildMinor,new Date())
   def packageVersion = XbimStages.generaterPackageVersion(buildVersion)
   println("Building package version ${packageVersion}")
   
   def builtNugets = "${WORKSPACE}/nupkgs"
   
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
   }
   
   stage('Build') {
      powershell "dotnet pack Xbim.Common/Xbim.Common.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // IFC4
      powershell "dotnet remove Xbim.Ifc4/Xbim.Ifc4.csproj reference ../Xbim.Common/Xbim.Common.csproj"
      powershell "dotnet add Xbim.Ifc4/Xbim.Ifc4.csproj package Xbim.Common -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc4/Xbim.Ifc4.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // IFC2x3
      powershell "dotnet remove Xbim.Ifc2x3/Xbim.Ifc2x3.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.Ifc2x3/Xbim.Ifc2x3.csproj package Xbim.Ifc4 -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc2x3/Xbim.Ifc2x3.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // MemoryModel
      powershell "dotnet remove Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj package Xbim.Ifc2x3 -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // Esent
      powershell "dotnet remove Xbim.IO.Esent/Xbim.IO.Esent.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
      powershell "dotnet add Xbim.IO.Esent/Xbim.IO.Esent.csproj package Xbim.IO.MemoryModel -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.IO.Esent/Xbim.IO.Esent.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // Ifc
      powershell "dotnet remove Xbim.Ifc/Xbim.Ifc.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj ../Xbim.IO.MemoryModel/Xbim.IO.MemoryModel.csproj"
      powershell "dotnet add Xbim.Ifc/Xbim.Ifc.csproj package Xbim.IO.MemoryModel -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Ifc/Xbim.Ifc.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
      // Tesselator
      powershell "dotnet remove Xbim.Tessellator/Xbim.Tessellator.csproj reference ../Xbim.Common/Xbim.Common.csproj ../Xbim.Ifc2x3/Xbim.Ifc2x3.csproj ../Xbim.Ifc4/Xbim.Ifc4.csproj"
      powershell "dotnet add Xbim.Tessellator/Xbim.Tessellator.csproj package Xbim.Ifc2x3 -s ${builtNugets} -v ${packageVersion}"
      powershell "dotnet pack Xbim.Tessellator/Xbim.Tessellator.csproj -c ${buildConfig} /p:PackageVersion=${packageVersion} -o ${builtNugets}"
   }
   
   stage('Locally publishing') {
      println('Congratulation! All binaries have been built!')
      println("Publishing to ${params.localNugetStore}")
      
      findFiles(glob:"**/*.nupkg").each { f ->
         println("Adding ${f}")
         XbimStages.nuget("add ${WORKSPACE}/${f} -Source ${params.localNugetStore} -Verbosity detailed")
      }
   }
}