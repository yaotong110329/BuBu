package com.kumo.bubu.data.local.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kumo.bubu.domain.model.ExpenseCategory
@Entity(tableName = "expense_records", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value=["publicId"], unique=true), Index(value=["vehicleId","dateEpochDay","timeMinuteOfDay","sequenceInDay"]), Index(value=["completedReminderId"])])
data class ExpenseRecordEntity(@PrimaryKey(autoGenerate=true) val id: Long=0, val publicId:String, val vehicleId:Long, val dateEpochDay:Long, val timeMinuteOfDay:Int?, val sequenceInDay:Int, val category:ExpenseCategory, val totalCostTwd:Long, val note:String?, val createdAt:Long, val updatedAt:Long, val completedReminderId:Long? = null)
