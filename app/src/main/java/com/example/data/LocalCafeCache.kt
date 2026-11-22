package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.cafeDataStore: DataStore<Preferences> by preferencesDataStore(name = "bunapos_local_cache")

/**
 * Ultra-lightweight local DataStore cache for instant cold-start/offline access
 * to PINs, menu items (1..1000), staff list, and ticket sequence counters.
 */
class LocalCafeCache(private val context: Context) {

    private fun keyCashierPin(uid: String) = stringPreferencesKey("cashier_pin_$uid")
    private fun keyTicketerPin(uid: String) = stringPreferencesKey("ticketer_pin_$uid")
    private fun keyPinsCustomized(uid: String) = booleanPreferencesKey("pins_custom_$uid")
    private fun keyAlias78To79(uid: String) = booleanPreferencesKey("alias_78_79_$uid")
    private fun keyStaffJson(uid: String) = stringPreferencesKey("staff_json_$uid")
    private fun keyMenuJson(uid: String) = stringPreferencesKey("menu_json_$uid")
    private fun keySeq(uid: String) = longPreferencesKey("seq_$uid")
    private fun keySeqDay(uid: String) = stringPreferencesKey("seq_day_$uid")
    private fun keyDayEpoch(uid: String) = longPreferencesKey("day_epoch_$uid")
    private fun keyLinkedEmail(uid: String) = stringPreferencesKey("linked_email_$uid")
    private fun keyAppPwHash(uid: String) = stringPreferencesKey("app_pw_hash_$uid")
    private fun keyAppPwSalt(uid: String) = stringPreferencesKey("app_pw_salt_$uid")
    private fun keyAppPwSet(uid: String) = booleanPreferencesKey("app_pw_set_$uid")
    private fun keyWorkspaceLocked(uid: String) = booleanPreferencesKey("workspace_locked_$uid")
    private fun keyTicketerKeepAwake() = booleanPreferencesKey("ticketer_keep_awake")
    private fun keyTicketerSound() = booleanPreferencesKey("ticketer_sound_enabled")

    fun observeTicketerKeepAwake(): Flow<Boolean> = context.cafeDataStore.data.map { prefs ->
        prefs[keyTicketerKeepAwake()] ?: true
    }

    suspend fun setTicketerKeepAwake(enabled: Boolean) {
        context.cafeDataStore.edit { prefs ->
            prefs[keyTicketerKeepAwake()] = enabled
        }
    }

    fun observeTicketerSoundEnabled(): Flow<Boolean> = context.cafeDataStore.data.map { prefs ->
        prefs[keyTicketerSound()] ?: true
    }

    suspend fun setTicketerSoundEnabled(enabled: Boolean) {
        context.cafeDataStore.edit { prefs ->
            prefs[keyTicketerSound()] = enabled
        }
    }

    fun observeWorkspaceLocked(uid: String): Flow<Boolean> = context.cafeDataStore.data.map { prefs ->
        prefs[keyWorkspaceLocked(uid)] ?: false
    }

    suspend fun setWorkspaceLocked(uid: String, locked: Boolean) {
        context.cafeDataStore.edit { prefs ->
            prefs[keyWorkspaceLocked(uid)] = locked
        }
    }

    fun observeCachedConfig(uid: String): Flow<CafeConfig?> = context.cafeDataStore.data.map { prefs ->
        val cPin = prefs[keyCashierPin(uid)] ?: return@map null
        val tPin = prefs[keyTicketerPin(uid)] ?: "2222"
        val custom = prefs[keyPinsCustomized(uid)] ?: false
        val alias = prefs[keyAlias78To79(uid)] ?: true
        val staffRaw = prefs[keyStaffJson(uid)]
        val staff = parseStaff(staffRaw)
        val seq = prefs[keySeq(uid)] ?: 1L
        val seqDay = prefs[keySeqDay(uid)] ?: ""
        val dayEpoch = prefs[keyDayEpoch(uid)] ?: 1L
        val linkedEmail = prefs[keyLinkedEmail(uid)] ?: ""
        val pwHash = prefs[keyAppPwHash(uid)] ?: ""
        val pwSalt = prefs[keyAppPwSalt(uid)] ?: ""
        val pwSet = prefs[keyAppPwSet(uid)] ?: false
        CafeConfig(
            id = "settings",
            userId = uid,
            cashierPin = cPin,
            ticketerPin = tPin,
            pinsCustomized = custom,
            alias78To79 = alias,
            staff = staff,
            nextTicketSeq = seq,
            lastTicketDay = seqDay,
            dayEpoch = dayEpoch,
            linkedEmail = linkedEmail,
            appPasswordHash = pwHash,
            appPasswordSalt = pwSalt,
            appPasswordSet = pwSet
        )
    }

    fun observeCachedMenu(uid: String): Flow<List<MenuItem>> = context.cafeDataStore.data.map { prefs ->
        parseMenu(prefs[keyMenuJson(uid)])
    }

    suspend fun saveConfig(uid: String, config: CafeConfig) {
        context.cafeDataStore.edit { prefs ->
            prefs[keyCashierPin(uid)] = config.cashierPin
            prefs[keyTicketerPin(uid)] = config.ticketerPin
            prefs[keyPinsCustomized(uid)] = config.pinsCustomized
            prefs[keyAlias78To79(uid)] = config.alias78To79
            prefs[keyStaffJson(uid)] = JSONArray(config.staff).toString()
            prefs[keySeq(uid)] = config.nextTicketSeq
            prefs[keySeqDay(uid)] = config.lastTicketDay
            prefs[keyDayEpoch(uid)] = config.dayEpoch
            prefs[keyLinkedEmail(uid)] = config.linkedEmail
            prefs[keyAppPwHash(uid)] = config.appPasswordHash
            prefs[keyAppPwSalt(uid)] = config.appPasswordSalt
            prefs[keyAppPwSet(uid)] = config.appPasswordSet
        }
    }

    suspend fun saveAppPasswordCache(
        uid: String,
        linkedEmail: String,
        hash: String,
        salt: String,
        appPasswordSet: Boolean
    ) {
        context.cafeDataStore.edit { prefs ->
            prefs[keyLinkedEmail(uid)] = linkedEmail
            prefs[keyAppPwHash(uid)] = hash
            prefs[keyAppPwSalt(uid)] = salt
            prefs[keyAppPwSet(uid)] = appPasswordSet
        }
    }

    suspend fun saveMenu(uid: String, items: List<MenuItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("c", item.code)
                put("n", item.name)
                put("p", item.price)
            })
        }
        context.cafeDataStore.edit { prefs ->
            prefs[keyMenuJson(uid)] = arr.toString()
        }
    }

    suspend fun getCachedMenuSnapshot(uid: String): List<MenuItem> {
        val prefs = context.cafeDataStore.data.first()
        return parseMenu(prefs[keyMenuJson(uid)])
    }

    private fun parseStaff(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf("ሰራተኛ 1", "ሰራተኛ 2", "ሰራተኛ 3")
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) add(s)
                }
            }
        } catch (_: Exception) {
            listOf("ሰራተኛ 1", "ሰራተኛ 2", "ሰራተኛ 3")
        }
    }

    private fun parseMenu(raw: String?): List<MenuItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val code = obj.optInt("c", 0)
                    val name = obj.optString("n", "")
                    val price = obj.optDouble("p", 0.0)
                    if (code in 1..1000 && name.isNotBlank()) {
                        add(MenuItem(id = code.toString(), code = code, name = name, price = price))
                    }
                }
            }.sortedBy { it.code }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
