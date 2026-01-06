package com.rmrbranco.galacticcom

import kotlin.random.Random

object CatastropheEngine {

    private const val EVENT_PROBABILITY = 0.2
    private const val CHAR_SWAP_PROBABILITY = 0.05
    private const val WORD_SWAP_PROBABILITY = 0.1

    private const val MIN_DELAY_SECONDS = 5
    private const val MAX_DELAY_SECONDS = 60

    private val wordDictionary = listOf("planet", "galaxy", "star", "ship", "space", "time", "black hole")

    data class ProcessedMessage(
        val corruptedMessage: String,
        val additionalDelaySeconds: Int,
        val catastropheType: String?
    )

    // Enum to define all possible catastrophe types
    private enum class CatastropheType {
        TEMPORAL_ANOMALY,
        SIGNAL_INTERFERENCE,
        CRITICAL_FAILURE,
        SUPERNOVA,
        SOLAR_WIND,
        COSMIC_DUST
    }

    fun processMessage(message: String): ProcessedMessage {
        if (Random.nextDouble() > EVENT_PROBABILITY) {
            return ProcessedMessage(message, 0, null) // No event
        }

        // Choose a random catastrophe from our list
        val catastrophe = CatastropheType.values().random()

        var corruptedMessage = message
        var additionalDelay = 0
        val catastropheLabel: String

        when (catastrophe) {
            CatastropheType.TEMPORAL_ANOMALY -> {
                additionalDelay = addDelay()
                catastropheLabel = "TEMPORAL ANOMALY DETECTED"
            }
            CatastropheType.SIGNAL_INTERFERENCE -> {
                corruptedMessage = legacyCorruptText(message)
                catastropheLabel = "SIGNAL INTERFERENCE"
            }
            CatastropheType.CRITICAL_FAILURE -> {
                corruptedMessage = legacyCorruptText(message)
                additionalDelay = addDelay()
                catastropheLabel = "CRITICAL TRANSMISSION FAILURE"
            }
            CatastropheType.SUPERNOVA -> {
                corruptedMessage = truncateMessage(message)
                catastropheLabel = "SUPERNOVA ALERT"
            }
            CatastropheType.SOLAR_WIND -> {
                corruptedMessage = alternateCase(message)
                catastropheLabel = "SOLAR WIND DETECTED"
            }
            CatastropheType.COSMIC_DUST -> {
                corruptedMessage = insertCosmicDust(message)
                catastropheLabel = "COSMIC DUST INTERFERENCE"
            }
        }

        return ProcessedMessage(corruptedMessage, additionalDelay, catastropheLabel)
    }

    //region Catastrophe Effects
    private fun swapCharacters(message: String): String {
        val messageBuilder = StringBuilder()
        for (char in message) {
            if (Random.nextDouble() < CHAR_SWAP_PROBABILITY) {
                messageBuilder.append(getRandomChar())
            } else {
                messageBuilder.append(char)
            }
        }
        return messageBuilder.toString()
    }

    private fun swapWords(message: String): String {
        val words = message.split(" ").toMutableList()
        if (words.isEmpty() || wordDictionary.isEmpty()) return message

        val wordsToSwap = (words.size * WORD_SWAP_PROBABILITY).toInt().coerceAtLeast(1)

        repeat(wordsToSwap) {
            val randomIndexToSwap = Random.nextInt(words.size)
            val randomWordFromDict = wordDictionary.random()
            words[randomIndexToSwap] = randomWordFromDict
        }
        return words.joinToString(" ")
    }

    private fun addDelay(): Int {
        return Random.nextInt(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS + 1)
    }

    private fun truncateMessage(message: String): String {
        if (message.length < 10) return "... [SIGNAL LOST]"
        val cutPoint = Random.nextInt(message.length / 2, (message.length - 1).coerceAtLeast(0))
        return message.substring(0, cutPoint) + "... [SIGNAL LOST DUE TO SUPERNOVA]"
    }

    private fun alternateCase(message: String): String {
        val result = StringBuilder()
        message.forEach { char ->
            result.append(if (Random.nextBoolean()) char.uppercase() else char.lowercase())
        }
        return result.toString()
    }

    private fun insertCosmicDust(message: String): String {
        val dustChars = listOf('.', '*', '`', '\'')
        val result = StringBuilder()
        message.forEach { char ->
            result.append(char)
            if (Random.nextDouble() < 0.15) { // 15% chance of adding dust after a character
                result.append(dustChars.random())
            }
        }
        return result.toString()
    }

    // Combines the original text corruption effects
    private fun legacyCorruptText(message: String): String {
        var corrupted = message
        if (Random.nextBoolean()) {
            corrupted = swapWords(corrupted)
        }
        if (Random.nextBoolean()) {
            corrupted = swapCharacters(corrupted)
        }
        // Ensures that if corruption was chosen, at least one effect happens
        if (message == corrupted) {
            corrupted = swapCharacters(corrupted)
        }
        return corrupted
    }

    private fun getRandomChar(): Char {
        val glitchChars = "*#§!?@%&"
        return glitchChars.random()
    }
    //endregion
}