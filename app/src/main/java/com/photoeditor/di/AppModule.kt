package com.photoeditor.di

import android.content.Context
import com.photoeditor.ai.AIEnhancer
import com.photoeditor.ai.FilterEngine
import com.photoeditor.ai.ImageProcessor
import com.photoeditor.ai.WatermarkGenerator
import com.photoeditor.data.AppDatabase
import com.photoeditor.data.model.EditHistoryDao
import com.photoeditor.data.repository.FilterRepository
import com.photoeditor.data.repository.PhotoRepository
import com.photoeditor.utils.BatchProcessor
import com.photoeditor.utils.FileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideEditHistoryDao(database: AppDatabase): EditHistoryDao {
        return database.editHistoryDao()
    }

    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): FileManager {
        return FileManager(context)
    }

    @Provides
    @Singleton
    fun provideFilterEngine(@ApplicationContext context: Context): FilterEngine {
        return FilterEngine(context)
    }

    @Provides
    @Singleton
    fun provideAIEnhancer(@ApplicationContext context: Context): AIEnhancer {
        return AIEnhancer(context)
    }

    @Provides
    @Singleton
    fun provideWatermarkGenerator(@ApplicationContext context: Context): WatermarkGenerator {
        return WatermarkGenerator(context)
    }

    @Provides
    @Singleton
    fun provideImageProcessor(@ApplicationContext context: Context): ImageProcessor {
        return ImageProcessor(context)
    }

    @Provides
    @Singleton
    fun provideBatchProcessor(@ApplicationContext context: Context): BatchProcessor {
        return BatchProcessor(context)
    }

    @Provides
    @Singleton
    fun providePhotoRepository(
        @ApplicationContext context: Context,
        editHistoryDao: EditHistoryDao,
        fileManager: FileManager
    ): PhotoRepository {
        return PhotoRepository(context, editHistoryDao, fileManager)
    }

    @Provides
    @Singleton
    fun provideFilterRepository(@ApplicationContext context: Context): FilterRepository {
        return FilterRepository(context)
    }
}
