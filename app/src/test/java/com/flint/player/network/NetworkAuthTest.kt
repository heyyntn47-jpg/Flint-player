// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAuthTest {
    private fun server(
        protocol: NetProtocol = NetProtocol.SMB,
        host: String = "nas.local",
        port: Int = 0,
        path: String = "",
        user: String = "",
        pass: String = ""
    ) = NetworkServer("id", "NAS", protocol, host, port, path, user, pass)

    @Test fun baseUrlUsesDefaultPortSilently() {
        assertEquals("smb://nas.local/", NetworkAuth.baseUrl(server()))
        assertEquals("ftp://nas.local/", NetworkAuth.baseUrl(server(NetProtocol.FTP, port = 21)))
    }

    @Test fun baseUrlKeepsCustomPortAndEncodesPath() {
        assertEquals(
            "sftp://nas.local:2222/My%20Videos/Action/",
            NetworkAuth.baseUrl(server(NetProtocol.SFTP, port = 2222, path = "/My Videos/Action/"))
        )
    }

    @Test fun stripRemovesLogin() {
        assertEquals("smb://nas.local/Movies/a.mkv", NetworkAuth.strip("smb://bob:secret@nas.local/Movies/a.mkv"))
        assertEquals("ftp://h:2121/x", NetworkAuth.strip("ftp://u@h:2121/x"))
        assertEquals("smb://nas.local/a", NetworkAuth.strip("smb://nas.local/a"))
    }

    @Test fun injectEncodesSpecialCharacters() {
        val s = server(user = "bob", pass = "p@ss word")
        assertEquals("smb://bob:p%40ss%20word@nas.local/a.mkv", NetworkAuth.inject("smb://nas.local/a.mkv", s))
    }

    @Test fun injectDoesNothingWithoutUser() {
        assertEquals("smb://nas.local/a.mkv", NetworkAuth.inject("smb://nas.local/a.mkv", server()))
    }

    @Test fun findServerMatchesHostProtocolAndPort() {
        val smb = server()
        val ftp = server(NetProtocol.FTP, host = "nas.local")
        val list = listOf(smb, ftp)
        assertEquals(smb, NetworkAuth.findServer("smb://NAS.local/a", list))
        assertEquals(ftp, NetworkAuth.findServer("ftp://nas.local/a", list))
        assertNull(NetworkAuth.findServer("smb://other/a", list))
        assertNull(NetworkAuth.findServer("smb://nas.local:1445/a", list))
    }

    @Test fun playableAddsSavedLoginOnly() {
        val list = listOf(server(user = "bob", pass = "x"))
        assertEquals("smb://bob:x@nas.local/a", NetworkAuth.playable("smb://nas.local/a", list))
        assertEquals("smb://other/a", NetworkAuth.playable("smb://other/a", list))
        assertEquals("https://example.com/a.mp4", NetworkAuth.playable("https://example.com/a.mp4", list))
    }

    @Test fun smbOptionsSplitDomain() {
        val list = listOf(server(user = "HOME\\bob", pass = "x"))
        assertEquals(
            listOf(":smb-domain=HOME", ":smb-user=bob", ":smb-pwd=x"),
            NetworkAuth.options("smb://nas.local/a", list)
        )
    }

    @Test fun noOptionsForGuest() {
        assertEquals(emptyList<String>(), NetworkAuth.options("smb://nas.local/a", listOf(server())))
    }
}
