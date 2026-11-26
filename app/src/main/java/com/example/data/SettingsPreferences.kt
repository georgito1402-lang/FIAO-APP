package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.businessDataStore: DataStore<Preferences> by preferencesDataStore(name = "fiao_prefs")

private val STORE_CODE_REGEX = Regex("^[A-Z0-9_\\-]{4,32}$")

fun normalizeStoreCode(raw: String): String =
    raw.trim()
        .replace(" ", "")
        .uppercase(Locale.US)

fun isValidStoreCode(code: String): Boolean = STORE_CODE_REGEX.matches(code)

data class BusinessSettings(
    val storeCode: String = "",
    val exchangeRate: Double = 45.0,
    val rateLabel: String = "Tasa BCV",
    val rateUpdatedAtMillis: Long = 0L,
    val businessName: String = DEFAULT_BUSINESS_NAME,
    val isDarkMode: Boolean = false,
    val enableSalesModule: Boolean = false,
    val enableExpensesModule: Boolean = false
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val STORE_CODE = stringPreferencesKey("store_code")
        val ENABLE_SALES_MODULE = booleanPreferencesKey("enable_sales_module")
        val ENABLE_EXPENSES_MODULE = booleanPreferencesKey("enable_expenses_module")
    }

    val isDarkModeFlow: Flow<Boolean> = context.businessDataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_MODE] ?: false
    }

    val storeCodeFlow: Flow<String> = context.businessDataStore.data.map { prefs ->
        prefs[Keys.STORE_CODE].orEmpty()
    }

    val enableSalesModuleFlow: Flow<Boolean> = context.businessDataStore.data.map { prefs ->
        prefs[Keys.ENABLE_SALES_MODULE] ?: false
    }

    val enableExpensesModuleFlow: Flow<Boolean> = context.businessDataStore.data.map { prefs ->
        prefs[Keys.ENABLE_EXPENSES_MODULE] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.businessDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_MODE] = enabled
        }
    }

    suspend fun setEnableSalesModule(enabled: Boolean) {
        context.businessDataStore.edit { prefs ->
            prefs[Keys.ENABLE_SALES_MODULE] = enabled
        }
    }

    suspend fun setEnableExpensesModule(enabled: Boolean) {
        context.businessDataStore.edit { prefs ->
            prefs[Keys.ENABLE_EXPENSES_MODULE] = enabled
        }
    }

    suspend fun setStoreCode(rawCode: String) {
        val normalized = normalizeStoreCode(rawCode)
        require(isValidStoreCode(normalized)) {
            "El Código de Tienda debe tener entre 4 y 32 caracteres (letras, números, - o _)."
        }
        context.businessDataStore.edit { prefs ->
            prefs[Keys.STORE_CODE] = normalized
        }
    }

    suspend fun clearStoreCode() {
        context.businessDataStore.edit { prefs ->
            prefs.remove(Keys.STORE_CODE)
        }
    }
}
