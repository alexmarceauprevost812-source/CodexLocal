# Démarre le mini-ordinateur de la clé : une fenêtre Windows s'ouvre (Edge en
# mode application, sans barre d'adresse) et le moteur IA tourne caché.
# Quand on ferme la fenêtre, tout s'arrête tout seul.
#
# Première fois (IA pas encore sur la clé) : le bureau s'ouvre quand même,
# servi par un petit serveur local écrit ici, et propose « Installer
# l'intelligence artificielle ». La progression s'affiche dans la fenêtre :
# aucune fenêtre noire, aucun terminal.
#
# Confidentialité : tout écoute seulement 127.0.0.1 (cet ordinateur) ; rien
# n'est envoyé sur internet pendant qu'elle travaille.

$ErrorActionPreference = 'Stop'
$app = Split-Path -Parent $MyInvocation.MyCommand.Path
$racine = Split-Path -Parent $app
$modeles = Join-Path $app 'modele'
$interface = [System.IO.Path]::GetFullPath((Join-Path $app 'interface'))
$cpu = Join-Path $app 'moteur\cpu\llama-server.exe'
$gpuExe = Join-Path $app 'moteur\gpu\llama-server.exe'
$port = 8765
$url = "http://127.0.0.1:$port/"
# La fenêtre reçoit l'emplacement de la clé, pour le bouton « Ouvrir dans Word ».
# Un petit fichier caché « .cle-assistante » (un numéro unique) dans « Mes
# documents » permet à la fenêtre de vérifier qu'un dossier choisi est bien
# celui de CETTE clé avant d'ouvrir un fichier directement dans Word.
$docsCle = Join-Path $racine 'Mes documents'
New-Item -ItemType Directory -Force $docsCle | Out-Null
$marqueur = Join-Path $docsCle '.cle-assistante'
if (-not (Test-Path $marqueur)) {
    [System.IO.File]::WriteAllText($marqueur, [guid]::NewGuid().ToString('N'), (New-Object System.Text.UTF8Encoding($false)))
    try { (Get-Item $marqueur -Force).Attributes = 'Hidden' } catch {}
}
$idCle = ([System.IO.File]::ReadAllText($marqueur)).Trim()
$urlFenetre = "$url" + "?racine=" + [uri]::EscapeDataString($racine) + "&cle=" + $idCle
$journal = Join-Path $env:TEMP 'assistante-moteur.log'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

function Avertir($texte) { [System.Windows.Forms.MessageBox]::Show($texte, 'Mon assistante', 'OK', 'Warning') | Out-Null }
function Pret {
    try { return (Invoke-WebRequest "${url}health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { return $false }
}

function Trouver-Edge {
    $chemins = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe", "$env:LOCALAPPDATA\Microsoft\Edge\Application\msedge.exe")
    return $chemins | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}

# Tout ce que l'assistante crée va directement dans « Mes documents » sur la
# clé (et pas dans Téléchargements de l'ordi). La lettre de la clé change d'un
# PC à l'autre, donc on le règle à chaque démarrage.
function Regler-Telechargements($profil) {
    $docs = Join-Path $racine 'Mes documents'
    New-Item -ItemType Directory -Force $docs | Out-Null
    $defaut = Join-Path $profil 'Default'
    New-Item -ItemType Directory -Force $defaut | Out-Null
    $fichier = Join-Path $defaut 'Preferences'
    $prefs = $null
    if (Test-Path $fichier) { try { $prefs = Get-Content $fichier -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $prefs = $null } }
    if (-not $prefs) { $prefs = [pscustomobject]@{} }
    foreach ($cle in 'download', 'savefile') {
        if (-not $prefs.PSObject.Properties[$cle]) { $prefs | Add-Member -NotePropertyName $cle -NotePropertyValue ([pscustomobject]@{}) }
    }
    $prefs.download | Add-Member -Force -NotePropertyName default_directory -NotePropertyValue $docs
    $prefs.download | Add-Member -Force -NotePropertyName prompt_for_download -NotePropertyValue $false
    $prefs.download | Add-Member -Force -NotePropertyName directory_upgrade -NotePropertyValue $true
    $prefs.savefile | Add-Member -Force -NotePropertyName default_directory -NotePropertyValue $docs
    $json = $prefs | ConvertTo-Json -Depth 100 -Compress
    [System.IO.File]::WriteAllText($fichier, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function Ouvrir-Fenetre {
    $edge = Trouver-Edge
    if (-not $edge) { Start-Process $urlFenetre; return $null }
    # Profil séparé gardé sur la clé : ses conversations et réglages la suivent.
    $profil = Join-Path $app 'profil'
    try { Regler-Telechargements $profil } catch { }
    $arg = "--app=$urlFenetre --user-data-dir=`"$profil`" --no-first-run --no-default-browser-check --disable-sync --window-size=1280,860"
    return Start-Process $edge -ArgumentList $arg -PassThru
}

# La fenêtre de la clé est-elle encore ouverte ? (Edge garde parfois un
# processus d'arrière-plan : on regarde tous ceux qui utilisent le profil.)
function Fenetre-Ouverte {
    $p = Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like "*$app\profil*" }
    return [bool]$p
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

# Icône « Mon assistante » sur le bureau Windows de l'ordinateur (sur demande,
# bouton dans Réglages). La lettre de la clé change d'un PC à l'autre : si
# l'icône existe déjà, on la remet à jour à chaque démarrage.
$raccourciBureau = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Mon assistante.lnk'
function Raccourci-Bureau-Windows($creer) {
    if (-not $creer -and -not (Test-Path $raccourciBureau)) { return $false }
    try {
        $sh = New-Object -ComObject WScript.Shell
        $lnk = $sh.CreateShortcut($raccourciBureau)
        $lnk.TargetPath = Join-Path $racine '▶️ START.cmd'
        $lnk.WorkingDirectory = $racine
        $lnk.IconLocation = (Join-Path $app 'interface\icone.ico') + ',0'
        $lnk.WindowStyle = 7
        $lnk.Description = 'Ouvrir mon mini-ordinateur (la clé doit être branchée)'
        $lnk.Save()
        return $true
    } catch { return $false }
}

# Modèle actif : modele\choix.json -> { "actif": "qwen3-vl-4b" }, fichiers
# dans modele\<id>\. Retourne le dossier, ou $null si rien d'installé.
function Dossier-Actif {
    $choix = Join-Path $modeles 'choix.json'
    if (-not (Test-Path $choix)) { return $null }
    try { $id = (Get-Content $choix -Raw -Encoding UTF8 | ConvertFrom-Json).actif } catch { return $null }
    if (-not $id) { return $null }
    $d = Join-Path $modeles $id
    if (Test-Path (Join-Path $d 'installe.ok')) { return $d } else { return $null }
}
function IA-Installee { return [bool]((Dossier-Actif) -and (Test-Path $cpu)) }

# =====================================================================
#  Service local : lecture du PC, modèles IA, installation
# =====================================================================
# Première fois : il sert aussi le bureau sur le port 8765 (pas encore de
# moteur). Ensuite il écoute sur le port 8766, à côté du moteur.
$TYPES = @{ '.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript; charset=utf-8'; '.css' = 'text/css; charset=utf-8'
            '.svg' = 'image/svg+xml'; '.png' = 'image/png'; '.ico' = 'image/x-icon'; '.json' = 'application/json; charset=utf-8'; '.txt' = 'text/plain; charset=utf-8' }
$portService = 8766
$etatFichier = Join-Path $env:TEMP 'assistante-installation.json'
$script:installeur = $null
$script:modeInstallation = $false
$script:activerDemande = $null

# Ce que le « moteur de lecture du PC » trouve sur l'ordinateur branché.
function Lire-Machine {
    $m = [ordered]@{ ram_go = 0; vram_go = 0; gpu = $null; processeur = ''; coeurs = 0; espace_go = 0; systeme = '' }
    try { $m.ram_go = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1) } catch {}
    try { $p = Get-CimInstance Win32_Processor | Select-Object -First 1; $m.processeur = ($p.Name -replace '\s+', ' ').Trim(); $m.coeurs = (Get-CimInstance Win32_Processor | Measure-Object NumberOfCores -Sum).Sum } catch {}
    if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
        try {
            $l = (& nvidia-smi --query-gpu=name,memory.total --format=csv,noheader,nounits | Select-Object -First 1)
            $n, $mio = $l -split ',\s*'; $m.gpu = $n; $m.vram_go = [math]::Round([double]$mio / 1024, 1)
        } catch {}
    }
    if (-not $m.gpu) {
        try {
            $c = Get-CimInstance Win32_VideoController | Where-Object { $_.Name -match 'Radeon RX|Arc A|Arc B' } | Select-Object -First 1
            if ($c) { $m.gpu = $c.Name; $m.vram_go = [math]::Round($c.AdapterRAM / 1GB, 1) }
        } catch {}
    }
    try { $m.espace_go = [math]::Round((Get-PSDrive -Name (Split-Path -Qualifier $app).TrimEnd(':')).Free / 1GB, 1) } catch {}
    try { $m.systeme = (Get-CimInstance Win32_OperatingSystem).Caption } catch {}
    return $m
}
$script:machine = $null

function Liste-Modeles {
    $actif = $null
    try { $actif = (Get-Content (Join-Path $modeles 'choix.json') -Raw -Encoding UTF8 | ConvertFrom-Json).actif } catch {}
    $installes = @()
    $deja = @{}
    Get-ChildItem $modeles -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        # « installe.ok » : écrit seulement quand TOUS les fichiers du modèle sont arrivés.
        if (Test-Path (Join-Path $_.FullName 'installe.ok')) { $installes += $_.Name }
        else {
            # Installation coupée : Go déjà téléchargés (pour le calcul d'espace).
            $somme = (Get-ChildItem $_.FullName -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
            if ($somme) { $deja[$_.Name] = [math]::Round($somme / 1GB, 2) }
        }
    }
    return [ordered]@{ actif = $actif; installes = $installes; deja = $deja; moteur = (Test-Path $cpu); panne = [bool]$script:moteurEnPanne }
}

function Repondre($flux, $code, $type, [byte[]]$corps) {
    $texte = @{ 200 = 'OK'; 204 = 'No Content'; 400 = 'Bad Request'; 404 = 'Not Found'; 503 = 'Service Unavailable' }[$code]
    # La fenêtre (port 8765) parle au service (port 8766) : on l'autorise, elle seule.
    $entete = "HTTP/1.1 $code $texte`r`nContent-Type: $type`r`nContent-Length: $($corps.Length)`r`nCache-Control: no-store`r`nAccess-Control-Allow-Origin: http://127.0.0.1:$port`r`nConnection: close`r`n`r`n"
    $b = [System.Text.Encoding]::ASCII.GetBytes($entete)
    $flux.Write($b, 0, $b.Length)
    if ($corps.Length) { $flux.Write($corps, 0, $corps.Length) }
}
function Json($objet) { return [System.Text.Encoding]::UTF8.GetBytes(($objet | ConvertTo-Json -Compress -Depth 5)) }

function Etat-Installation {
    $e = [ordered]@{ etat = 'attente'; etape = 0; message = ''; pourcent = $null; recu = 0; total = 0; machine = ''; modele = $script:modeleEnCours }
    if ($script:installeur -and (Test-Path $etatFichier)) {
        try {
            $lu = Get-Content $etatFichier -Raw -Encoding UTF8 | ConvertFrom-Json
            $e.etat = $lu.etat; $e.etape = $lu.etape; $e.message = $lu.message; $e.machine = $lu.machine
            if ($lu.fichier -and (Test-Path $lu.fichier)) {
                # Octets déjà reçus : le bureau en tire la vitesse et le temps restant.
                $e.recu = [int64](Get-Item $lu.fichier).Length
                if ($lu.taille -gt 0) {
                    $e.total = [int64]$lu.taille
                    $e.pourcent = [math]::Min(100, [math]::Round($e.recu * 100 / $lu.taille))
                }
            }
        } catch { }
    } elseif ($script:installeur) { $e.etat = 'encours'; $e.message = 'Préparation…' }
    if ($script:installeur -and $script:installeur.HasExited -and $e.etat -eq 'encours') {
        $e.etat = 'erreur'; $e.message = "L'installation s'est arrêtée. Vérifie la connexion internet, puis réessaie."
    }
    return $e
}

$script:modeleEnCours = $null
function Lancer-Installation($id) {
    if ($script:installeur -and -not $script:installeur.HasExited) { return }
    if ($id -notmatch '^[a-z0-9.\-]+$') { return }
    Remove-Item $etatFichier -ErrorAction SilentlyContinue
    $script:modeleEnCours = $id
    $script_ = Join-Path $app 'preparer.ps1'
    $script:installeur = Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$script_`" -FichierEtat `"$etatFichier`" -Modele $id" -WindowStyle Hidden -PassThru
}

function Servir($client, $avecInterface) {
    try {
        $client.ReceiveTimeout = 3000
        $flux = $client.GetStream()
        $lecteur = New-Object System.IO.StreamReader($flux, [System.Text.Encoding]::ASCII, $false, 4096, $true)
        $ligne = $lecteur.ReadLine()
        if (-not $ligne) { return }
        while (($h = $lecteur.ReadLine()) -ne $null -and $h -ne '') { }
        $morceaux = $ligne.Split(' ')
        $methode = $morceaux[0]
        $chemin = [uri]::UnescapeDataString(($morceaux[1] -split '\?')[0])
        $requete = if ($morceaux[1] -match '\?(.*)$') { $Matches[1] } else { '' }
        $id = if ($requete -match '(?:^|&)id=([a-z0-9.\-]+)') { $Matches[1] } else { '' }
        switch ($chemin) {
            '/service/machine' {
                if (-not $script:machine) { $script:machine = Lire-Machine }
                Repondre $flux 200 'application/json' (Json $script:machine); return
            }
            '/service/modeles' { Repondre $flux 200 'application/json' (Json (Liste-Modeles)); return }
            '/service/raccourci-bureau' {
                $ok = if ($methode -eq 'POST') { Raccourci-Bureau-Windows $true } else { Test-Path $raccourciBureau }
                Repondre $flux 200 'application/json' (Json @{ ok = [bool]$ok; existe = (Test-Path $raccourciBureau) }); return
            }
            '/service/installation' { Repondre $flux 200 'application/json' (Json (Etat-Installation)); return }
            '/service/installer' {
                if ($methode -eq 'POST' -and $id) { Lancer-Installation $id }
                Repondre $flux 200 'application/json' (Json (Etat-Installation)); return
            }
            '/service/activer' {
                if ($methode -eq 'POST' -and $id -and (Test-Path (Join-Path (Join-Path $modeles $id) 'installe.ok'))) {
                    [System.IO.File]::WriteAllText((Join-Path $modeles 'choix.json'), (@{ actif = $id } | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
                    $script:activerDemande = $id
                    Repondre $flux 200 'application/json' (Json @{ ok = $true }); return
                }
                Repondre $flux 400 'application/json' (Json @{ ok = $false }); return
            }
        }
        if (-not $avecInterface) { Repondre $flux 404 'text/plain' ([byte[]]@()); return }
        # Mode installation : pas encore de moteur, c'est nous qui servons le bureau.
        if ($chemin -eq '/health' -or $chemin -like '/v1/*' -or $chemin -eq '/props') {
            Repondre $flux 503 'application/json' (Json @{ status = 'installation' }); return
        }
        $rel = $chemin.TrimStart('/'); if (-not $rel) { $rel = 'index.html' }
        $complet = [System.IO.Path]::GetFullPath((Join-Path $interface $rel))
        if ($complet.StartsWith($interface + '\') -and (Test-Path $complet -PathType Leaf)) {
            $type = $TYPES[[System.IO.Path]::GetExtension($complet).ToLower()]
            if (-not $type) { $type = 'application/octet-stream' }
            Repondre $flux 200 $type ([System.IO.File]::ReadAllBytes($complet))
        } else {
            Repondre $flux 404 'text/plain; charset=utf-8' ([System.Text.Encoding]::UTF8.GetBytes('Introuvable'))
        }
    } catch { } finally { $client.Close() }
}

# =====================================================================
#  Moteur IA
# =====================================================================
function Lancer-Moteur($exe, $couches) {
    # Un journal par moteur : celui de la carte graphique peut rester verrouillé
    # un instant après son arrêt, quand on bascule sur le processeur.
    $log = if ($couches -gt 0) { "$journal-gpu" } else { $journal }
    $a = @('-m', "`"$($script:modele.FullName)`"", '--host', '127.0.0.1', '--port', $port, '-c', '8192', '-np', '1',
           '-t', $script:coeurs, '-ngl', $couches, '--jinja', '--path', "`"$interface`"")
    if ($script:vision) { $a += @('--mmproj', "`"$($script:vision.FullName)`"") }
    return Start-Process $exe -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardError $log -RedirectStandardOutput "$log.out"
}

# Démarre le moteur et attend qu'il réponde (carte graphique d'abord si elle
# existe, sinon ou en cas de souci : processeur). Retourne $true si prêt.
function Demarrer-Moteur($avecEcran) {
    $dossier = Dossier-Actif
    if (-not $dossier) { return $false }
    $script:modele = Get-ChildItem $dossier -Filter *.gguf | Where-Object { $_.Name -notmatch 'mmproj' } | Select-Object -First 1
    $script:vision = Get-ChildItem $dossier -Filter *mmproj*.gguf -ErrorAction SilentlyContinue | Select-Object -First 1
    $carte = Get-CimInstance Win32_VideoController | Where-Object { $_.Name -match 'NVIDIA|Radeon RX|Arc A|Arc B' } | Select-Object -First 1
    $script:coeurs = (Get-CimInstance Win32_Processor | Measure-Object NumberOfCores -Sum).Sum
    if (-not $script:coeurs) { $script:coeurs = [math]::Max(1, [int]($env:NUMBER_OF_PROCESSORS / 2)) }
    $script:essaiGpu = [bool]($carte -and (Test-Path $gpuExe))
    $script:serveur = if ($script:essaiGpu) { Lancer-Moteur $gpuExe 99 } else { Lancer-Moteur $cpu 0 }
    $script:debut = Get-Date
    $script:ok = $false

    # Variables au niveau du script : la minuterie les appelle plus tard.
    $script:tic = {
        if (Pret) { $script:ok = $true; return $true }
        $ecoule = ((Get-Date) - $script:debut).TotalSeconds
        $mort = $script:serveur.HasExited
        # La carte graphique ne coopère pas : on repasse sur le processeur.
        if ($script:essaiGpu -and ($mort -or $ecoule -gt 90)) {
            if (-not $mort) { Stop-Process -Id $script:serveur.Id -Force -ErrorAction SilentlyContinue }
            $script:essaiGpu = $false
            try { $script:serveur = Lancer-Moteur $cpu 0 } catch { return $true }
            $script:debut = Get-Date
            return $false
        }
        return ($mort -or $ecoule -gt 240)
    }

    if ($avecEcran) {
        # Petit écran « je me réveille » (la fenêtre n'est pas encore ouverte)
        $script:form = New-Object System.Windows.Forms.Form
        $form = $script:form
        $form.Text = 'Mon assistante'
        $form.Size = New-Object System.Drawing.Size(460, 190)
        $form.StartPosition = 'CenterScreen'
        $form.FormBorderStyle = 'FixedDialog'
        $form.MaximizeBox = $false; $form.MinimizeBox = $false
        $form.BackColor = [System.Drawing.Color]::White
        $etiq = New-Object System.Windows.Forms.Label
        $etiq.Text = "Ton mini-ordinateur se réveille…`nÇa prend de 20 secondes à une minute."
        $etiq.Font = New-Object System.Drawing.Font('Segoe UI', 12)
        $etiq.AutoSize = $false; $etiq.TextAlign = 'MiddleCenter'
        $etiq.Size = New-Object System.Drawing.Size(420, 70); $etiq.Location = New-Object System.Drawing.Point(12, 18)
        $barre = New-Object System.Windows.Forms.ProgressBar
        $barre.Style = 'Marquee'; $barre.MarqueeAnimationSpeed = 30
        $barre.Size = New-Object System.Drawing.Size(400, 18); $barre.Location = New-Object System.Drawing.Point(22, 104)
        $form.Controls.AddRange(@($etiq, $barre))
        $script:minuterie = New-Object System.Windows.Forms.Timer
        $script:minuterie.Interval = 1000
        $script:minuterie.Add_Tick({ if (& $script:tic) { $script:minuterie.Stop(); $script:form.Close() } })
        $script:minuterie.Start()
        [void]$form.ShowDialog()
    } else {
        # La fenêtre est déjà ouverte (après l'installation) : elle affiche
        # elle-même « l'assistante se réveille ».
        while (-not (& $script:tic)) { Start-Sleep -Seconds 1 }
    }
    if (-not $script:ok -and -not $script:serveur.HasExited) { Stop-Process -Id $script:serveur.Id -Force -ErrorAction SilentlyContinue }
    return $script:ok
}

# =====================================================================
#  Départ
# =====================================================================
Mettre-A-Jour-Raccourci $racine $app
[void](Raccourci-Bureau-Windows $false)

# Déjà démarrée (double-clic une 2e fois, même pendant l'installation) :
# on rouvre juste la fenêtre.
function En-Installation {
    try { return (Invoke-WebRequest "${url}service/modeles" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { return $false }
}
if ((Pret) -or (En-Installation)) { Ouvrir-Fenetre | Out-Null; exit 0 }

$script:serveur = $null
$script:moteurEnPanne = $false
$fenetre = $null
$ecoute = $null

# Ouvre l'écoute qui convient : avec le moteur, seulement le service (8766) ;
# sans moteur (pas encore d'IA, ou le modèle choisi ne démarre pas sur cet
# ordinateur), c'est nous qui servons le bureau (8765) pour qu'elle puisse
# choisir ou installer un autre modèle dans « Modèles IA ».
function Ecouter($moteurOk) {
    if ($script:ecoute) { try { $script:ecoute.Stop() } catch {} }
    $script:modeInstallation = -not $moteurOk
    $p = if ($moteurOk) { $portService } else { $port }
    $script:ecoute = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $p)
    $script:ecoute.Start()
}
function Essayer-Moteur($avecEcran) {
    $ok = $false
    try { $ok = [bool](Demarrer-Moteur $avecEcran) } catch { $ok = $false }
    if (-not $ok -and $script:form) { try { $script:form.Close() } catch {} }
    $script:moteurEnPanne = -not $ok
    if (-not $ok) { try { Add-Content $journal "`r`n[$(Get-Date)] Le moteur n'a pas démarré avec ce modèle : retour à « Modèles IA »." } catch {} }
    Ecouter $ok
}

if (IA-Installee) { Essayer-Moteur $true }
else { Ecouter $false }   # Pas encore d'IA : le bureau s'ouvre sur « Modèles IA ».
$ecoute = $script:ecoute
$fenetre = Ouvrir-Fenetre

$derniereVerif = Get-Date
try {
    while ($true) {
        if ($ecoute.Pending()) { Servir $ecoute.AcceptTcpClient() $script:modeInstallation; continue }
        Start-Sleep -Milliseconds 40

        # Première installation terminée : on passe du petit serveur au vrai moteur.
        if ($script:modeInstallation -and $script:installeur -and $script:installeur.HasExited -and (IA-Installee)) {
            $fin = (Get-Date).AddSeconds(3)   # le temps que la fenêtre lise « fini »
            while ((Get-Date) -lt $fin) { if ($ecoute.Pending()) { Servir $ecoute.AcceptTcpClient() $true } else { Start-Sleep -Milliseconds 40 } }
            $ecoute.Stop()
            $script:installeur = $null
            # Le modèle actif ne démarrait pas : celui qu'elle vient d'installer le remplace.
            if ($script:moteurEnPanne -and $script:modeleEnCours -and (Test-Path (Join-Path (Join-Path $modeles $script:modeleEnCours) 'installe.ok'))) {
                [System.IO.File]::WriteAllText((Join-Path $modeles 'choix.json'), (@{ actif = $script:modeleEnCours } | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
            }
            Essayer-Moteur $false
            $ecoute = $script:ecoute
        }

        # Changement de modèle demandé dans l'appli (aussi depuis le bureau de
        # secours quand le modèle précédent ne démarrait pas) : on (re)démarre le moteur.
        if ($script:activerDemande -and -not ($script:installeur -and -not $script:installeur.HasExited)) {
            $script:activerDemande = $null
            $fin = (Get-Date).AddSeconds(1)   # le temps de finir de répondre à la fenêtre
            while ((Get-Date) -lt $fin) { if ($ecoute.Pending()) { Servir $ecoute.AcceptTcpClient() $script:modeInstallation } else { Start-Sleep -Milliseconds 40 } }
            $ecoute.Stop()
            if ($script:serveur -and -not $script:serveur.HasExited) { Stop-Process -Id $script:serveur.Id -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 1 }
            Essayer-Moteur $false
            $ecoute = $script:ecoute
        }

        # Fenêtre fermée : on arrête tout.
        if (((Get-Date) - $derniereVerif).TotalSeconds -ge 3) {
            $derniereVerif = Get-Date
            if (-not (Fenetre-Ouverte) -and ($fenetre -eq $null -or $fenetre.HasExited)) { break }
        }
    }
} finally {
    try { $ecoute.Stop() } catch {}
    if ($script:installeur -and -not $script:installeur.HasExited) { Stop-Process -Id $script:installeur.Id -Force -ErrorAction SilentlyContinue }
    if ($script:serveur -and -not $script:serveur.HasExited) { Stop-Process -Id $script:serveur.Id -Force -ErrorAction SilentlyContinue }
}
