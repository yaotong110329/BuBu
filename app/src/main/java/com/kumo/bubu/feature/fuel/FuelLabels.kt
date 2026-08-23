package com.kumo.bubu.feature.fuel

import androidx.annotation.StringRes
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.FuelProduct

@StringRes
fun FuelProduct.labelRes(): Int = when (this) {
    FuelProduct.GASOLINE_92 -> R.string.fuel_product_92
    FuelProduct.GASOLINE_95 -> R.string.fuel_product_95
    FuelProduct.GASOLINE_98 -> R.string.fuel_product_98
    FuelProduct.DIESEL -> R.string.fuel_product_diesel
    FuelProduct.OTHER -> R.string.fuel_product_other
}

@StringRes
fun FuelFormError.messageRes(): Int = when (this) {
    FuelFormError.REQUIRED -> R.string.required_field_error
    FuelFormError.INVALID_DATE -> R.string.invalid_date_error
    FuelFormError.FUTURE_DATE -> R.string.fuel_future_date_error
    FuelFormError.INVALID_TIME -> R.string.invalid_time_error
    FuelFormError.NON_NEGATIVE_INTEGER_REQUIRED -> R.string.non_negative_integer_error
    FuelFormError.POSITIVE_VOLUME_REQUIRED -> R.string.fuel_volume_error
    FuelFormError.INVALID_TOTAL_COST -> R.string.fuel_total_cost_error
    FuelFormError.INVALID_PRICE -> R.string.fuel_price_error
}
