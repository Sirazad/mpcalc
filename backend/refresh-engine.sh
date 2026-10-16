#!/bin/sh
# Rebuild the engine and copy its jar (with its pom) into backend/lib-repo.
# Run this after every engine change, before building the backend.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
engine="$here/../engine"
(cd "$engine" && mvn -B -q package)
(cd "$engine" && mvn -B -q install:install-file \
  -Dfile=target/mpcalc-engine-0.1.0-SNAPSHOT.jar -DpomFile=pom.xml \
  -DlocalRepositoryPath="$here/lib-repo")
# The version stays 0.1.0-SNAPSHOT, so Maven would keep using the copy it cached in ~/.m2. Remove only our own artifact from
# that cache; the next build resolves the new jar from lib-repo.
(cd "$here" && mvn -B -q dependency:purge-local-repository -DmanualInclude=hu.mpcalc:mpcalc-engine -DreResolve=false)
echo "engine jar copied to $here/lib-repo (cached copy of hu.mpcalc:mpcalc-engine removed from ~/.m2)"
