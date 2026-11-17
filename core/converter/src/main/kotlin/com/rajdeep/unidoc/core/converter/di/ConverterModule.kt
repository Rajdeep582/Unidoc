package com.rajdeep.unidoc.core.converter.di

import com.rajdeep.unidoc.core.converter.ConverterEngine
import com.rajdeep.unidoc.core.converter.cloud.CloudClient
import com.rajdeep.unidoc.core.model.CloudProbe
import com.rajdeep.unidoc.core.model.ToolRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ConverterModule {
    @Binds
    fun toolRunner(engine: ConverterEngine): ToolRunner

    @Binds
    fun cloudProbe(client: CloudClient): CloudProbe
}
