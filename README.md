# jenkins-pipelines

Jenkins Pipelines

## Xbim Pipes

Prerequisites of Jenkins
- Pipeline (Groovy)
- Pipeline Utility Steps
- MSBuild Plugin 
- PowerShell Plugin

Environment variables:
- LOCAL_NUGET_CACHE (Local nuget cache)

### BuildXbimEssentials 

Packs and locally publishes modular essential packages

Parameters:
- doCleanUpWs (boolean)
- xbimRepository (URL)
- xbimBranch (name)
- buildConfig (Release, Debug)
- buildMajor (int)
- buildMinor (int)
- buildIdentifier (string or empty)
- useLocalArtifacts (boolean flag)

### BuildXbimGeometry

Packs and locally publishes modular essential packages

Parameters:
- doCleanUpWs (boolean)
- doUpdatePackages (boolean)
- xbimRepository (URL)
- xbimBranch (name)
- buildConfig (Release, Debug)
- buildMajor (int)
- buildMinor (int)
- buildIdentifier (string or empty)
- useLocalArtifacts (boolean flag)

### BuildXbimWindowsUI

Not tested yet.
