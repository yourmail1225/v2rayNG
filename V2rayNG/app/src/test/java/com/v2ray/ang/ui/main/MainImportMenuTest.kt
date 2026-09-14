package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainImportMenuTest {

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isLocked = false)
        )
    }

    @Test
    fun regularMoreMenuContainsEveryNonLockActionInDisplayOrder() {
        val expected = ServerMenuAction.entries.filter { !it.isLockAction }
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isLocked = false),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false, isLocked = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = true, includeManagementActions = true, isLocked = false)
        )
    }

    @Test
    fun lockedProfileMenuShowsUnlockAndDeleteOnly() {
        val expected = listOf(
            ServerMenuAction.Unlock,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isLocked = true)
        )
    }

    @Test
    fun lockedComplexProfileMenuShowsUnlockAndDeleteOnly() {
        val expected = listOf(
            ServerMenuAction.Unlock,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = true, includeManagementActions = true, isLocked = true)
        )
    }

    @Test
    fun lockedProfileShareDialogWouldShowNoShareActions() {
        // The row UI never opens the share dialog for a locked profile with management
        // actions hidden; assert the predicate stays consistent if it ever does.
        assertEquals(
            emptyList<ServerMenuAction>(),
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isLocked = true)
        )
    }
}