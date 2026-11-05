# One-time setup of the live site on Azure (day 11). Run it in PowerShell from the repo root,
# ONE SECTION AT A TIME (copy-paste each block), and check each result before the next.
#
# What it creates, all in one new resource group (delete the group = delete everything):
#   - PostgreSQL Flexible Server, Burstable B1ms   (Azure for Students: 750 h/month free, 12 months)
#   - Container Registry, Standard                 (Azure for Students: free, 12 months)
#   - Container Apps environment + the app         (always-free monthly grant; scales to zero)
#     The environment comes out as an "express" one: no managed identity on the app, so the app
#     pulls its image with the registry's admin login (stored as a Container Apps secret).
#   - A managed identity that GitHub Actions logs in as (OIDC: no password stored in GitHub)
#
# Secrets: the database password is generated here and the Azure OpenAI key is read from .env.
# Both go straight into Container Apps secrets; neither is printed or written to a file.

# ---- 0. Settings: CHANGE the GitHub line ------------------------------------------------------
$GitHubRepo = "YOUR-GITHUB-USER/LabelLens"     # owner/name exactly as in the GitHub URL
$Branch     = "main"
$Location   = "spaincentral"                   # the region your student subscription allows
$Rg         = "labellens-app"
$Suffix     = Get-Random -Minimum 1000 -Maximum 9999   # registry and DB names must be globally unique
$Acr        = "labellensacr$Suffix"
$Pg         = "labellens-db-$Suffix"
$Env        = "labellens-env"
$App        = "labellens"
$Identity   = "labellens-github"

# ---- 1. Log in and prepare the CLI ---------------------------------------------------------------
az login
az account show --query "{subscription:name, id:id, tenant:tenantId}" -o table
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait
az provider register --namespace Microsoft.DBforPostgreSQL --wait
az provider register --namespace Microsoft.ContainerRegistry --wait
az group create --name $Rg --location $Location -o table

# ---- 2. Database (about 5-10 minutes) -------------------------------------------------------------
# 24 random letters and digits. Only used in this PowerShell session.
$DbPassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
az postgres flexible-server create `
  --resource-group $Rg --name $Pg --location $Location `
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 17 `
  --admin-user labellens --admin-password $DbPassword `
  --public-access 0.0.0.0 `
  --backup-retention 7 -o table
# High availability is off by default (a second server would double the cost).
# --public-access 0.0.0.0 = only Azure services can connect (not the whole internet).
az postgres flexible-server db create --resource-group $Rg --server-name $Pg --name labellens -o table

# ---- 3. Registry, and the first image (built with your local Docker, ~5 minutes) ----------------------
# Not "az acr build": student subscriptions don't allow ACR Tasks (TasksOperationsNotAllowed).
az acr create --resource-group $Rg --name $Acr --sku Standard --location $Location -o table
az acr login --name $Acr
$AcrServer = az acr show --name $Acr --query loginServer -o tsv
docker build -t "$AcrServer/labellens:first" .
docker push "$AcrServer/labellens:first"

# ---- 4. Container Apps environment and the app ------------------------------------------------------
az containerapp env create --resource-group $Rg --name $Env --location $Location -o table

# The Azure OpenAI values come from your .env (not printed).
$DotEnv = @{}
Get-Content .env | Where-Object { $_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$' } | ForEach-Object { $DotEnv[$Matches[1]] = $Matches[2].Trim('"') }
$DbHost    = az postgres flexible-server show --resource-group $Rg --name $Pg --query fullyQualifiedDomainName -o tsv

# The new environment is an "express" one, which can't pull images with a managed identity
# (ExpressEnvironmentFeatureNotSupported). It pulls with the registry's admin login instead,
# kept as a Container Apps secret like the others.
az acr update --name $Acr --admin-enabled true -o none
$AcrUser = az acr credential show --name $Acr --query username -o tsv
$AcrPass = az acr credential show --name $Acr --query "passwords[0].value" -o tsv

az containerapp create `
  --resource-group $Rg --name $App --environment $Env `
  --image "$AcrServer/labellens:first" `
  --registry-server $AcrServer --registry-username $AcrUser --registry-password $AcrPass `
  --ingress external --target-port 8080 `
  --max-replicas 1 `
  --cpu 0.5 --memory 1Gi `
  --secrets "db-password=$DbPassword" "openai-key=$($DotEnv['AZURE_OPENAI_API_KEY'])" `
  --env-vars `
    "DB_URL=jdbc:postgresql://${DbHost}:5432/labellens?sslmode=require" `
    "DB_USER=labellens" `
    "DB_PASSWORD=secretref:db-password" `
    "AZURE_OPENAI_ENDPOINT=$($DotEnv['AZURE_OPENAI_ENDPOINT'])" `
    "AZURE_OPENAI_API_KEY=secretref:openai-key" `
    "LABELLENS_READER_DEPLOYMENT=gpt-4.1-mini" `
    "LABELLENS_ALLERGEN_DEPLOYMENT=gpt-4.1" `
  -o table
# max-replicas 1: photos live in the container (decisions log, 2026-11-04), and it caps the cost.
# Scaling to zero is automatic in an express environment.

$Url = "https://" + (az containerapp show --resource-group $Rg --name $App --query properties.configuration.ingress.fqdn -o tsv)
"Live site: $Url   (first load after a pause takes ~30 s: the app starts from zero)"

# ---- 5. GitHub Actions login without a password (OIDC) ------------------------------------------------
az identity create --resource-group $Rg --name $Identity --location $Location -o table
# The subject must match EXACTLY what GitHub sends. This repo's tokens use the newer format with
# the owner's and the repo's numeric IDs: "repo:OWNER@OWNER_ID/REPO@REPO_ID:ref:refs/heads/main".
# Copy it from the "subject claim" line of a failed "Log in to Azure" step if it differs.
$Subject = "repo:kaikimax@96614722/LabelLens@1413715125:ref:refs/heads/$Branch"
az identity federated-credential create --resource-group $Rg --identity-name $Identity `
  --name github-main-ids --issuer "https://token.actions.githubusercontent.com" `
  --subject $Subject --audiences "api://AzureADTokenExchange" -o table
$PrincipalId = az identity show --resource-group $Rg --name $Identity --query principalId -o tsv
$RgId        = az group show --name $Rg --query id -o tsv
# Contributor on THIS resource group only: it can push images and update the app, nothing else.
az role assignment create --assignee-object-id $PrincipalId --assignee-principal-type ServicePrincipal `
  --role Contributor --scope $RgId -o table
$AcrId = az acr show --name $Acr --query id -o tsv
az role assignment create --assignee-object-id $PrincipalId --assignee-principal-type ServicePrincipal `
  --role AcrPush --scope $AcrId -o table

# ---- 6. Values to copy into GitHub: Settings > Secrets and variables > Actions > VARIABLES tab -------
# (IDs and names, not secrets: that's the point of OIDC.)
"AZURE_CLIENT_ID       = " + (az identity show --resource-group $Rg --name $Identity --query clientId -o tsv)
"AZURE_TENANT_ID       = " + (az account show --query tenantId -o tsv)
"AZURE_SUBSCRIPTION_ID = " + (az account show --query id -o tsv)
"AZURE_RESOURCE_GROUP  = $Rg"
"AZURE_ACR_NAME        = $Acr"
"AZURE_CONTAINER_APP   = $App"