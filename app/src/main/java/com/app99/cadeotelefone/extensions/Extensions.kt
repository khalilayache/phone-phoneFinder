package com.app99.cadeotelefone.extensions

import android.content.Context
import android.os.Build
import android.support.v4.content.ContextCompat
import android.view.View

fun View.gone() {
  this.visibility = View.GONE
}

fun View.visible() {
  this.visibility = View.VISIBLE
}

fun Context.getCompatColor(color: Int): Int {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    this.getColor(color)
  } else {
    ContextCompat.getColor(this, color)
  }
}
