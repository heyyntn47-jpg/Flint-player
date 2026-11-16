// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.network

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flint.player.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved SMB / FTP / SFTP servers. They live in the app's private storage (backups are off in the
 * manifest). Passwords are stored as plain text there, like most player apps do.
 */
object NetworkStore {
    private var prefs: SharedPreferences? = null

    var servers by mutableStateOf<List<NetworkServer>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("flint_network", Context.MODE_PRIVATE)
        prefs = p
        servers = runCatching { parse(p.getString("servers", null)) }
            .onFailure { Logger.warn("NetworkStore", "Could not read saved servers", it) }
            .getOrDefault(emptyList())
    }

    fun save(server: NetworkServer) {
        servers = if (servers.any { it.id == server.id }) {
            servers.map { if (it.id == server.id) server else it }
        } else {
            servers + server
        }
        write()
    }

    fun remove(id: String) {
        servers = servers.filterNot { it.id == id }
        write()
    }

    private fun write() {
        val array = JSONArray()
        servers.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("protocol", s.protocol.name)
                    .put("host", s.host)
                    .put("port", s.port)
                    .put("path", s.path)
                    .put("user", s.username)
                    .put("pass", s.password)
            )
        }
        prefs?.edit()?.putString("servers", array.toString())?.apply()
    }

    private fun parse(text: String?): List<NetworkServer> {
        if (text.isNullOrEmpty()) return emptyList()
        val array = JSONArray(text)
        val result = ArrayList<NetworkServer>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val protocol = runCatching { NetProtocol.valueOf(o.getString("protocol")) }.getOrNull() ?: continue
            result.add(
                NetworkServer(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    protocol = protocol,
                    host = o.optString("host"),
                    port = o.optInt("port", 0),
                    path = o.optString("path"),
                    username = o.optString("user"),
                    password = o.optString("pass")
                )
            )
        }
        return result
    }
}
