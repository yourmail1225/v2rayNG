package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun regularMoreMenuContainsShareEditLockAndDeleteInOrder() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Lock,
            ServerMenuAction.Delete,
        )
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
            ServerMenuAction.Lock,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = true, includeManagementActions = true, isLocked = false)
        )
    }

    @Test
    fun lockedProfileMenuShowsOnlyUnlockAndDelete() {
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
    fun lockedComplexProfileMenuShowsOnlyUnlockAndDelete() {
        assertEquals(
            listOf(
                ServerMenuAction.Unlock,
                ServerMenuAction.Delete,
            ),
            serverMenuActions(isComplexProfile = true, includeManagementActions = true, isLocked = true)
        )
    }

    @Test
    fun lockedProfileShareDialogExposesNoLockedShareActions() {
        // A permanently locked profile exposes no lock-link/lock-file/lock-settings
        // actions at all; a share-only dialog therefore has nothing to offer.
        val actions = serverMenuActions(isComplexProfile = false, includeManagementActions = false, isLocked = true)
        assertTrue(actions.none { it.isShareAction })
        assertTrue(actions.isEmpty())
    }
}