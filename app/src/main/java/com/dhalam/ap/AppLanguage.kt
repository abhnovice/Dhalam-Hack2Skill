package com.dhalam.ap

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import java.util.Locale

/** One observable language for sign-in, dashboard, tools and chat. */
internal val appLanguage = mutableStateOf(languageForCode(Locale.getDefault().language))

internal fun tr(english: String, args: Map<String, Any?> = emptyMap(), language: ChatLanguage = appLanguage.value): String {
    val index = appLanguageOrder.indexOf(language.code)
    var text = if (index < 0) english else appStringTable[english]?.get(index) ?: english
    args.forEach { (key, value) -> text = text.replace("{$key}", value.toString()) }
    return text
}

// Match generated templates to English scanner output without changing saved values.
private val displayTemplates by lazy {
    appStringTable.keys.filter { Regex("\\{\\w+\\}").containsMatchIn(it) }.map { template ->
        val names = mutableListOf<String>()
        val pattern = StringBuilder("^")
        var end = 0
        Regex("\\{(\\w+)\\}").findAll(template).forEach { match ->
            pattern.append(Regex.escape(template.substring(end, match.range.first)))
            pattern.append("(.+?)")
            names.add(match.groupValues[1])
            end = match.range.last + 1
        }
        pattern.append(Regex.escape(template.substring(end))).append("$")
        Triple(template, Regex(pattern.toString()), names)
    }
}

/** Display-time lookup. Unknown text, identifiers and OS values retain their wording. */
internal fun trText(english: String, language: ChatLanguage = appLanguage.value): String {
    if (language.code == "en") return english
    if (appStringTable.containsKey(english)) return tr(english, language = language)
    val updateTail = " — review Software Update in Settings"
    if (english.endsWith(updateTail)) {
        val os = english.removeSuffix(updateTail)
        return "$os — ${tr("review Software Update in Settings", language = language)}"
    }
    displayTemplates.forEach { (template, pattern, names) ->
        val match = pattern.matchEntire(english)
        if (match != null) return tr(template, names.mapIndexed { index, name -> name to match.groupValues[index + 1] }.toMap(), language)
    }
    return english
}

@Composable
internal fun LanguageMenuButton() {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ms_translate), contentDescription = appLanguage.value.strings.language)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            chatLanguages.forEach { language ->
                DropdownMenuItem(text = { Text(language.name) }, onClick = {
                    appLanguage.value = language
                    open = false
                })
            }
        }
    }
}

/** Local report copy; never writes translations back to the journal. */
internal fun translateSecurityReport(report: String, language: ChatLanguage = appLanguage.value): String {
    if (language.code == "en") return report
    return report.split("\n").joinToString("\n") { line ->
        val finding = Regex("^(.+) \\[([^]]+)]: (.*)$").matchEntire(line)
        if (finding != null) {
            val (title, state, detail) = finding.destructured
            "${trText(title, language)} [${trText(state, language)}]: ${trText(detail, language)}"
        } else trText(line, language)
    }
}
