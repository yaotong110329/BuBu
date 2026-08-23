package com.kumo.bubu.feature.vehicle

import androidx.annotation.StringRes
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.MotorcycleClass
import com.kumo.bubu.domain.model.PowertrainType
import com.kumo.bubu.domain.model.VehicleType

@StringRes
internal fun VehicleType.labelRes(): Int = when (this) {
    VehicleType.CAR -> R.string.vehicle_type_car
    VehicleType.MOTORCYCLE -> R.string.vehicle_type_motorcycle
}

@StringRes
internal fun MotorcycleClass.labelRes(): Int = when (this) {
    MotorcycleClass.LIGHT -> R.string.motorcycle_class_light
    MotorcycleClass.ORDINARY_HEAVY -> R.string.motorcycle_class_ordinary_heavy
    MotorcycleClass.LARGE_HEAVY -> R.string.motorcycle_class_large_heavy
}

@StringRes
internal fun PowertrainType.labelRes(): Int = when (this) {
    PowertrainType.GASOLINE -> R.string.powertrain_gasoline
    PowertrainType.DIESEL -> R.string.powertrain_diesel
    PowertrainType.HYBRID -> R.string.powertrain_hybrid
    PowertrainType.OTHER_LIQUID_FUEL -> R.string.powertrain_other_liquid
}
