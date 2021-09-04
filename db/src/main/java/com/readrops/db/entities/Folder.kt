package com.readrops.db.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.readrops.db.entities.account.Account
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"],
        childColumns = ["account_id"], onDelete = ForeignKey.CASCADE)])
data class Folder(
        @PrimaryKey(autoGenerate = true) var id: Int = 0,
        var name: String? = null,
        var remoteId: String? = null,
        @ColumnInfo(name = "account_id", index = true) var accountId: Int = 0,
) : Parcelable, Comparable<Folder> {

    override fun compareTo(other: Folder): Int = this.name!!.compareTo(other.name!!)
}