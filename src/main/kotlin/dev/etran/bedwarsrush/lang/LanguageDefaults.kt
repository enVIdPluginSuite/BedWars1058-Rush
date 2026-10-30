package dev.etran.bedwarsrush.lang

import com.andrei1058.bedwars.api.language.Language

/**
 * Registers the in-game texts (start tutorial, bridge mode on/off) in every BedWars1058 language file.
 * Existing values are never overwritten, so server owners can re-word or translate them in BedWars1058's lang folder.
 */
object LanguageDefaults {
    private class Texts(val tutorial: List<String>, val bridgeOn: String, val bridgeOff: String)

    private val bar = "&a" + "▬".repeat(72)

    private fun tutorial(title: String, l1: String, l2: String, l3: String) = listOf(
        bar,
        "&f&l                       $title",
        "",
        "&e&l        $l1",
        "&e&l       $l2",
        "&e&l             $l3",
        "",
        bar,
    )

    private val english = Texts(
        tutorial(
            "Bed Wars Rush",
            "Generators are maxed out and your bed is already",
            "fortified. Hold a block and left click to switch",
            "bridge mode on or off, then place to extend!",
        ),
        "&a&lBRIDGE MODE ON",
        "&c&lBRIDGE MODE OFF",
    )

    private val translations: Map<String, Texts> = mapOf(
        "pt" to Texts(
            tutorial(
                "Bed Wars Rush",
                "Os geradores estão no máximo e sua cama já está",
                "fortificada. Segure um bloco e clique com o botão",
                "esquerdo para ligar ou desligar o modo ponte!",
            ),
            "&a&lMODO PONTE LIGADO",
            "&c&lMODO PONTE DESLIGADO",
        ),
        "es" to Texts(
            tutorial(
                "Bed Wars Rush",
                "Los generadores están al máximo y tu cama ya está",
                "fortificada. Sostén un bloque y haz clic izquierdo",
                "para activar o desactivar el modo puente.",
            ),
            "&a&lMODO PUENTE ACTIVADO",
            "&c&lMODO PUENTE DESACTIVADO",
        ),
        "de" to Texts(
            tutorial(
                "Bed Wars Rush",
                "Die Generatoren sind voll ausgebaut und dein Bett",
                "ist bereits gesichert. Halte einen Block und klicke",
                "links, um den Brückenmodus ein- oder auszuschalten.",
            ),
            "&a&lBRÜCKENMODUS AN",
            "&c&lBRÜCKENMODUS AUS",
        ),
        "fr" to Texts(
            tutorial(
                "Bed Wars Rush",
                "Les générateurs sont au maximum et votre lit est",
                "déjà fortifié. Tenez un bloc et faites un clic gauche",
                "pour activer ou désactiver le mode pont.",
            ),
            "&a&lMODE PONT ACTIVÉ",
            "&c&lMODE PONT DÉSACTIVÉ",
        ),
        "it" to Texts(
            tutorial(
                "Bed Wars Rush",
                "I generatori sono al massimo e il tuo letto è già",
                "fortificato. Tieni un blocco e fai clic sinistro per",
                "attivare o disattivare la modalità ponte.",
            ),
            "&a&lMODALITÀ PONTE ON",
            "&c&lMODALITÀ PONTE OFF",
        ),
    )

    /** @return how many language files were updated */
    fun inject(): Int {
        var updated = 0
        for (language in Language.getLanguages()) {
            val texts = translations[language.iso.lowercase()] ?: english
            val yml = language.yml
            yml.addDefault(Paths.ARENA_START_TUTORIAL, texts.tutorial)
            yml.addDefault(Paths.BRIDGE_MODE_ACTIVATED, texts.bridgeOn)
            yml.addDefault(Paths.BRIDGE_MODE_DEACTIVATED, texts.bridgeOff)
            yml.options().copyDefaults(true)
            language.save()
            updated++
        }
        return updated
    }
}
