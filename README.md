# jenkins-pipelines

Jenkins Pipelines

## Xbim Pipes

### BuildXbimEssentials 

Packs and locally publishes modular essential packages

Parameters:
- doCleanUpWs (boolean)
- localNugetStore (local path)
- xbimRepository (URL)
- xbimBranch (name)
- buildConfig (Release, Debug)
- buildMajor (int)
- buildMinor (int)
- buildIdentifier (string or empty)

### BuildXbimGeometry

Packs and locally publishes modular essential packages

Parameters:
- doCleanUpWs (boolean)
- doUpdatePackages (boolean)
- localNugetStore (local path)
- xbimRepository (URL)
- xbimBranch (name)
- buildConfig (Release, Debug)
- buildMajor (int)
- buildMinor (int)
- buildIdentifier (string or empty)