package org.phenoapps.cotton.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.phenoapps.cotton.database.dao.SampleDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Singleton
    @Provides
    fun provideSampleDao(db: CottonDatabase): SampleDao {
        return db.sampleDao()
    }

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext appContext: Context): CottonDatabase {
        return CottonDatabase.getInstance(appContext)
    }
}