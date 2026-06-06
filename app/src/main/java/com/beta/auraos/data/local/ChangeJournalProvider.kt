package com.beta.auraos.data.local

import android.content.Context
import com.beta.auraos.data.local.room.AppDatabase

object ChangeJournalProvider {

    @Volatile
    private var instance: ChangeJournal? = null

    fun initialize(context: Context, database: AppDatabase) {
        instance = ChangeJournal(database, context)
    }

    fun get(): ChangeJournal? = instance
}
