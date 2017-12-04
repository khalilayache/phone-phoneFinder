package com.app99.cadeotelefone.contract

import com.google.firebase.database.ValueEventListener

interface ServiceCallbacks {

  fun phoneListenerCallback(): ValueEventListener
  fun finderListenerCallback(): ValueEventListener

}
