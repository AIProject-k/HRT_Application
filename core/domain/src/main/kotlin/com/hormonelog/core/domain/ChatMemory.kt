package com.hormonelog.core.domain

/** Explicitly taught vocabulary and dose recipes, never evidence of an administration. */
sealed interface ChatMemory {
    val name: String

    data class Alias(override val name: String, val drug: Drug, val product: P4Product = P4Product.UNKNOWN) : ChatMemory {
        init { validName(name); require(drug == Drug.PROGESTERONE_MICRONIZED || product == P4Product.UNKNOWN) }
    }
    data class Shortcut(override val name: String, val doses: List<Dose>) : ChatMemory {
        init { validName(name); require(doses.size in 1..5) }
    }
    data class Dose(val drug: Drug, val route: Route, val amountMg: Double, val product: P4Product = P4Product.UNKNOWN) {
        init {
            require(amountMg.isFinite() && amountMg > 0 && amountMg <= 500)
            require(route in drug.allowedRoutes && route in setOf(Route.ORAL, Route.SUBLINGUAL, Route.IM_INJECTION, Route.SC_INJECTION))
            require(drug == Drug.PROGESTERONE_MICRONIZED || product == P4Product.UNKNOWN)
        }
    }
}

private fun validName(name: String) {
    require(name.isNotBlank() && name.length <= 32 && Regex("[\\p{L}\\p{N}_ -]+").matches(name))
    require(name.any { it.isLetter() })
}

/** Learned triggers must be words, not a substring of another product or a sentence. */
object ChatMemoryNames {
    fun mentioned(input: String, name: String): Boolean {
        val token = name.trim().split(Regex("\\s+")).joinToString("\\s*") { Regex.escape(it) }
        val before = "(?:(?<![\\p{L}\\p{N}_])|(?<=방금)|(?<=지금)|(?<=오늘)|(?<=어제)|(?<=그제)|(?<=먹은)|(?<=복용한))"
        val after = "(?=$|[^\\p{L}\\p{N}_]|[0-9]|은|는|을|를|이|가|도|와|과|먹|복용|기록|등록|저장)"
        return name.isNotBlank() && Regex(before + token + after, RegexOption.IGNORE_CASE).containsMatchIn(input)
    }
}
