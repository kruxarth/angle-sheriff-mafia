package com.angelsheriffmafia.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.angelsheriffmafia.engine.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("setup")

/** Player names in seating order and house rules, kept between games. */
class SetupStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val playersKey = stringPreferencesKey("players")
    private val rulesKey = stringPreferencesKey("rules")
    private val narratorKey = booleanPreferencesKey("narrator_on")

    val setup: Flow<Setup> = context.dataStore.data.map { prefs ->
        Setup(
            players = prefs[playersKey]?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlayer.serializer()), it) }.getOrNull() }.orEmpty(),
            rules = prefs[rulesKey]?.let { runCatching { json.decodeFromString(Rules.serializer(), it) }.getOrNull() } ?: Rules(),
            narratorOn = prefs[narratorKey] ?: true,
        )
    }

    suspend fun save(setup: Setup) {
        context.dataStore.edit {
            it[playersKey] = json.encodeToString(ListSerializer(SavedPlayer.serializer()), setup.players)
            it[rulesKey] = json.encodeToString(Rules.serializer(), setup.rules)
            it[narratorKey] = setup.narratorOn
        }
    }
}
