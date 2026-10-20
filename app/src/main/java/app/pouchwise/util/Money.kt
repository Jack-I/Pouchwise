package app.pouchwise.util

import app.pouchwise.model.Settings
import java.util.Currency
import java.util.Locale

object Money {
    /** The symbol set in Settings, otherwise the one of the phone's region. */
    fun symbol(s: Settings): String = s.currency.trim().ifEmpty { regionSymbol() }

    fun regionSymbol(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault()) }.getOrDefault("")
}
