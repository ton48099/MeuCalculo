<#
  publicar.ps1 - publica uma nova versao do Radar de Ganhos

  O que ele faz, em ordem:
    1. soma 1 no versionCode e atualiza o versionName (app/build.gradle.kts)
    2. compila o APK assinado (gradlew assembleRelease)
    3. copia o APK para dist/RadarDeGanhos-<versao>.apk
    4. commita tudo e faz push na main
    5. cria e envia a tag v<versao>
    6. cria a release no GitHub e anexa o APK

  Exemplos:
    .\publicar.ps1 -Resumo "Corrige o X do card" -Notas "Notas da versao aqui"
    .\publicar.ps1 -Versao 1.10 -Resumo "Ajusta minimos" -NotasArquivo notas.md
    .\publicar.ps1 -Resumo "Teste" -Simulacao      (nao altera nada, so mostra o plano)

  Sem -Versao, ele sobe o ultimo numero: 1.9 -> 1.10
  Sem -Sim, ele pede confirmacao antes de commitar.
#>
[CmdletBinding()]
param(
    [string]$Versao,
    [string]$Resumo = "",
    [string]$Notas,
    [string]$NotasArquivo,
    [switch]$Simulacao,
    [switch]$SemPush,
    [switch]$SemBump,
    [switch]$PularBuild
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
Set-Location $raiz

function Passo($texto) { Write-Host ""; Write-Host "== $texto" -ForegroundColor Cyan }
function Ok($texto)    { Write-Host "   ok: $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "   aviso: $texto" -ForegroundColor Yellow }

function AcharGit {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fixos = @(
        "$env:ProgramFiles\Git\cmd\git.exe",
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )
    foreach ($p in $fixos) { if (Test-Path $p) { return $p } }
    $achado = Get-ChildItem "$env:LOCALAPPDATA\Programs" -Filter git.exe -Recurse -Depth 6 -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*\cmd\git.exe" } | Select-Object -First 1
    if ($achado) { return $achado.FullName }
    throw "git nao encontrado nesta maquina."
}

function TokenGitHub {
    param([string]$Git)
    $cred = "protocol=https`nhost=github.com`n`n" | & $Git credential fill 2>$null
    $tok = ($cred | Where-Object { $_ -like "password=*" }) -replace "^password=", ""
    if (-not $tok) {
        throw "Nenhuma credencial do GitHub salva. Faca um push manual uma vez para o Windows guardar o login."
    }
    return $tok
}

function ChamarApi {
    param($Metodo, $Url, $Token, $Corpo, $TipoConteudo = "application/json; charset=utf-8", $TempoLimite = 120)
    $h = @{
        Authorization          = "Bearer $Token"
        "User-Agent"           = "publicar.ps1"
        Accept                 = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }
    $params = @{ Uri = $Url; Method = $Metodo; Headers = $h; TimeoutSec = $TempoLimite }
    if ($null -ne $Corpo) {
        $params.Body = if ($Corpo -is [byte[]]) { $Corpo } else { [System.Text.Encoding]::UTF8.GetBytes($Corpo) }
        $params.ContentType = $TipoConteudo
    }
    return Invoke-RestMethod @params
}

$git = AcharGit

# ---------- 1. versao ----------
Passo "Lendo a versao atual"
$arquivoGradle = Join-Path $raiz "app\build.gradle.kts"
$texto = Get-Content $arquivoGradle -Raw
if ($texto -notmatch 'versionCode\s*=\s*(\d+)')            { throw "versionCode nao encontrado em app/build.gradle.kts" }
$codeAtual = [int]$Matches[1]
if ($texto -notmatch 'versionName\s*=\s*"([^"]+)"')        { throw "versionName nao encontrado em app/build.gradle.kts" }
$versaoAtual = $Matches[1]

if (-not $Versao) {
    $partes = $versaoAtual.Split(".")
    $partes[$partes.Length - 1] = [string]([int]$partes[$partes.Length - 1] + 1)
    $Versao = $partes -join "."
}
if ($Versao -notmatch '^\d+(\.\d+)*$') { throw "Versao invalida: '$Versao'. Use algo como 1.10" }

$codeNovo = $codeAtual + 1
if ($SemBump) {
    $Versao = $versaoAtual
    $codeNovo = $codeAtual
}
$tag = "v$Versao"
Ok "versao atual $versaoAtual (versionCode $codeAtual)"
if ($SemBump) {
    Ok "sem bump: publica a versao que ja esta no build.gradle.kts"
} else {
    Ok "nova versao   $Versao (versionCode $codeNovo), tag $tag"
}

$tagExiste = (& $git tag -l $tag)
$tagRemota = (& $git ls-remote --tags origin "refs/tags/$tag" 2>$null)
if ($tagExiste -or $tagRemota) { throw "A tag $tag ja existe. Escolha outra versao com -Versao." }

$remoto = (& $git remote get-url origin)
$repositorio = ($remoto -replace '\.git$', '') -replace '^.*github\.com[:/]', ''
if ($repositorio -notmatch '^[\w.-]+/[\w.-]+$') { throw "Nao consegui identificar o repositorio a partir de '$remoto'." }
$branch = (& $git rev-parse --abbrev-ref HEAD)
Ok "repositorio $repositorio (branch $branch)"

# ---------- 2. notas ----------
if (-not $Notas -and $NotasArquivo) {
    if (-not (Test-Path $NotasArquivo)) { throw "Arquivo de notas nao encontrado: $NotasArquivo" }
    # ReadAllText e nao Get-Content -Raw: o Get-Content devolve a string com as
    # propriedades do provedor (PSPath, ReadCount...) e o ConvertTo-Json serializava
    # isso como objeto -- era o 422 da release ("body ... is not a string").
    $Notas = [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $NotasArquivo).ProviderPath,
        [System.Text.Encoding]::UTF8
    ).Trim()
}
if (-not $Notas) {
    $Notas = "**APK $Versao (versionCode $codeNovo)** - assinado com o mesmo keystore. Baixe o arquivo abaixo."
}

$mensagemCommit = if ($Resumo) { "Versao $Versao`: $Resumo" } else { "Versao $Versao" }

# ---------- plano ----------
Passo "Plano"
Write-Host "   versionCode : $codeAtual -> $codeNovo"
Write-Host "   versionName : $versaoAtual -> $Versao"
Write-Host "   commit      : $mensagemCommit"
Write-Host "   tag         : $tag"
Write-Host "   apk         : dist\RadarDeGanhos-$Versao.apk"
Write-Host "   release     : https://github.com/$repositorio/releases/tag/$tag"

$sujo = (& $git status --porcelain)
if ($sujo) {
    Aviso "ha alteracoes nao commitadas; elas entram no mesmo commit:"
    ($sujo | Select-Object -First 15) | ForEach-Object { Write-Host "      $_" }
    if (($sujo | Measure-Object).Count -gt 15) { Write-Host "      ..." }
}

if ($Simulacao) {
    Passo "Simulacao: nada foi alterado"
    return
}

$resp = Read-Host "Continuar? (s/N)"
if ($resp -notmatch '^[sSyY]$') { Passo "Cancelado"; return }

# ---------- 3. bump da versao ----------
if ($SemBump) {
    Passo "Mantendo a versao do build.gradle.kts (-SemBump)"
} else {
    Passo "Atualizando versionCode e versionName"
    $novoTexto = $texto -replace '(versionCode\s*=\s*)\d+', "`${1}$codeNovo"
    $novoTexto = $novoTexto -replace '(versionName\s*=\s*")[^"]+(")', "`${1}$Versao`${2}"
    [System.IO.File]::WriteAllText($arquivoGradle, $novoTexto, (New-Object System.Text.UTF8Encoding($false)))
    Ok "app/build.gradle.kts atualizado"
}

# ---------- 4. build ----------
$apkBuild = Join-Path $raiz "app\build\outputs\apk\release\app-release.apk"
if ($PularBuild) {
    Aviso "build pulado (-PularBuild); usando o APK existente"
    if (-not (Test-Path $apkBuild)) { throw "APK nao encontrado em $apkBuild" }
} else {
    Passo "Compilando o APK (isso demora alguns minutos)"
    & (Join-Path $raiz "gradlew.bat") assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "O build falhou. Nada foi commitado nem enviado." }
    Ok "build concluido"
}

$destinoApk = Join-Path $raiz "dist\RadarDeGanhos-$Versao.apk"
New-Item -ItemType Directory -Force -Path (Join-Path $raiz "dist") | Out-Null
Copy-Item $apkBuild $destinoApk -Force
Ok ("APK copiado para dist\RadarDeGanhos-{0}.apk ({1:N1} MB)" -f $Versao, ((Get-Item $destinoApk).Length / 1MB))

# ---------- 5. commit e push ----------
Passo "Commitando"
& $git add -A
# Mensagem por arquivo: com -m, o PowerShell quebra as notas de várias linhas em
# argumentos soltos e o commit falha (foi o que aconteceu na 1.11).
$arquivoMsg = Join-Path ([System.IO.Path]::GetTempPath()) "publicar-commit-msg.txt"
[System.IO.File]::WriteAllText(
    $arquivoMsg,
    "$mensagemCommit`n`n$Notas`n",
    (New-Object System.Text.UTF8Encoding($false))
)
& $git commit -F $arquivoMsg
$commitOk = $LASTEXITCODE -eq 0
Remove-Item $arquivoMsg -Force -ErrorAction SilentlyContinue
if (-not $commitOk) { Aviso "nada para commitar" } else { Ok "commit criado" }

if ($SemPush) {
    Aviso "push desativado (-SemPush); tag e release nao foram criados"
    return
}

Passo "Enviando para o GitHub"
& $git push origin $branch
if ($LASTEXITCODE -ne 0) { throw "O push falhou." }
Ok "push concluido"

# ---------- 6. tag ----------
Passo "Criando a tag $tag"
& $git tag -a $tag -m "Versao $Versao"
if ($LASTEXITCODE -ne 0) { throw "Falha ao criar a tag $tag" }
& $git push origin $tag
if ($LASTEXITCODE -ne 0) { throw "Falha ao enviar a tag $tag" }
Ok "tag enviada"

# ---------- 7. release ----------
Passo "Criando a release no GitHub"
$token = TokenGitHub -Git $git

$corpo = @{
    tag_name         = $tag
    # A branch, e nao o SHA: com SHA o GitHub devolve 422 quando a tag ja existe.
    target_commitish = $branch
    name             = if ($Resumo) { "Versao $Versao - $Resumo" } else { "Versao $Versao" }
    body             = [string]$Notas
    draft            = $false
    prerelease       = $false
} | ConvertTo-Json -Depth 3

try {
    $release = ChamarApi -Metodo Post -Url "https://api.github.com/repos/$repositorio/releases" -Token $token -Corpo $corpo
} catch {
    $detalhe = ""
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $detalhe = "`n" + $_.ErrorDetails.Message }
    throw "O commit e a tag foram enviados, mas a release falhou: $($_.Exception.Message)$detalhe"
}
Ok "release criada (id $($release.id))"

Passo "Anexando o APK"
$urlUpload = ($release.upload_url -replace '\{.*\}$', '') + "?name=RadarDeGanhos-$Versao.apk"
$bytes = [System.IO.File]::ReadAllBytes($destinoApk)
Write-Host ("   enviando {0:N1} MB..." -f ($bytes.Length / 1MB))
$asset = ChamarApi -Metodo Post -Url $urlUpload -Token $token -Corpo $bytes `
    -TipoConteudo "application/vnd.android.package-archive" -TempoLimite 900
Ok ("anexo enviado: {0} ({1:N1} MB, estado {2})" -f $asset.name, ($asset.size / 1MB), $asset.state)

Passo "Pronto"
Write-Host "   release : $($release.html_url)" -ForegroundColor Green
Write-Host "   apk     : $($asset.browser_download_url)" -ForegroundColor Green
Write-Host "   tag     : $tag" -ForegroundColor Green
