package com.kumo.bubu.data.cloud.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveBackupDataSourceTest {
    @Test
    fun createsMetadataWithAppDataFolderAsAnArrayParent() {
        val metadata = Json.parseToJsonElement(
            createDriveBackupMetadataJson(
                fileName = "bubu-backup-20260824-193000.bubu",
                appProperties = mapOf("app" to "BuBu"),
            ),
        )

        assertEquals("appDataFolder", metadata.jsonObject["parents"]!!.jsonArray[0].jsonPrimitive.content)
    }
}
