package com.kumo.bubu.data.csv

import com.kumo.bubu.data.local.entity.FuelRecordEntity
import com.kumo.bubu.data.local.entity.ServiceAttachmentEntity
import com.kumo.bubu.data.local.entity.VehicleEntity
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.CsvExportRequest
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CsvExportTablesTest {
    @Test
    fun exportsSelectedVehicleRecordsInLocalOrderWithoutPrivateIdentifiers() {
        val source = CsvExportSource(
            vehicles = listOf(
                vehicle(id = 1, publicId = "private-vehicle-a", name = "RAV4"),
                vehicle(id = 2, publicId = "private-vehicle-b", name = "JET"),
            ),
            fuelRecords = listOf(
                fuel(id = 9, vehicleId = 1, day = "2026-08-12", sequence = 1),
                fuel(id = 8, vehicleId = 1, day = "2026-08-12", sequence = 0),
                fuel(id = 7, vehicleId = 2, day = "2026-08-12", sequence = 0),
            ),
            serviceAttachments = listOf(
                ServiceAttachmentEntity(
                    id = 4,
                    publicId = "private-attachment",
                    serviceRecordId = 99,
                    sequenceInRecord = 0,
                    relativePath = "attachments/service/secret.jpg",
                    displayName = "invoice.jpg",
                    mimeType = "image/jpeg",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )

        val tables = CsvExportTables.build(
            source,
            CsvExportRequest(
                vehicleIds = setOf(1),
                startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                endEpochDay = LocalDate.of(2026, 8, 31).toEpochDay(),
            ),
        )

        val vehicles = tables.single { it.fileName == "vehicles.csv" }
        val fuel = tables.single { it.fileName == "fuel_records.csv" }
        val attachments = tables.single { it.fileName == "attachments.csv" }

        assertEquals(listOf(listOf("VEH-001", "RAV4") + vehicles.rows.single().drop(2)), vehicles.rows)
        assertEquals(listOf("FUEL-000001", "VEH-001", "2026-08-12", "", "0"), fuel.rows[0].take(5))
        assertEquals(listOf("FUEL-000002", "VEH-001", "2026-08-12", "", "1"), fuel.rows[1].take(5))
        assertEquals(emptyList<List<String>>(), attachments.rows)
        assertFalse(tables.flatMap(CsvTable::rows).flatten().any { "private-" in it || "attachments/service" in it })
    }

    private fun vehicle(id: Long, publicId: String, name: String) = VehicleEntity(
        id = id,
        publicId = publicId,
        name = name,
        vehicleType = VehicleType.CAR,
        motorcycleClass = null,
        brand = null,
        model = null,
        manufactureYear = null,
        engineDisplacementCc = null,
        licensePlate = null,
        powertrainType = null,
        trackingStartDateEpochDay = 0,
        trackingStartOdometerKm = 0,
        currentOdometerKm = 0,
        note = null,
        isArchived = false,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun fuel(id: Long, vehicleId: Long, day: String, sequence: Int) = FuelRecordEntity(
        id = id,
        publicId = "private-fuel-$id",
        vehicleId = vehicleId,
        dateEpochDay = LocalDate.parse(day).toEpochDay(),
        timeMinuteOfDay = null,
        sequenceInDay = sequence,
        odometerKm = 1_000L + sequence,
        fuelVolumeMl = 42_500,
        pricePerLiterMilli = 31_200,
        totalCostTwd = 1_326,
        isFullTank = true,
        fuelProduct = FuelProduct.GASOLINE_95,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
