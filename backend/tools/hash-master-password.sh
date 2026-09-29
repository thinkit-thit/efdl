#!/usr/bin/env bash
# Génère le hash BCrypt du mot de passe maître (admin.pass) pour eFDL.
# Le mot de passe est saisi sans écho ; il n'est ni affiché, ni passé en argument, ni écrit sur disque.
set -euo pipefail

JAVA_HOME="${EFDL_JAVA_HOME:-$HOME/.jdks/temurin-21}"
export JAVA_HOME
if [ ! -x "$JAVA_HOME/bin/java" ] && [ ! -x "$JAVA_HOME/bin/java.exe" ]; then
  echo "JDK 21 introuvable dans $JAVA_HOME. Définissez EFDL_JAVA_HOME." >&2
  exit 1
fi

BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
cd "$BACKEND"

echo "Compilation..."
mvn -q -B compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -DincludeScope=runtime -DincludeArtifactIds=spring-security-crypto,commons-logging

SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
CP="target/classes${SEP}$(cat target/classpath.txt)"

RUN=("$JAVA_HOME/bin/java" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$CP" com.ffs.efdl.tools.MasterPasswordHashTool "--write=$BACKEND/src/main/resources/application-local.yml")
# Git Bash (mintty) n'offre pas de console Java sans winpty : on l'utilise s'il est disponible
# et si l'entrée est un terminal (sinon l'outil affiche lui-même qu'un terminal interactif est requis).
if [ -n "${MSYSTEM:-}" ] && [ -t 0 ] && command -v winpty >/dev/null 2>&1; then
  winpty "${RUN[@]}"
else
  "${RUN[@]}"
fi
