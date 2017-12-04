package com.app99.cadeotelefone.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings.Secure.ANDROID_ID
import android.provider.Settings.Secure.getString
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.app99.cadeotelefone.R
import com.app99.cadeotelefone.contract.ServiceCallbacks
import com.app99.cadeotelefone.extensions.getCompatColor
import com.app99.cadeotelefone.extensions.gone
import com.app99.cadeotelefone.extensions.visible
import com.app99.cadeotelefone.model.Phone
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_checkin.check_out_button
import kotlinx.android.synthetic.main.activity_checkin.check_text
import kotlinx.android.synthetic.main.activity_checkin.fifteen_min_button
import kotlinx.android.synthetic.main.activity_checkin.five_min_button
import kotlinx.android.synthetic.main.activity_checkin.phone_status
import kotlinx.android.synthetic.main.activity_checkin.progressBar
import kotlinx.android.synthetic.main.activity_checkin.ten_min_button
import kotlinx.android.synthetic.main.activity_checkin.timeleft_info
import kotlinx.android.synthetic.main.activity_checkin.view
import rx.Completable
import rx.Observable
import rx.Subscription
import rx.subscriptions.Subscriptions
import java.util.Calendar
import java.util.concurrent.TimeUnit


class CheckinActivity : AppCompatActivity(), ServiceCallbacks {


  private val FIVE_MINUTES = 5
  private val TEN_MINUTES = 10
  private val FIFTEEN_MINUTES = 15
  private val CHECKOUT = 99
  private val CHECK_IN_LIMIT_KEY = "check_in_limit"
  private val DEVICE_ID_KEY = "deviceId"
  private val LAST_UPDATE_KEY = "last_update"

  private val PHONE_REFERENCE = "phones"
  private val FIND_PHONE_REFERENCE = "findPhone"
  private val PROD = "prodUAU"
  private val DEV = "testDEV"

  private var findPhoneSubscription = Subscriptions.empty()
  private lateinit var playerSubscription: Subscription

  private var player: MediaPlayer? = null
  private val audioManager: AudioManager  by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  private var timer = 0L

  private lateinit var phone: Phone
  private var finderCalledTime = 0L


  //region PROD
//  private val finderRef = FirebaseDatabase.getInstance().getReference(PROD).child(FIND_PHONE_REFERENCE)
//  private val phonesRef = FirebaseDatabase.getInstance().getReference(PROD).child(PHONE_REFERENCE)
  //endregion

  //region PROD
  private val finderRef = FirebaseDatabase.getInstance().getReference(DEV).child(FIND_PHONE_REFERENCE)
  private val phonesRef = FirebaseDatabase.getInstance().getReference(DEV).child(PHONE_REFERENCE)
  //endregion


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_checkin)

    showProgressBar()
    initService()
    initListeners()
  }

  override fun phoneListenerCallback() = FirebasePhoneListener()

  override fun finderListenerCallback() = FirebaseFinderListener()

  private fun initService() {
    val intent = Intent(this, FindPhoneService::class.java)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }

  private fun initListeners() {
    clickListeners()
    firebaseListeners()
  }

  private fun clickListeners() {
    five_min_button.setOnClickListener { execute(FIVE_MINUTES) }
    ten_min_button.setOnClickListener { execute(TEN_MINUTES) }
    fifteen_min_button.setOnClickListener { execute(FIFTEEN_MINUTES) }
    check_out_button.setOnClickListener { execute(CHECKOUT) }
  }

  private fun firebaseListeners() {
    phonesRef.addValueEventListener(FirebasePhoneListener())
    finderRef.addValueEventListener(FirebaseFinderListener())
  }

  private fun setViewState(phoneList: ArrayList<Phone>) {
    val timerNow = phoneDateInSeconds()

    for (phone in phoneList) {
      if (phone.deviceId == getUniqueID()) {
        this.phone = phone
        if (timerNow < this.phone.check_in_limit) {
          checkInActiveStateView()
          try {
            playerSubscription.unsubscribe()
          } catch (e: UninitializedPropertyAccessException) {
          }
          return
        } else {
          if (timerNow < finderCalledTime) {
            val playerTimer = (finderCalledTime - timerNow)
            playSound(if (playerTimer > 20L) 30L else playerTimer)
          } else {
            disableAlarm()
          }

          checkInDeactiveStateView()
          return
        }
      }
    }
    checkInDeactiveStateView()
  }

  private fun disableAlarm() {
    if (player != null) {
      player?.stop()
      player?.release()
      player = null
      playerSubscription.unsubscribe()
    }
  }

  private fun playSound(playerTimer: Long) {
    timer = playerTimer
    player = MediaPlayer.create(this, R.raw.alertmp3)
    player?.setVolume(20f, 20f)
    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 20, 0);

    playerSubscription = Observable.timer(1, TimeUnit.SECONDS)
        .flatMapCompletable {
          Completable.create { s ->
            player?.start()

            player?.setOnCompletionListener {
              s.onCompleted()
            }
          }
        }
        .repeat(30)
        .subscribe()

  }

  private fun execute(action: Int) {
    when (action) {
      FIVE_MINUTES -> executeCheckin(FIVE_MINUTES)
      TEN_MINUTES -> executeCheckin(TEN_MINUTES)
      FIFTEEN_MINUTES -> executeCheckin(FIFTEEN_MINUTES)
      CHECKOUT -> executeCheckout()
    }
  }

  private fun checkInDeactiveStateView() {
    five_min_button.isEnabled = true
    fifteen_min_button.isEnabled = true
    ten_min_button.isEnabled = true
    check_out_button.isEnabled = false

    phone_status.setTextColor(getCompatColor(android.R.color.holo_red_dark))
    phone_status.text = getString(R.string.inactive_check_in)

    check_text.text = getString(R.string.do_check_in)
  }

  private fun checkInActiveStateView() {
    five_min_button.isEnabled = false
    fifteen_min_button.isEnabled = false
    ten_min_button.isEnabled = false
    check_out_button.isEnabled = true

    phone_status.setTextColor(getCompatColor(R.color.green))
    phone_status.text = getString(R.string.active_check_in)

    timeleft_info.text = ""

    check_text.text = getString(R.string.do_check_out)
  }

  private fun showProgressBar() {
    view.visible()
    progressBar.visible()
  }

  private fun hideProgressBar() {
    view.gone()
    progressBar.gone()
  }

  private fun executeCheckout() {
    showProgressBar()
    phonesRef.child(getUniqueID()).child("check_in_limit").setValue(CHECKOUT)
  }

  private fun executeCheckin(minutes: Int) {
    showProgressBar()
    phone = Phone(getUniqueID(), getDateInSecondsAddingMinutes(minutes), phoneDateInSeconds())

    phonesRef.child(phone.deviceId).setValue(phone)
  }

  private fun FirebasePhoneListener(): ValueEventListener {
    return object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {

        FirebasePhoneUpdate(dataSnapshot)

        hideProgressBar()
      }

      override fun onCancelled(databaseError: DatabaseError) {
        checkInDeactiveStateView()
      }
    }
  }

  private fun FirebasePhoneUpdate(dataSnapshot: DataSnapshot) {
    val phoneList = ArrayList<Phone>()

    for (messageSnapshot in dataSnapshot.children) {
      val deviceId = messageSnapshot.child(DEVICE_ID_KEY).value as String
      val limit = messageSnapshot.child(CHECK_IN_LIMIT_KEY).value as Long
      val update = messageSnapshot.child(LAST_UPDATE_KEY).value as Long

      phoneList.add(Phone(deviceId, limit, update))
    }

    if (phoneList.size > 0) {
      setViewState(phoneList)
    } else {
      checkInDeactiveStateView()
    }
  }

  private fun FirebaseFinderListener(): ValueEventListener {
    return object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {

        FirebaseFinderPhone(dataSnapshot)
      }

      override fun onCancelled(databaseError: DatabaseError) {
      }
    }
  }

  private fun FirebaseFinderPhone(dataSnapshot: DataSnapshot) {
    var time = 0L

    dataSnapshot.value?.let { time = it as Long }

    if (time > 0L) {

      if (timer > 0 && time == 99L)
        timer = 0

      finderCalledTime = time
      try {
        phone.last_update = phoneDateInSeconds()
        phonesRef.child(phone.deviceId).setValue(phone)
      } catch (e: UninitializedPropertyAccessException) {
        phone = Phone(getUniqueID(), CHECKOUT.toLong(), phoneDateInSeconds())
        phonesRef.child(phone.deviceId).setValue(phone)
      }
    }
  }

  @SuppressLint("HardwareIds")
  private fun getUniqueID() = getString(contentResolver, ANDROID_ID)

  private fun getPhoneDate() = Calendar.getInstance().time

  private fun getDateInSecondsAddingMinutes(minutes: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.time = getPhoneDate()
    calendar.add(Calendar.MINUTE, minutes)

    return ((calendar.timeInMillis) / 1000)
  }

  private fun phoneDateInSeconds() = getPhoneDate().time / 1000

  private val serviceConnection = object : ServiceConnection {

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
      val binder = service as FindPhoneService.LocalBinder
      findPhoneSubscription = binder.finderStream()
          .subscribe(
              onNext@ {
                Log.d("OK", "$it")
                FirebaseFinderPhone(it)
              },
              onError@ { Log.e("FAT", "aaa") }
          )


    }

    override fun onServiceDisconnected(arg0: ComponentName) {
      findPhoneSubscription.unsubscribe()
    }
  }


}
