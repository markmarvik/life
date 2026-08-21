package com.life.app.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuzzyTest {

    @Test
    fun exactAndPrefixBeatTypo() {
        val exact = Fuzzy.score("meditate", "Meditate")
        val prefix = Fuzzy.score("med", "Meditate")
        val typo = Fuzzy.score("meditete", "Meditate")
        val miss = Fuzzy.score("zzzz", "Meditate")
        assertTrue(exact > prefix)
        assertTrue(prefix > typo)
        assertTrue(typo > 0.45)
        assertTrue(miss < 0.45)
    }

    @Test
    fun tokenStartMatches() {
        assertTrue(Fuzzy.matches("sun", "Morning sunlight"))
        assertTrue(Fuzzy.matches("walk", "Lunch walk"))
    }

    @Test
    fun subsequenceMatches() {
        assertTrue(Fuzzy.matches("mngsun", "Morning sunlight") || Fuzzy.score("msun", "Morning sunlight") > 0.45)
        assertTrue(Fuzzy.matches("brush", "Brush + floss"))
    }
}
