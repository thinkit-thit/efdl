@echo off
rem Genere le hash BCrypt du mot de passe maitre (admin.pass) pour eFDL.
rem Le mot de passe est saisi sans echo : il n'est ni affiche, ni passe en argument, ni ecrit sur disque.
rem (Fichier volontairement en ASCII : cmd relit le script apres chcp 65001.)
setlocal

rem --- JDK 21 : EFDL_JAVA_HOME, sinon %USERPROFILE%\.jdks\temurin-21 ---
if defined EFDL_JAVA_HOME (
  set "JAVA_HOME=%EFDL_JAVA_HOME%"
) else (
  set "JAVA_HOME=%USERPROFILE%\.jdks\temurin-21"
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 21 introuvable dans "%JAVA_HOME%". Definissez EFDL_JAVA_HOME.
  exit /b 1
)

set "BACKEND=%~dp0.."
pushd "%BACKEND%"

echo Compilation...
call mvn -q -B compile dependency:build-classpath -Dmdep.outputFile=target\classpath.txt -DincludeScope=runtime -DincludeArtifactIds=spring-security-crypto,commons-logging
if errorlevel 1 (
  popd
  exit /b 1
)
rem Classpath limite aux jars utiles a BCrypt ; for /f plutot que set /p (limite a 1023 caracteres).
for /f "usebackq delims=" %%i in ("target\classpath.txt") do set "CP=%%i"

rem Console en UTF-8 pour les messages accentues de l'outil Java.
chcp 65001 >nul
"%JAVA_HOME%\bin\java" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "target\classes;%CP%" com.ffs.efdl.tools.MasterPasswordHashTool "--write=%BACKEND%\src\main\resources\application-local.yml"
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
