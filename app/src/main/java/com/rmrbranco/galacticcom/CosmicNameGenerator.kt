package com.rmrbranco.galacticcom

import kotlin.random.Random

object CosmicNameGenerator {

    private val galaxyPrefixes = arrayOf("Andro", "Centa", "Triangu", "Sombre", "Magella", "Circin", "Sculpto")
    private val galaxySuffixes = arrayOf("meda", "urus", "lum", "ro", "nic", "us", "r")
    private val galaxyMorphologies = arrayOf("Spiral", "Elliptical", "Lenticular", "Irregular", "Dwarf")
    private val galaxyCompositions = arrayOf("Stars, Gas and Dust", "Dark Matter and Old Stars", "Ionized Gas and Intense Star Formation")

    private val starPrefixes = arrayOf("Alpha", "Beta", "Gamma", "Delta", "Sir", "Vega", "Alta", "Procy")
    private val starSuffixes = arrayOf(" Majoris", " Minoris", " Centauri", " Lyrae", " Aquilae", " Canis", "onis")

    private val planetLetters = ('b'..'z').toList()

    fun generateGalaxyName(): String {
        val prefix = galaxyPrefixes.random()
        val suffix = galaxySuffixes.random()
        return "$prefix$suffix"
    }

    fun getMorphologyForGalaxy(galaxyName: String): String {
        val index = galaxyName.hashCode() % galaxyMorphologies.size
        return galaxyMorphologies[if (index < 0) index * -1 else index]
    }

    fun getGalaxyAge(galaxyName: String): String {
        val age = (galaxyName.hashCode() % 10) + 5 // Age between 5 and 14 billion years
        return "${if (age < 0) age * -1 else age} billion years"
    }

    fun getGalaxyDimension(galaxyName: String): String {
        val dimension = (galaxyName.hashCode() % 100) + 50 // Dimension between 50 and 149 thousand light-years
        return "${if (dimension < 0) dimension * -1 else dimension},000 light-years"
    }

    fun getGalaxyComposition(galaxyName: String): String {
        val index = galaxyName.hashCode() % galaxyCompositions.size
        return galaxyCompositions[if (index < 0) index * -1 else index]
    }

    fun getHabitablePlanets(galaxyName: String): Int {
        val planets = (galaxyName.hashCode() % 100) * 100
        return if (planets < 0) planets * -1 else planets
    }

    fun getNonHabitablePlanets(galaxyName: String): Int {
        val planets = (galaxyName.hashCode() % 1000) * 1000
        return if (planets < 0) planets * -1 else planets
    }

    fun generateStarName(): String {
        val prefix = starPrefixes.random()
        val suffix = starSuffixes.random()
        return "$prefix$suffix"
    }

    fun generatePlanetName(starName: String): String {
        val starInitial = starName.split(" ").first().take(3).uppercase()
        val number = Random.nextInt(1, 10)
        val letter = planetLetters.random()
        return "$starInitial-$number$letter (Habitable)"
    }

    fun generateGalaxies(count: Int = 20): List<GalaxyInfo> {
        val galaxyNames = List(count) { generateGalaxyName() }.distinct()
        return galaxyNames.map { name ->
            GalaxyInfo(
                name = name,
                inhabitants = Random.nextInt(1_000, 10_000_000),
                messageCount = Random.nextInt(0, 5000), // Added this line
                morphology = getMorphologyForGalaxy(name),
                age = getGalaxyAge(name),
                dimension = getGalaxyDimension(name),
                composition = getGalaxyComposition(name),
                habitablePlanets = getHabitablePlanets(name),
                nonHabitablePlanets = getNonHabitablePlanets(name)
            )
        }
    }

    fun generateStars(galaxyName: String, count: Int = 5): List<String> {
        return List(count) { generateStarName() }.distinct()
    }

    fun generatePlanets(starName: String, count: Int = 3): List<String> {
        return List(count) { generatePlanetName(starName) }.distinct()
    }
}
