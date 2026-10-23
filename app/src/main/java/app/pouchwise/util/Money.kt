package app.pouchwise.util

import android.content.Context
import android.telephony.TelephonyManager
import app.pouchwise.model.Settings
import java.util.Currency
import java.util.Locale

object Money {
    @Volatile private var phone: Currency? = null

    /** The symbol set in Settings, otherwise the one of the country the phone is in. */
    fun symbol(s: Settings, context: Context): String = s.currency.trim().ifEmpty { phoneSymbol(context) }

    /** The network's currency is looked up once per run; its symbol follows the language in use. */
    fun phoneSymbol(context: Context): String {
        val currency = phone ?: localCurrency(context)?.also { phone = it } ?: regionCurrency()
        return currency?.getSymbol(Locale.getDefault()).orEmpty()
    }

    private fun regionCurrency(): Currency? = runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()

    /**
     * The currency of the country the phone's mobile network (or else its SIM) is in: the language region can be
     * anywhere, an English (UK) phone in Cyprus pays in euros. Null without either.
     */
    private fun localCurrency(context: Context): Currency? = runCatching {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val country = tm.networkCountryIso.orEmpty().ifEmpty { tm.simCountryIso.orEmpty() }.uppercase(Locale.ROOT)
        if (country.length != 2) return null
        Currency.getInstance(Locale.Builder().setRegion(country).build())
    }.getOrNull()
}
