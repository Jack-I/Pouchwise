package app.pouchwise.util

import android.content.Context
import android.telephony.TelephonyManager
import app.pouchwise.model.Settings
import java.util.Currency
import java.util.Locale

object Money {
    /** The symbol set in Settings, otherwise the one of the phone's region. */
    fun symbol(s: Settings): String = s.currency.trim().ifEmpty { regionSymbol() }

    fun regionSymbol(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault()) }.getOrDefault("")

    /**
     * The currency of the country the phone's mobile network (or else its SIM) is in: the language region can be
     * anywhere, an English (UK) phone in Cyprus pays in euros. Null without either.
     */
    fun localSymbol(context: Context): String? = runCatching {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val country = tm.networkCountryIso.orEmpty().ifEmpty { tm.simCountryIso.orEmpty() }.uppercase(Locale.ROOT)
        if (country.length != 2) return null
        Currency.getInstance(Locale.Builder().setRegion(country).build()).getSymbol(Locale.getDefault())
    }.getOrNull()
}
