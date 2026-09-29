# Livraison d'eFDL depuis le PC Windows vers le VPS.
#   .\deploy\pc\deploy.ps1 -Server 51.x.x.x                                    (mot de passe demandé à chaque connexion)
#   .\deploy\pc\deploy.ps1 -Server 51.x.x.x -Key C:\Users\moi\.ssh\efdl-vps     (clé SSH : conseillé)
#   .\deploy\pc\deploy.ps1 -Server 51.x.x.x -Port 22                           (autre port SSH que celui du VPS)
# Construit le backend (.jar) et le frontend (dist), les envoie, puis lance /opt/efdl-modern/bin/update.sh
# (qui garde la version précédente pour un retour arrière).
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$Key,
    [string]$User = "ubuntu",
    # Port SSH du VPS (2123 depuis le 28/09/2026).
    [int]$Port = 2123,
    [string]$JavaHome = "C:\Users\mamich\.jdks\temurin-21",
    [switch]$SkipTests
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$out = Join-Path $env:TEMP "efdl-release"
$target = "$User@$Server"
# Port SSH (ssh : -p, scp : -P) et clé SSH facultative (sinon ssh / scp demandent le mot de passe).
$sshArgs = @("-p", $Port)
$scpArgs = @("-P", $Port)
if ($Key) { $sshArgs += @("-i", $Key); $scpArgs += @("-i", $Key) }

Write-Host "== Construction du backend" -ForegroundColor Cyan
$env:JAVA_HOME = $JavaHome
Push-Location (Join-Path $root "backend")
try {
    if ($SkipTests) { mvn -q -B -DskipTests package } else { mvn -q -B package }
    if ($LASTEXITCODE -ne 0) { throw "Échec de la construction du backend" }
} finally { Pop-Location }
$jar = Get-ChildItem (Join-Path $root "backend\target") -Filter "efdl-backend-*.jar" |
    Where-Object { $_.Name -notlike "*.original" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "Fichier .jar introuvable" }

Write-Host "== Construction du frontend" -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try {
    # Dépendances : installées seulement si absentes. « npm ci » efface node_modules, ce qui échoue (EPERM) quand
    # le serveur de développement Vite tourne encore et garde des fichiers ouverts.
    if (-not (Test-Path "node_modules\.package-lock.json")) {
        npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm ci a échoué (arrêtez le serveur Vite « npm run dev » s'il tourne, puis relancez)" }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Échec de la construction du frontend" }
} finally { Pop-Location }

Write-Host "== Préparation de la livraison" -ForegroundColor Cyan
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory $out | Out-Null
Copy-Item $jar.FullName (Join-Path $out "efdl-backend.jar")
tar -czf (Join-Path $out "frontend.tgz") -C (Join-Path $root "frontend\dist") .
if ($LASTEXITCODE -ne 0) { throw "Archive du frontend impossible" }

Write-Host "== Envoi vers $Server (port SSH $Port)" -ForegroundColor Cyan
ssh @sshArgs $target "rm -rf /tmp/efdl-release && mkdir -p /tmp/efdl-release"
if ($LASTEXITCODE -ne 0) { throw "Connexion SSH impossible (port $Port). Test : Test-NetConnection $Server -Port $Port" }
scp @scpArgs (Join-Path $out "efdl-backend.jar") (Join-Path $out "frontend.tgz") "${target}:/tmp/efdl-release/"
if ($LASTEXITCODE -ne 0) { throw "Envoi impossible" }

Write-Host "== Mise en place sur le serveur" -ForegroundColor Cyan
ssh -t @sshArgs $target "sudo /opt/efdl-modern/bin/update.sh"
if ($LASTEXITCODE -ne 0) { throw "La mise en place a échoué (voir le message ci-dessus)" }
Write-Host "Livraison terminée." -ForegroundColor Green
