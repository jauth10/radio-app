package com.iu.radioapp.di

import com.iu.radioapp.interactor.DeliveryScheduler
import com.iu.radioapp.work.WorkManagerDeliveryScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkModule {

    @Binds
    abstract fun bindDeliveryScheduler(scheduler: WorkManagerDeliveryScheduler): DeliveryScheduler
}
