package com.codexlocal.outils

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * Boîte à outils de l'APK perso : minuteur / pomodoro, chronomètre, taxes du Québec,
 * liste de tâches et convertisseur. Thème noir, accents orange et or.
 */
class OutilsActivity : AppCompatActivity() {

    private val orange = Color.parseColor("#FF7A00")
    private val bleu = Color.parseColor("#38BDF8")
    private val or = Color.parseColor("#FFC107")
    private lateinit var zone: LinearLayout
    private var minuteur: CountDownTimer? = null
    private val horloge = Handler(Looper.getMainLooper())
    private var tic: Runnable? = null
    private var dansOutil = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        zone = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); isFillViewport = true; addView(zone) })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dansOutil) accueil() else finish()
            }
        })
        accueil()
    }

    override fun onDestroy() {
        arreterTout()
        super.onDestroy()
    }

    private fun arreterTout() {
        minuteur?.cancel(); minuteur = null
        tic?.let { horloge.removeCallbacks(it) }; tic = null
    }

    // ------------------------------------------------------------- briques

    private fun titre(texte: String) = TextView(this).apply {
        text = texte; setTextColor(orange); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(12))
    }

    private fun texte(t: String, couleur: Int = Color.WHITE, taille: Float = 15f) = TextView(this).apply {
        text = t; setTextColor(couleur); textSize = taille
    }

    private fun bouton(t: String, action: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(or); textSize = 16f
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.parseColor("#141414")); setStroke(dp(2), orange) }
        setOnClickListener { action() }
    }

    private fun champ(indice: String, nombre: Boolean = true) = EditText(this).apply {
        hint = indice; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 18f
        if (nombre) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun ajouter(v: View, haut: Int = 8) {
        zone.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(haut), 0, 0) })
    }

    private fun rangee(vararg vues: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        vues.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
    }

    private fun bip() = runCatching {
        ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
    }

    private fun nombre(e: EditText) = e.text.toString().replace(',', '.').replace(" ", "").toDoubleOrNull()

    // ------------------------------------------------------------- accueil

    private fun accueil() {
        arreterTout(); dansOutil = false
        zone.removeAllViews()
        ajouter(titre("🧰 Outils"), 0)
        listOf(
            "⏳ Minuteur & Pomodoro" to ::outilMinuteur,
            "⏱️ Chronomètre" to ::outilChrono,
            "🧾 Taxes du Québec (TPS + TVQ)" to ::outilTaxes,
            "✅ Liste de tâches" to ::outilTaches,
            "📏 Convertisseur" to ::outilConvertisseur,
        ).forEach { (nom, ouvrir) -> ajouter(bouton(nom) { dansOutil = true; zone.removeAllViews(); ouvrir() }, 10) }
        ajouter(bouton("👤 Comment l'IA t'appelle" + prenom(this).let { if (it.isBlank()) "" else " : $it" }) {
            demanderPrenom(this) { accueil() }
        }, 10)
        ajouter(bouton("📅 Agenda") { startActivity(android.content.Intent(this, AgendaActivity::class.java)) }, 10)
        val notif = CheckBox(this).apply {
            text = "🔔 Notifications des nouvelles (environ aux 3 h)"
            setTextColor(Color.WHITE); textSize = 15f
            buttonTintList = android.content.res.ColorStateList.valueOf(orange)
            isChecked = NotifNouvelles.actives(this@OutilsActivity)
            setOnCheckedChangeListener { _, c ->
                if (c && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 8)
                }
                NotifNouvelles.definir(this@OutilsActivity, c)
            }
        }
        ajouter(notif, 20)
        ajouter(bouton("🔊 Écouter les nouvelles (voix québécoise)") {
            com.codexlocal.chat.LecteurVoix.preparer(this)
            Thread {
                val texte = runCatching { com.codexlocal.web.RechercheRiche.manchettes(6) }.getOrDefault(emptyList())
                    .joinToString("\n") { (titre, _, source) -> "$titre. ${if (source.isBlank()) "" else "Source : $source."}" }
                runOnUiThread {
                    if (texte.isBlank()) android.widget.Toast.makeText(this, "Nouvelles indisponibles", android.widget.Toast.LENGTH_SHORT).show()
                    else com.codexlocal.chat.LecteurVoix.lire(this, "Voici les nouvelles du moment.\n$texte")
                }
            }.start()
        }, 8)
        ajouter(rangee(
            bouton("🗣️ Choisir la voix") { choisirVoix() },
            bouton("⏹ Stop") { com.codexlocal.chat.LecteurVoix.arreter() }
        ), 8)
        ajouter(bouton("📰 Voir les manchettes maintenant") {
            Thread { NotifNouvelles.verifier(applicationContext) }.start()
            android.widget.Toast.makeText(this, "Les nouvelles arrivent en notification…", android.widget.Toast.LENGTH_SHORT).show()
        }, 8)
    }

    companion object {
        /** Prénom de l'utilisateur : l'IA l'appelle par son nom. */
        fun prenom(c: android.content.Context): String =
            c.getSharedPreferences("outils", android.content.Context.MODE_PRIVATE).getString("prenom", "").orEmpty()

        fun demanderPrenom(c: android.content.Context, ensuite: (() -> Unit)? = null) {
            val champ = EditText(c).apply {
                hint = "Ton nom ou surnom"; setText(prenom(c)); inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_CLASS_TEXT
            }
            android.app.AlertDialog.Builder(c)
                .setTitle("👋 Bienvenue!")
                .setMessage("Écris comment tu veux qu'on t'appelle — pas besoin de compte. L'IA va t'appeler comme ça.")
                .setView(champ)
                .setPositiveButton("OK") { _, _ ->
                    c.getSharedPreferences("outils", android.content.Context.MODE_PRIVATE).edit()
                        .putString("prenom", champ.text.toString().trim().take(40)).apply()
                    ensuite?.invoke()
                }
                .setNegativeButton("Plus tard", null)
                .show()
        }
    }

    private fun choisirVoix() {
        com.codexlocal.chat.LecteurVoix.preparer(this)
        val voix = com.codexlocal.chat.LecteurVoix.voixQuebec()
        if (voix.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Voix québécoise")
                .setMessage("Aucune voix « français (Canada) » trouvée pour l'instant. Réessaie dans quelques secondes, ou installe-la : Paramètres du téléphone → Synthèse vocale → Données vocales → Français (Canada).")
                .setPositiveButton("OK", null)
                .setNeutralButton("Ouvrir les réglages") { _, _ ->
                    runCatching { startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS")) }
                }
                .show()
            return
        }
        val noms = voix.mapIndexed { i, v -> "Voix ${i + 1}" + (if (v.isNetworkConnectionRequired) " (en ligne)" else " (hors ligne)") + " · qualité ${v.quality}" }
        android.app.AlertDialog.Builder(this)
            .setTitle("🗣️ Voix québécoise")
            .setItems(noms.toTypedArray()) { _, i ->
                com.codexlocal.chat.LecteurVoix.choisir(this, voix[i].name)
                com.codexlocal.chat.LecteurVoix.lire(this, "Salut! C'est moi, ta voix québécoise. Comment ça va aujourd'hui?")
            }
            .setNeutralButton("Vitesse") { _, _ ->
                val vitesses = listOf(0.8f, 1.0f, 1.2f, 1.5f)
                android.app.AlertDialog.Builder(this).setTitle("Vitesse de lecture")
                    .setItems(arrayOf("Lente", "Normale", "Rapide", "Très rapide")) { _, j ->
                        com.codexlocal.chat.LecteurVoix.vitesse(this, vitesses[j])
                    }.show()
            }
            .show()
    }

    // ------------------------------------------------------------- minuteur

    private fun outilMinuteur() {
        ajouter(titre("⏳ Minuteur"), 0)
        val affichage = texte("00:00", or, 64f).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        val minutes = champ("Minutes (ex. 10)")
        val etat = texte("", bleu).apply { gravity = Gravity.CENTER }
        fun lancer(min: Double, libelle: String, ensuite: (() -> Unit)? = null) {
            minuteur?.cancel()
            etat.text = libelle
            minuteur = object : CountDownTimer((min * 60_000).toLong(), 250) {
                override fun onTick(reste: Long) {
                    val s = (reste / 1000) + 1
                    affichage.text = String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
                }
                override fun onFinish() {
                    affichage.text = "00:00"; bip()
                    etat.text = "🔔 Terminé !"
                    ensuite?.invoke()
                }
            }.start()
        }
        ajouter(affichage); ajouter(minutes); ajouter(etat)
        ajouter(rangee(
            bouton("▶ Démarrer") { nombre(minutes)?.takeIf { it > 0 }?.let { lancer(it, "⏳ En cours") } },
            bouton("■ Arrêter") { minuteur?.cancel(); etat.text = "Arrêté" }
        ))
        ajouter(texte("🍅 Pomodoro : 25 min de travail puis 5 min de pause", Color.GRAY), 18)
        ajouter(bouton("🍅 Lancer un pomodoro") { lancer(25.0, "🍅 Travail — concentre-toi") { lancer(5.0, "☕ Pause") } })
        ajouter(bouton("← Retour") { accueil() }, 24)
    }

    // ------------------------------------------------------------- chrono

    private fun outilChrono() {
        ajouter(titre("⏱️ Chronomètre"), 0)
        val affichage = texte("00:00.0", or, 56f).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        val tours = texte("", Color.WHITE)
        var debut = 0L; var cumul = 0L; var marche = false; var n = 0
        fun temps() = cumul + if (marche) SystemClock.elapsedRealtime() - debut else 0
        fun format(ms: Long) = String.format(Locale.ROOT, "%02d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)
        val boucle = object : Runnable {
            override fun run() { affichage.text = format(temps()); if (marche) horloge.postDelayed(this, 50) }
        }
        tic = boucle
        ajouter(affichage)
        ajouter(rangee(
            bouton("▶ / ❚❚") {
                if (marche) { cumul = temps(); marche = false } else { debut = SystemClock.elapsedRealtime(); marche = true; horloge.post(boucle) }
            },
            bouton("🏁 Tour") { if (marche) { n++; tours.text = "Tour $n : ${format(temps())}\n" + tours.text } },
            bouton("↺") { marche = false; cumul = 0; n = 0; tours.text = ""; affichage.text = format(0) }
        ))
        ajouter(tours, 14)
        ajouter(bouton("← Retour") { accueil() }, 24)
    }

    // ------------------------------------------------------------- taxes

    private fun outilTaxes() {
        ajouter(titre("🧾 Taxes du Québec"), 0)
        val montant = champ("Montant ($)")
        val resultat = texte("", Color.WHITE, 18f)
        val argent = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
        ajouter(montant)
        ajouter(rangee(
            bouton("Avant taxes → total") {
                val m = nombre(montant) ?: return@bouton
                val tps = m * 0.05; val tvq = m * 0.09975
                resultat.text = "Montant : ${argent.format(m)}\nTPS (5 %) : ${argent.format(tps)}\nTVQ (9,975 %) : ${argent.format(tvq)}\n💰 Total : ${argent.format(m + tps + tvq)}"
            },
            bouton("Total → avant taxes") {
                val t = nombre(montant) ?: return@bouton
                val m = t / 1.14975
                resultat.text = "Total : ${argent.format(t)}\nAvant taxes : ${argent.format(m)}\nTPS : ${argent.format(m * 0.05)}\nTVQ : ${argent.format(m * 0.09975)}"
            }
        ))
        ajouter(resultat, 16)
        ajouter(bouton("← Retour") { accueil() }, 24)
    }

    // ------------------------------------------------------------- tâches

    private val sp by lazy { getSharedPreferences("outils", MODE_PRIVATE) }

    private fun outilTaches() {
        ajouter(titre("✅ Liste de tâches"), 0)
        val nouvelle = champ("Nouvelle tâche…", nombre = false)
        val liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun lire(): JSONArray = runCatching { JSONArray(sp.getString("taches", "[]")) }.getOrDefault(JSONArray())
        fun ecrire(a: JSONArray) = sp.edit().putString("taches", a.toString()).apply()
        fun dessiner() {
            liste.removeAllViews()
            val a = lire()
            if (a.length() == 0) liste.addView(texte("Aucune tâche 🎉", Color.GRAY))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val case = CheckBox(this).apply {
                    text = o.optString("t"); isChecked = o.optBoolean("f")
                    setTextColor(if (isChecked) Color.GRAY else Color.WHITE); textSize = 16f
                    buttonTintList = android.content.res.ColorStateList.valueOf(orange)
                    setOnCheckedChangeListener { _, c -> val b = lire(); b.getJSONObject(i).put("f", c); ecrire(b); dessiner() }
                }
                ligne.addView(case, LinearLayout.LayoutParams(0, -2, 1f))
                ligne.addView(texte("🗑️", Color.WHITE, 18f).apply {
                    setPadding(dp(12), dp(6), dp(6), dp(6))
                    setOnClickListener { val b = lire(); b.remove(i); ecrire(b); dessiner() }
                })
                liste.addView(ligne)
            }
        }
        ajouter(nouvelle)
        ajouter(bouton("＋ Ajouter") {
            val t = nouvelle.text.toString().trim()
            if (t.isNotEmpty()) { ecrire(lire().put(JSONObject().put("t", t).put("f", false))); nouvelle.setText(""); dessiner() }
        })
        ajouter(liste, 14)
        ajouter(bouton("🧹 Effacer les tâches faites") {
            val a = lire(); val b = JSONArray()
            for (i in 0 until a.length()) if (!a.getJSONObject(i).optBoolean("f")) b.put(a.getJSONObject(i))
            ecrire(b); dessiner()
        }, 14)
        ajouter(bouton("← Retour") { accueil() }, 24)
        dessiner()
    }

    // ------------------------------------------------------------- convertisseur

    private data class Conversion(val nom: String, val a: String, val b: String, val versB: (Double) -> Double, val versA: (Double) -> Double)

    private val conversions = listOf(
        Conversion("Température", "°C", "°F", { it * 9 / 5 + 32 }, { (it - 32) * 5 / 9 }),
        Conversion("Distance", "km", "milles", { it / 1.609344 }, { it * 1.609344 }),
        Conversion("Longueur", "cm", "pouces", { it / 2.54 }, { it * 2.54 }),
        Conversion("Longueur", "m", "pieds", { it * 3.28084 }, { it / 3.28084 }),
        Conversion("Poids", "kg", "livres", { it * 2.20462 }, { it / 2.20462 }),
        Conversion("Volume", "litres", "gallons US", { it / 3.78541 }, { it * 3.78541 }),
        Conversion("Vitesse", "km/h", "mi/h", { it / 1.609344 }, { it * 1.609344 }),
    )

    private fun outilConvertisseur() {
        ajouter(titre("📏 Convertisseur"), 0)
        val choix = Spinner(this).apply {
            adapter = ArrayAdapter(this@OutilsActivity, android.R.layout.simple_spinner_dropdown_item,
                conversions.map { "${it.nom} : ${it.a} ⇄ ${it.b}" })
        }
        val valeur = champ("Valeur")
        val resultat = texte("", or, 22f)
        val format = NumberFormat.getNumberInstance(Locale.CANADA_FRENCH).apply { maximumFractionDigits = 3 }
        ajouter(choix); ajouter(valeur)
        ajouter(rangee(
            bouton("→") {
                val c = conversions[choix.selectedItemPosition]; val v = nombre(valeur) ?: return@bouton
                resultat.text = "${format.format(v)} ${c.a} = ${format.format(c.versB(v))} ${c.b}"
            },
            bouton("←") {
                val c = conversions[choix.selectedItemPosition]; val v = nombre(valeur) ?: return@bouton
                resultat.text = "${format.format(v)} ${c.b} = ${format.format(c.versA(v))} ${c.a}"
            }
        ))
        ajouter(resultat, 16)
        ajouter(bouton("← Retour") { accueil() }, 24)
    }
}
