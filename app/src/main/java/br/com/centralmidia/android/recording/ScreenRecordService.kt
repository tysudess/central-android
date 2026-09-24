package br.com.centralmidia.android.recording

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.NotificationHelper

class ScreenRecordService:Service(){
 private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var recorder:MediaRecorder?=null;private var pfd:ParcelFileDescriptor?=null
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  when(intent?.action){ACTION_STOP->stopRecording();ACTION_START->startRecording(intent)};return START_NOT_STICKY
 }
 private fun startRecording(intent:Intent){
  startForeground(991,NotificationCompat.Builder(this,NotificationHelper.CHANNEL_RECORDING).setSmallIcon(R.drawable.ic_stat_central).setContentTitle("Central • Gravando tela").setContentText("Toque em Parar no aplicativo para encerrar.").setOngoing(true).build())
  val code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);@Suppress("DEPRECATION") val data=intent.getParcelableExtra<Intent>("data")?:return stopSelf();val withMic=intent.getBooleanExtra("mic",true)
  val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"Central-Gravacao-${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Central Inteligente de Midia/Gravacoes")};val uri=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:return stopSelf();pfd=contentResolver.openFileDescriptor(uri,"w")
  val dm=resources.displayMetrics;val width=dm.widthPixels.coerceAtMost(1920);val height=dm.heightPixels.coerceAtMost(1920);val density=dm.densityDpi
  recorder=createRecorder()
  recorder?.apply{if(withMic)setAudioSource(MediaRecorder.AudioSource.MIC);setVideoSource(MediaRecorder.VideoSource.SURFACE);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setVideoEncoder(MediaRecorder.VideoEncoder.H264);setVideoEncodingBitRate(8_000_000);setVideoFrameRate(30);setVideoSize(width,height);if(withMic){setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setAudioEncodingBitRate(128000);setAudioSamplingRate(44100)};setOutputFile(pfd?.fileDescriptor);prepare()}
  projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(code,data);projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopRecording()}},Handler(Looper.getMainLooper()))
  display=projection?.createVirtualDisplay("CentralRecorder",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder?.surface,null,null);recorder?.start()
 }
 @Suppress("DEPRECATION")
 private fun createRecorder():MediaRecorder = if(Build.VERSION.SDK_INT>=31) MediaRecorder(this) else MediaRecorder()
 private fun stopRecording(){runCatching{recorder?.stop()};runCatching{recorder?.reset()};runCatching{recorder?.release()};recorder=null;display?.release();display=null;projection?.stop();projection=null;pfd?.close();pfd=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){super.onDestroy();runCatching{stopRecording()}}
 companion object{const val ACTION_START="central.record.START";const val ACTION_STOP="central.record.STOP"}
}
