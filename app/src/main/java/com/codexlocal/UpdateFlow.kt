package com.codexlocal

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Enchainement complet d'une mise a jour : verification, dialogue,
 * telechargement, puis installation par Android.
 */
object UpdateFlow {

    /**
     * @param silencieux si vrai, n'affiche rien quand l'appli est deja a jour
     *                   (utilise au demarrage de l'appli).
     */
    fun verifier(
        scope: CoroutineScope,
        activity: Activity,
        silencieux: Boolean,
        onStatus: (String) -> Unit
    ) {
        scope.launch {
            if (!silencieux) onStatus("Recherche d'une mise à jour…")
            val info = try {
                UpdateChecker.verifier(activity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silencieux) onStatus(e.message ?: "Vérification impossible.")
                return@launch
            }
            when {
                info != null -> proposer(scope, activity, info, onStatus)
                silencieux -> Unit
                else -> onStatus(
                    activity.getString(
                        R.string.update_none,
                        UpdateChecker.versionInstallee(activity)
                    )
                )
            }
        }
    }

    private fun proposer(
        scope: CoroutineScope,
        activity: Activity,
        info: UpdateInfo,
        onStatus: (String) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(
                activity.getString(
                    R.string.update_available_msg,
                    info.version,
                    UpdateChecker.versionInstallee(activity)
                )
            )
            .setPositiveButton(R.string.update_download) { _, _ ->
                scope.launch {
                    onStatus("Téléchargement de la mise à jour…")
                    try {
                        val apk = Updater.telecharger(activity, info.apkUrl) { pourcent ->
                            activity.runOnUiThread {
                                onStatus(
                                    activity.getString(R.string.update_downloading, pourcent)
                                )
                            }
                        }
                        val lance = Updater.installer(activity, apk)
                        if (!lance) {
                            onStatus(activity.getString(R.string.update_permission))
                        } else {
                            onStatus("Installation lancée…")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onStatus("Échec du téléchargement : ${e.message}")
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
