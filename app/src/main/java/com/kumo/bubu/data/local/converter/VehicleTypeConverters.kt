package com.kumo.bubu.data.local.converter

import androidx.room.TypeConverter
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PaymentMethod
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.FuelProduct
import com.kumo.bubu.domain.model.FuelingMode
import com.kumo.bubu.domain.model.FuelEconomyStatisticsStatus
import com.kumo.bubu.domain.model.VehicleType
import com.kumo.bubu.domain.model.ServiceQuantityUnit
import com.kumo.bubu.domain.model.ServiceRecordType
import com.kumo.bubu.domain.model.ExpenseCategory
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.ReminderStatus
import java.time.MonthDay

class VehicleTypeConverters {
    @TypeConverter
    fun monthDayToString(value: MonthDay?): String? = value?.toString()

    @TypeConverter
    fun stringToMonthDay(value: String?): MonthDay? = value?.let(MonthDay::parse)

    @TypeConverter
    fun vehicleTypeToString(value: VehicleType): String = value.name

    @TypeConverter
    fun stringToVehicleType(value: String): VehicleType = VehicleType.valueOf(value)

    @TypeConverter
    fun motorcycleClassToString(value: MotorcycleClass?): String? = value?.name

    @TypeConverter
    fun stringToMotorcycleClass(value: String?): MotorcycleClass? = value?.let(MotorcycleClass::valueOf)

    @TypeConverter
    fun powertrainTypeToString(value: PowertrainType?): String? = value?.name

    @TypeConverter
    fun stringToPowertrainType(value: String?): PowertrainType? = value?.let(PowertrainType::valueOf)

    @TypeConverter
    fun fuelProductToString(value: FuelProduct?): String? = value?.name

    @TypeConverter
    fun stringToFuelProduct(value: String?): FuelProduct? = value?.let(FuelProduct::valueOf)

    @TypeConverter
    fun fuelingModeToString(value: FuelingMode): String = value.name

    @TypeConverter
    fun stringToFuelingMode(value: String): FuelingMode = FuelingMode.valueOf(value)

    @TypeConverter
    fun fuelEconomyStatisticsStatusToString(value: FuelEconomyStatisticsStatus): String = value.name

    @TypeConverter
    fun stringToFuelEconomyStatisticsStatus(value: String): FuelEconomyStatisticsStatus =
        runCatching { FuelEconomyStatisticsStatus.valueOf(value) }.getOrDefault(FuelEconomyStatisticsStatus.UNREVIEWED)

    @TypeConverter
    fun serviceRecordTypeToString(value: ServiceRecordType): String = value.name

    @TypeConverter
    fun stringToServiceRecordType(value: String): ServiceRecordType = ServiceRecordType.valueOf(value)

    @TypeConverter
    fun paymentMethodToString(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun stringToPaymentMethod(value: String?): PaymentMethod? = value?.let(PaymentMethod::valueOf)

    @TypeConverter
    fun serviceQuantityUnitToString(value: ServiceQuantityUnit?): String? = value?.name

    @TypeConverter
    fun stringToServiceQuantityUnit(value: String?): ServiceQuantityUnit? = value?.let(ServiceQuantityUnit::valueOf)

    @TypeConverter
    fun expenseCategoryToString(value: ExpenseCategory): String = value.name

    @TypeConverter
    fun stringToExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter
    fun reminderSourceToString(value: ReminderSource): String = value.name

    @TypeConverter
    fun stringToReminderSource(value: String): ReminderSource = ReminderSource.valueOf(value)

    @TypeConverter
    fun reminderStatusToString(value: ReminderStatus?): String? = value?.name

    @TypeConverter
    fun stringToReminderStatus(value: String?): ReminderStatus? = value?.let(ReminderStatus::valueOf)
}
