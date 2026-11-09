package com.biyahe.companion.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountRulesTest {
    @Test fun usernamesAreTrimmedAndLowercased() = assertEquals("juan.dc", AccountService.normalize("  Juan.DC "))
    @Test fun tooShortOrOddUsernamesAreRefused() {
        assertNull(AccountService.normalize("ab"))
        assertNull(AccountService.normalize("juan dela cruz"))
        assertNull(AccountService.normalize("juan@mail"))
    }
    @Test fun eachUsernameMapsToItsOwnFirebaseEmail() = assertEquals("user@biyahe.app", AccountService.email("user"))
    @Test fun passwordsAreNeverStoredAsIs() {
        val h = AccountService.hash("user", "user123")
        assertNotEquals("user123", h)
        assertEquals(64, h.length)
        assertNotEquals(h, AccountService.hash("juan", "user123")) // same password, different user, different hash
    }
    @Test fun everyRiderGetsTheirOwnDatabaseFile() {
        assertEquals("biyahe_u_user.db", AccountDb.nameFor("user", isDemo = false))
        assertEquals("biyahe_u_juan.db", AccountDb.nameFor("juan", isDemo = false))
        assertEquals("biyahe.db", AccountDb.nameFor("admin", isDemo = false))
        assertEquals("biyahe_demo.db", AccountDb.nameFor("juan", isDemo = true))
    }
}
