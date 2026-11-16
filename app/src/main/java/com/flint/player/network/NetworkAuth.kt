// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.network

import java.net.URLEncoder

enum class NetProtocol(val scheme: String, val label: String, val defaultPort: Int) {
    SMB("smb", "SMB", 445),
    FTP("ftp", "FTP", 21),
    SFTP("sftp", "SFTP", 22),
    NFS("nfs", "NFS", 2049)
}

/** A saved network server. [port] 0 means the protocol's default port. */
data class NetworkServer(
    val id: String,
    val name: String,
    val protocol: NetProtocol,
    val host: String,
    val port: Int,
    val path: String,
    val username: String,
    val password: String
) {
    val effectivePort: Int get() = if (port > 0) port else protocol.defaultPort
}

/**
 * Pure string helpers for network urls and logins. Kept free of Android classes so they can be
 * unit tested on the JVM.
 *
 * Saved urls (history, queue) never contain a password. The login is looked up by host when a
 * video is opened and added only at the moment libVLC is given the url.
 */
object NetworkAuth {
    private val URL = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://(?:([^/@]*)@)?([^/:?#]+)(?::(\\d+))?(.*)$")

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** The first url to browse for a server, without any login in it. */
    fun baseUrl(server: NetworkServer): String {
        val sb = StringBuilder(server.protocol.scheme).append("://").append(server.host.trim())
        if (server.effectivePort != server.protocol.defaultPort) sb.append(':').append(server.effectivePort)
        sb.append('/')
        val path = server.path.trim().trim('/')
        if (path.isNotEmpty()) {
            sb.append(path.split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) })
            sb.append('/')
        }
        return sb.toString()
    }

    /** Removes any `user:password@` part. */
    fun strip(url: String): String {
        val m = URL.matchEntire(url) ?: return url
        val scheme = m.groupValues[1]
        val host = m.groupValues[3]
        val port = m.groupValues[4]
        val rest = m.groupValues[5]
        return "$scheme://$host${if (port.isNotEmpty()) ":$port" else ""}$rest"
    }

    /** Adds the server's login to [url]. Returns [url] unchanged when the server has no user name. */
    fun inject(url: String, server: NetworkServer): String {
        if (server.username.isEmpty()) return url
        val clean = strip(url)
        val marker = clean.indexOf("://")
        if (marker < 0) return clean
        val login = if (server.password.isEmpty()) enc(server.username)
        else enc(server.username) + ":" + enc(server.password)
        return clean.substring(0, marker + 3) + login + "@" + clean.substring(marker + 3)
    }

    /** The saved server that [url] belongs to (same protocol, host and port), if any. */
    fun findServer(url: String, servers: List<NetworkServer>): NetworkServer? {
        val m = URL.matchEntire(url) ?: return null
        val scheme = m.groupValues[1].lowercase()
        val host = m.groupValues[3].lowercase()
        val port = m.groupValues[4].toIntOrNull()
        return servers.firstOrNull { s ->
            s.protocol.scheme == scheme &&
                s.host.trim().lowercase() == host &&
                (port ?: s.protocol.defaultPort) == s.effectivePort
        }
    }

    /** The url to hand to libVLC: [url] plus the saved login for its server, if there is one. */
    fun playable(url: String, servers: List<NetworkServer>): String {
        val server = findServer(url, servers) ?: return url
        return inject(url, server)
    }

    /** libVLC media options carrying the login, as a second way to pass it. */
    fun options(url: String, servers: List<NetworkServer>): List<String> {
        val server = findServer(url, servers) ?: return emptyList()
        if (server.username.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        var user = server.username
        when (server.protocol) {
            NetProtocol.SMB -> {
                val slash = user.indexOf('\\')
                if (slash > 0) {
                    result.add(":smb-domain=" + user.substring(0, slash))
                    user = user.substring(slash + 1)
                }
                result.add(":smb-user=$user")
                if (server.password.isNotEmpty()) result.add(":smb-pwd=${server.password}")
            }
            NetProtocol.FTP -> {
                result.add(":ftp-user=$user")
                if (server.password.isNotEmpty()) result.add(":ftp-pwd=${server.password}")
            }
            NetProtocol.SFTP -> {
                result.add(":sftp-user=$user")
                if (server.password.isNotEmpty()) result.add(":sftp-pwd=${server.password}")
            }
            // NFS has no login of its own; access is decided by the server's export rules
            NetProtocol.NFS -> {}
        }
        return result
    }
}
