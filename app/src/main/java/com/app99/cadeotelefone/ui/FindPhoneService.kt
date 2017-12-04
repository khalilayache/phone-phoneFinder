package com.app99.cadeotelefone.ui

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import rx.subjects.BehaviorSubject

class FindPhoneService : Service() {

  private val FIND_PHONE_REFERENCE = "findPhone"

  private val PROD = "prodUAU"
  private val DEV = "testDEV"

//  private val finderRef = FirebaseDatabase.getInstance().getReference(PROD).child(FIND_PHONE_REFERENCE)

  private val finderRef = FirebaseDatabase.getInstance().getReference(DEV).child(FIND_PHONE_REFERENCE)

  private var finderStream: BehaviorSubject<DataSnapshot> = BehaviorSubject.create()

  private val mBinder = LocalBinder()

  inner class LocalBinder : Binder() {

    fun finderStream() = finderStream
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

    finderRef.addValueEventListener(object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        finderStream.onNext(dataSnapshot)
      }

      override fun onCancelled(databaseError: DatabaseError) {
      }
    })

    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder {
    return mBinder
  }


}


