# Installe l'IA sur la clé : regarde la puissance de l'ordinateur, puis
# télécharge le moteur (llama.cpp) et le modèle qui lui conviennent.
# Tout va sur la clé : rien n'est installé dans Windows.
# Se relance sans danger : ce qui est déjà téléchargé n'est pas refait.
#
# Normalement lancé par le bureau (bouton « Installer l'intelligence
# artificielle ») avec -FichierEtat : la progression est écrite dans ce fichier JSON
# et affichée dans la fenêtre. -Modele choisit le modèle (voir interface\modeles.json) ;
# sans -Modele, on prend celui qui convient à l'ordinateur. À la main : installer-ia.cmd.
param([string]$FichierEtat = '', [string]$Modele = '')

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
# Windows PowerShell 5.1 utilise encore de vieux protocoles par défaut :
# GitHub et Hugging Face exigent TLS 1.2.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$app = Split-Path -Parent $MyInvocation.MyCommand.Path
$racine = Split-Path -Parent $app
$moteurs = Join-Path $app 'moteur'
$modeles = Join-Path $app 'modele'
New-Item -ItemType Directory -Force $moteurs, $modeles | Out-Null

# Progression lue par le bureau : étape (1 à 4), message, fichier en cours et
# sa taille attendue (le bureau calcule le pourcentage à partir de la taille).
$script:infoMachine = ''
$script:etapeCourante = 1
# Version du moteur connue bonne (fichiers Windows présents), si GitHub ne répond pas bien.
$script:moteurDeSecours = 'b11926'
function Ecrire-Etat($statut, $etape, $message, $fichier = '', $taille = 0) {
    if ($etape -gt 0) { $script:etapeCourante = $etape }
    # En cas d'erreur, on garde l'étape où ça a bloqué (pour l'afficher).
    if ($statut -eq 'erreur') { $etape = $script:etapeCourante }
    if (-not $FichierEtat) { return }
    $o = [ordered]@{ etat = $statut; etape = $etape; message = $message; fichier = $fichier; taille = $taille; machine = $script:infoMachine }
    $json = $o | ConvertTo-Json -Compress
    try { [System.IO.File]::WriteAllText($FichierEtat, $json, (New-Object System.Text.UTF8Encoding($false))) } catch {}
}
function Titre($t) { Write-Host ""; Write-Host "== $t ==" -ForegroundColor Cyan }
function Info($t) { Write-Host "   $t" }

# Taille d'un fichier en ligne, pour afficher le pourcentage. Hugging Face
# renvoie vers un autre serveur : on lit « x-linked-size » ou la dernière
# « content-length » de toute la chaîne (curl.exe suit les redirections).
function Taille-Distante($url) {
    try {
        $entetes = & curl.exe -s -I -L --max-time 20 -A 'assistante' $url
        $lie = $entetes | Where-Object { $_ -match '^x-linked-size:\s*(\d+)' } | Select-Object -Last 1
        if ($lie -and $lie -match '(\d+)\s*$') { return [int64]$Matches[1] }
        $long = $entetes | Where-Object { $_ -match '^content-length:\s*(\d+)' } | Select-Object -Last 1
        if ($long -and $long -match '(\d+)\s*$') { return [int64]$Matches[1] }
    } catch {}
    return 0
}

function Telecharger($url, $dest, $etape, $quoi, [int64]$taille = 0) {
    if (Test-Path $dest) { Info "Déjà là : $(Split-Path -Leaf $dest)"; return }
    Info "Téléchargement de $(Split-Path -Leaf $dest)..."
    $tmp = "$dest.partiel"
    if ($taille -le 0) { $taille = Taille-Distante $url }
    Ecrire-Etat 'encours' $etape "Téléchargement $quoi…" $tmp $taille
    # curl.exe est fourni avec Windows 10 et 11 ; -C - reprend un téléchargement coupé.
    & curl.exe -L --fail --retry 5 --retry-delay 3 -C - -s -S -o $tmp $url
    if ($LASTEXITCODE -ne 0) { throw "Le téléchargement a échoué. Vérifie la connexion internet, puis réessaie : ce qui est déjà téléchargé sera gardé." }
    Move-Item -Force $tmp $dest
}

function Moteur($suffixe, $dossier, $etape) {
    # $suffixe : fin du nom du fichier, ex. « bin-win-cpu-x64.zip »
    $dest = Join-Path $moteurs $dossier
    if (Test-Path (Join-Path $dest 'llama-server.exe')) { Info "Moteur $dossier déjà installé."; return }
    # On ne se fie pas à « latest » : GitHub peut y mettre une version sans les
    # fichiers Windows (ex. v0.7.0). On prend la plus récente version publiée
    # qui contient vraiment le fichier voulu ; sinon, une version connue bonne.
    $url = $null; $taille = 0; $tag = ''
    try {
        $versions = Invoke-RestMethod 'https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=30' -Headers @{ 'User-Agent' = 'assistante' }
        foreach ($v in $versions) {
            if ($v.draft -or $v.prerelease) { continue }
            $a = $v.assets | Where-Object { $_.name -like "llama-*-$suffixe" } | Select-Object -First 1
            if ($a) { $url = $a.browser_download_url; $taille = [int64]$a.size; $tag = $v.tag_name; break }
        }
    } catch { Info "Liste des versions indisponible : $($_.Exception.Message)" }
    if (-not $url) {
        $tag = $script:moteurDeSecours
        $url = "https://github.com/ggml-org/llama.cpp/releases/download/$tag/llama-$tag-$suffixe"
        Info "Version de secours : $tag"
    }
    $zip = Join-Path $moteurs (Split-Path -Leaf $url)
    Telecharger $url $zip $etape 'du moteur' $taille
    Ecrire-Etat 'encours' $etape 'Décompression du moteur…'
    New-Item -ItemType Directory -Force $dest | Out-Null
    Expand-Archive -Force $zip $dest
    $exe = Get-ChildItem $dest -Recurse -Filter llama-server.exe | Select-Object -First 1
    if ($exe -and $exe.DirectoryName -ne $dest) { Get-ChildItem $exe.DirectoryName | Move-Item -Destination $dest -Force }
    Remove-Item $zip -Force
    Info "Moteur $dossier prêt (llama.cpp $tag)."
}

# Raccourci « Mon assistante » avec le logo, à la racine de la clé, et clé
# reconnaissable dans « Ce PC » (autorun.inf : icône + nom, sans rien lancer).
function Mettre-A-Jour-Raccourci($racine, $app) {
    try {
        $sh = New-Object -ComObject WScript.Shell
        $lnk = $sh.CreateShortcut((Join-Path $racine 'Mon assistante.lnk'))
        $lnk.TargetPath = Join-Path $racine '▶️ START.cmd'
        $lnk.WorkingDirectory = $racine
        $lnk.IconLocation = (Join-Path $app 'interface\icone.ico') + ',0'
        $lnk.WindowStyle = 7
        $lnk.Description = 'Ouvrir mon mini-ordinateur'
        $lnk.Save()
        $inf = Join-Path $racine 'autorun.inf'
        if (Test-Path $inf) { (Get-Item $inf -Force).Attributes = 'Hidden' }
    } catch { }
}

try {
    Write-Host "Installation de l'assistante sur la clé" -ForegroundColor Yellow
    Write-Host "Il faut internet, et ça peut prendre de 10 à 40 minutes."

    # -------------------------------------------------------- la machine
    Titre "1. J'examine l'ordinateur"
    Ecrire-Etat 'encours' 1 "J'examine l'ordinateur…"
    $ramGo = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
    $vramGo = 0
    $gpuNom = $null
    if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
        try {
            $l = (& nvidia-smi --query-gpu=name,memory.total --format=csv,noheader,nounits | Select-Object -First 1)
            $gpuNom, $mio = $l -split ',\s*'
            $vramGo = [math]::Round([double]$mio / 1024, 1)
        } catch {}
    }
    if (-not $gpuNom) {
        # AMD / autres : AdapterRAM est plafonné à 4 Go par Windows, on s'en contente.
        $carte = Get-CimInstance Win32_VideoController | Where-Object { $_.Name -match 'Radeon RX|Arc A|Arc B' } | Select-Object -First 1
        if ($carte) { $gpuNom = $carte.Name; $vramGo = [math]::Round($carte.AdapterRAM / 1GB, 1) }
    }
    $espaceGo = [math]::Round((Get-PSDrive -Name (Split-Path -Qualifier $app).TrimEnd(':')).Free / 1GB, 1)

    # Catalogue des modèles (le même que dans la fenêtre)
    $catalogue = (Get-Content (Join-Path $app 'interface\modeles.json') -Raw -Encoding UTF8 | ConvertFrom-Json).modeles
    if (-not $Modele) {
        # Sans choix : le plus gros modèle qui tient confortablement.
        $ok = $catalogue | Where-Object { $ramGo -ge $_.ram_confort -or ($ramGo -ge $_.ram_min -and $vramGo -ge $_.vram_conseillee -and $_.vram_conseillee -gt 0) }
        $Modele = if ($ok) { ($ok | Select-Object -Last 1).id } else { $catalogue[0].id }
    }
    $choisi = $catalogue | Where-Object { $_.id -eq $Modele } | Select-Object -First 1
    if (-not $choisi) { throw "Modèle inconnu : $Modele" }
    $taille = $choisi.famille
    $gpu = [bool]($gpuNom -and $vramGo -ge 4)

    $carteTxt = if ($gpuNom) { "$gpuNom ($vramGo Go)" } else { 'aucune carte graphique dédiée' }
    $script:infoMachine = "$ramGo Go de mémoire, $carteTxt — modèle : $($choisi.nom) ($taille)"
    Info "Mémoire vive : $ramGo Go"
    Info "Carte graphique : $carteTxt"
    Info "Espace libre sur la clé : $espaceGo Go"
    Write-Host "   -> Modèle choisi : $($choisi.nom) ($taille)" -ForegroundColor Green

    $dossierModele = Join-Path $modeles $choisi.id
    $dejaLa = Test-Path (Join-Path $dossierModele 'installe.ok')
    # Ce qui est déjà téléchargé (reprise après une coupure) ne compte plus.
    $dejaGo = 0
    if (Test-Path $dossierModele) {
        $dejaGo = ((Get-ChildItem $dossierModele -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum) / 1GB
    }
    $besoin = [math]::Ceiling([math]::Max(0, $choisi.taille_go - $dejaGo) + 0.6)
    if (-not $dejaLa -and $espaceGo -lt $besoin) { throw "Pas assez d'espace sur la clé : il faut environ $besoin Go libres (il en reste $espaceGo)." }

    # ---------------------------------------------------------- le moteur
    Titre "2. Le moteur IA"
    Moteur 'bin-win-cpu-x64.zip' 'cpu' 2                       # marche sur tous les PC
    if ($gpu) { Moteur 'bin-win-vulkan-x64.zip' 'gpu' 2 }      # plus rapide avec une carte graphique

    # ---------------------------------------------------------- le modèle
    Titre "3. Le modèle (le « cerveau »)"
    Ecrire-Etat 'encours' 3 'Je cherche le modèle…'
    New-Item -ItemType Directory -Force $dossierModele | Out-Null
    $depot = $choisi.depot
    # « blobs=true » : la liste des fichiers avec leur taille (pour le pourcentage).
    try { $infos = Invoke-RestMethod "https://huggingface.co/api/models/${depot}?blobs=true" -Headers @{ 'User-Agent' = 'assistante' } }
    catch { throw "Ce modèle n'est pas disponible en ce moment. Essaie un autre modèle, ou réessaie plus tard." }
    $fichiers = $infos.siblings.rfilename
    $principal = $fichiers | Where-Object { $_ -match 'Q4_K_M\.gguf$' -and $_ -notmatch 'mmproj' } | Select-Object -First 1
    $vision = ($fichiers | Where-Object { $_ -match 'mmproj.*Q8_0\.gguf$' } | Select-Object -First 1)
    if (-not $vision) { $vision = $fichiers | Where-Object { $_ -match 'mmproj.*\.gguf$' } | Select-Object -First 1 }
    if (-not $principal) { throw "Ce modèle n'a pas de version légère (Q4_K_M)." }
    $taillePrincipal = [int64](($infos.siblings | Where-Object { $_.rfilename -eq $principal } | Select-Object -First 1).size)
    if ($taillePrincipal -le 0) { $taillePrincipal = [int64]($choisi.taille_go * 0.9 * 1GB) }   # estimation si le site ne la donne pas
    Telecharger "https://huggingface.co/$depot/resolve/main/$principal" (Join-Path $dossierModele (Split-Path -Leaf $principal)) 3 'du cerveau de l''assistante' $taillePrincipal
    if ($vision) {
        $tailleVision = [int64](($infos.siblings | Where-Object { $_.rfilename -eq $vision } | Select-Object -First 1).size)
        Telecharger "https://huggingface.co/$depot/resolve/main/$vision" (Join-Path $dossierModele (Split-Path -Leaf $vision)) 4 'des yeux (pour lire les photos)' $tailleVision
    }
    # Tout est là : on le note. Sans ce fichier, le modèle n'est pas considéré
    # comme installé (un téléchargement coupé pourra être repris).
    Set-Content -Path (Join-Path $dossierModele 'installe.ok') -Value (Get-Date -Format s) -Encoding ASCII

    # Le modèle installé devient le modèle actif s'il n'y en avait pas encore.
    $choix = Join-Path $modeles 'choix.json'
    $actif = $null
    if (Test-Path $choix) { try { $actif = (Get-Content $choix -Raw -Encoding UTF8 | ConvertFrom-Json).actif } catch {} }
    if (-not $actif -or -not (Test-Path (Join-Path (Join-Path $modeles $actif) 'installe.ok'))) {
        [System.IO.File]::WriteAllText($choix, (@{ actif = $choisi.id } | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
    }

    # Clé bien rangée pour elle : on voit seulement « ▶️ START »,
    # « Mes documents » et le mode d'emploi. Le dossier technique est caché.
    New-Item -ItemType Directory -Force (Join-Path $racine 'Mes documents') | Out-Null
    try { (Get-Item $app -Force).Attributes = 'Directory, Hidden' } catch {}
    Mettre-A-Jour-Raccourci $racine $app

    Ecrire-Etat 'fini' 4 "C'est installé !"
    Titre "Terminé !"
    Write-Host "   L'assistante est prête." -ForegroundColor Green
    exit 0
} catch {
    Ecrire-Etat 'erreur' 0 ($_.Exception.Message)
    Write-Host ""
    Write-Host "Problème : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
