package com.mkulikov.taskb.feature.settings.api.database

const val REMEMBER_LAST_GROUP = "rememberLastGroup"
const val LAST_SELECTED_GROUP_ID = "lastSelectedGroupId"

suspend fun IAppSettingsDao.rememberLastGroupEnabled(): Boolean = getValue(key = REMEMBER_LAST_GROUP) != "false"

/** Called in the same write transaction that inserts the new item. */
suspend fun IAppSettingsDao.rememberCreatedItemGroup(groupId: Int?) {
    if (!rememberLastGroupEnabled()) return
    if (groupId == null) {
        deleteValue(key = LAST_SELECTED_GROUP_ID)
    } else {
        upsert(setting = ApplicationSetting(key = LAST_SELECTED_GROUP_ID, value = groupId.toString()))
    }
}
