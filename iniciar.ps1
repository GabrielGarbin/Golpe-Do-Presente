# Inicia o sistema inteiro (API + front-end) e abre o navegador.
# Uso: dê dois cliques em iniciar.cmd (ou rode .\iniciar.cmd no terminal).
# Para parar: feche as duas janelas que abrirem (ou Ctrl+C dentro delas).

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $raiz 'backend'
$frontend = Join-Path $raiz 'frontend'
$urlApi = 'http://localhost:8081/saude'
$urlFront = 'http://localhost:5500'

function Escrever($texto, $cor = 'Gray', [switch]$NoNewline) { Write-Host $texto -ForegroundColor $cor -NoNewline:$NoNewline }

function PortaEmUso($porta) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $porta)
        $c.Close()
        return $true
    } catch { return $false }
}

function ComandoQueFunciona($nome, $argumento) {
    $cmd = Get-Command $nome -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    try {
        & $cmd.Source $argumento *> $null
        if ($LASTEXITCODE -eq 0) { return $cmd.Source }
    } catch { }
    return $null
}

Escrever ''
Escrever '  Golpe do Presente - iniciando API e front-end' 'Cyan'
Escrever ''

# 1) Configuração do banco (fica fora do Git)
$temConfig = (Test-Path (Join-Path $backend 'config\application.properties')) -or (Test-Path (Join-Path $backend '.env'))
if (-not $temConfig) {
    Escrever '  Falta a configuração do banco de dados.' 'Yellow'
    Escrever '  Crie backend\config\application.properties (veja o README, seção "Banco de dados").' 'Yellow'
    exit 1
}

# 2) Servidor de arquivos para o front: Python (py ou python) ou, se não houver, Node (npx serve)
$python = ComandoQueFunciona 'py' '--version'
if (-not $python) { $python = ComandoQueFunciona 'python' '--version' }
if ($python) {
    $comandoFront = "& '$python' -m http.server 5500"
} elseif (Get-Command npx -ErrorAction SilentlyContinue) {
    $comandoFront = 'npx --yes serve -l 5500 .'
} else {
    Escrever '  Não encontrei Python nem Node.js para servir o front-end. Instale um deles.' 'Yellow'
    exit 1
}

# 3) API (Spring Boot) em uma janela própria
if (PortaEmUso 8081) {
    Escrever '  API: já está rodando na porta 8081.' 'Green'
} else {
    Escrever '  API: abrindo em uma nova janela (a primeira vez demora mais: o Maven baixa as dependências)...'
    Start-Process powershell -WorkingDirectory $backend -ArgumentList @(
        '-NoExit', '-Command',
        "`$Host.UI.RawUI.WindowTitle = 'Golpe do Presente - API (8081)'; .\mvnw.cmd spring-boot:run")
}

# 4) Front-end em outra janela
if (PortaEmUso 5500) {
    Escrever '  Front-end: já está rodando na porta 5500.' 'Green'
} else {
    Escrever '  Front-end: abrindo em uma nova janela...'
    Start-Process powershell -WorkingDirectory $frontend -ArgumentList @(
        '-NoExit', '-Command',
        "`$Host.UI.RawUI.WindowTitle = 'Golpe do Presente - Front (5500)'; $comandoFront")
}

# 5) Espera a API responder antes de abrir o navegador
Escrever '  Aguardando a API ficar pronta' -NoNewline
$limite = (Get-Date).AddMinutes(4)
$pronta = $false
while ((Get-Date) -lt $limite) {
    try {
        Invoke-WebRequest -Uri $urlApi -UseBasicParsing -TimeoutSec 3 | Out-Null
        $pronta = $true
        break
    } catch {
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 3
    }
}
Escrever ''

if ($pronta) {
    Escrever "  Tudo pronto: $urlFront" 'Green'
    Start-Process $urlFront
} else {
    Escrever '  A API não respondeu em 4 minutos. Veja a mensagem de erro na janela "API (8081)".' 'Yellow'
    exit 1
}
