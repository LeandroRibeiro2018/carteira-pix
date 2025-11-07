# Deploy Helper - Carteira Pix (PowerShell)
# Uso: .\deploy-helper.ps1 [comando]

param(
    [Parameter(Position=0)]
    [string]$Command = "help"
)

# Funções auxiliares
function Print-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Print-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Print-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Print-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Cyan
}

# Verificar gh CLI
function Check-GhCli {
    if (!(Get-Command gh -ErrorAction SilentlyContinue)) {
        Print-Error "GitHub CLI (gh) não está instalado"
        Print-Info "Instale em: https://cli.github.com/"
        exit 1
    }
}

# Criar feature
function Create-Feature {
    Print-Info "Criando nova branch de feature..."
    
    $featureName = Read-Host "Nome da feature"
    
    git checkout dev
    git pull origin dev
    git checkout -b "feature/$featureName"
    
    Print-Success "Feature branch criada: feature/$featureName"
    Print-Info "Agora você pode desenvolver sua feature"
}

# Promover para dev
function Promote-ToDev {
    Check-GhCli
    
    $currentBranch = git branch --show-current
    
    if ($currentBranch -notmatch "^feature/") {
        Print-Error "Você deve estar em uma branch feature/*"
        exit 1
    }
    
    Print-Info "Criando PR: $currentBranch → dev"
    
    $title = "feat: $($currentBranch -replace 'feature/','')"
    $body = "## Descrição`n`n[Descreva as mudanças]`n`n## Checklist`n`n- [ ] Testes adicionados`n- [ ] Documentação atualizada"
    
    gh pr create --base dev --head $currentBranch --title $title --body $body
    
    Print-Success "Pull Request criado!"
}

# Promover para homolog
function Promote-ToHomolog {
    Check-GhCli
    
    Print-Info "Promovendo dev → homolog"
    
    git checkout dev
    git pull origin dev
    
    $body = "## Mudanças`n`nPromovendo alterações testadas de dev para homolog"
    
    gh pr create --base homolog --head dev --title "chore: promover dev para homolog" --body $body
    
    Print-Success "Pull Request criado!"
    Print-Warning "Aguarde aprovação e merge automático"
}

# Promover para produção
function Promote-ToProd {
    Check-GhCli
    
    Print-Warning "⚠️  ATENÇÃO: Você está prestes a promover para PRODUÇÃO!"
    $confirm = Read-Host "Tem certeza? (sim/não)"
    
    if ($confirm -ne "sim") {
        Print-Info "Operação cancelada"
        exit 0
    }
    
    Print-Info "Promovendo homolog → main (produção)"
    
    git checkout homolog
    git pull origin homolog
    
    $version = Get-Date -Format "yyyy.MM.dd"
    $body = "## Release v$version`n`n### Mudanças`n`n[Liste as principais mudanças]`n`n### Checklist`n`n- [ ] Testes passando`n- [ ] Aprovado em homologação`n- [ ] Documentação atualizada`n- [ ] Changelog atualizado"
    
    gh pr create --base main --head homolog --title "Release v$version" --body $body
    
    Print-Success "Pull Request de release criado!"
    Print-Warning "Requer 2 aprovações antes do merge"
}

# Listar rollbacks
function List-Rollbacks {
    Print-Info "Branches de rollback disponíveis:"
    git branch -r | Select-String "rollback/prod" | ForEach-Object { $_ -replace "origin/","" } | Sort-Object -Descending
}

# Executar rollback
function Execute-Rollback {
    Check-GhCli
    
    Print-Warning "⚠️  ROLLBACK - Use apenas em caso de emergência!"
    
    List-Rollbacks
    
    Write-Host ""
    $rollbackBranch = Read-Host "Branch de rollback"
    $reason = Read-Host "Motivo do rollback"
    
    Print-Warning "Executando rollback via GitHub Actions..."
    
    gh workflow run rollback.yml -f rollback_branch=$rollbackBranch -f reason=$reason
    
    Print-Success "Workflow de rollback iniciado!"
    
    $repo = gh repo view --json nameWithOwner -q .nameWithOwner
    Print-Info "Acompanhe em: https://github.com/$repo/actions"
}

# Status do pipeline
function Get-Status {
    Check-GhCli
    
    Print-Info "Status dos últimos workflows:"
    gh run list --limit 5
}

# Verificar ambiente
function Check-Environment {
    $env = Read-Host "Ambiente (dev/homolog/prod)"
    
    $url = switch ($env) {
        "dev"     { "https://dev.pixwallet.example.com" }
        "homolog" { "https://homolog.pixwallet.example.com" }
        "prod"    { "https://pixwallet.example.com" }
        default   { 
            Print-Error "Ambiente inválido"
            exit 1
        }
    }
    
    Print-Info "Verificando $env em $url..."
    
    try {
        $response = Invoke-WebRequest -Uri "$url/actuator/health" -Method Get -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Print-Success "Ambiente $env está saudável (HTTP $($response.StatusCode))"
        }
    }
    catch {
        Print-Error "Ambiente $env com problemas: $_"
    }
}

# Ajuda
function Show-Help {
    Write-Host @"
🚀 Deploy Helper - Carteira Pix

Uso: .\deploy-helper.ps1 [comando]

Comandos disponíveis:

  create-feature      Criar nova branch de feature
  promote-dev         Promover feature para dev
  promote-homolog     Promover dev para homolog
  promote-prod        Promover homolog para produção
  list-rollbacks      Listar branches de rollback disponíveis
  rollback            Executar rollback em produção
  status              Ver status dos workflows
  check-env           Verificar saúde de um ambiente
  help                Mostrar esta ajuda

Exemplos:

  # Iniciar nova feature
  .\deploy-helper.ps1 create-feature

  # Promover para dev
  .\deploy-helper.ps1 promote-dev

  # Ver status
  .\deploy-helper.ps1 status

  # Rollback (emergência)
  .\deploy-helper.ps1 rollback

Para mais informações, consulte: CI-CD-GUIDE.md
"@
}

# Main
switch ($Command) {
    "create-feature"    { Create-Feature }
    "promote-dev"       { Promote-ToDev }
    "promote-homolog"   { Promote-ToHomolog }
    "promote-prod"      { Promote-ToProd }
    "list-rollbacks"    { List-Rollbacks }
    "rollback"          { Execute-Rollback }
    "status"            { Get-Status }
    "check-env"         { Check-Environment }
    default             { Show-Help }
}
