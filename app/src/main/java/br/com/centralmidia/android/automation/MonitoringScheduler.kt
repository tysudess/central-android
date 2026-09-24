package br.com.centralmidia.android.automation

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object MonitoringScheduler{
 fun schedule(context:Context,minutes:Long){val request=PeriodicWorkRequestBuilder<MonitoringWorker>(minutes.coerceAtLeast(15),TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("central-monitoring").build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("central-monitoring",ExistingPeriodicWorkPolicy.UPDATE,request)}
 fun cancel(context:Context)=WorkManager.getInstance(context).cancelUniqueWork("central-monitoring")
}
