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
    fun lockedProfileMenuShowsLockedShareUnlockSettingsAndDeleteInOrder() {
        val expected = listOf(
            ServerMenuAction.ShareLockedQRCode,
            ServerMenuAction.ShareLockedClipboard,
            ServerMenuAction.ShareLockedFile,
            ServerMenuAction.Unlock,
            ServerMenuAction.LockSettings,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isLocked = true)
        )
    }

    @Test
    fun lockedComplexProfileMenuShowsLockedShareUnlockSettingsAndDeleteInOrder() {
        val expected = listOf(
            ServerMenuAction.ShareLockedQRCode,
            ServerMenuAction.ShareLockedClipboard,
            ServerMenuAction.ShareLockedFile,
            ServerMenuAction.Unlock,
            ServerMenuAction.LockSettings,
            ServerMenuAction.Delete,
        )
        assertEquals(
            expected,
            serverMenuActions(isComplexProfile = true, includeManagementActions = true, isLocked = true)
        )
    }

    @Test
    fun lockedProfileShareDialogShowsOnlyLockedShareActions() {
        assertEquals(
            listOf(
                ServerMenuAction.ShareLockedQRCode,
                ServerMenuAction.ShareLockedClipboard,
                ServerMenuAction.ShareLockedFile,
            ),
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isLocked = true)
        )
    }
}