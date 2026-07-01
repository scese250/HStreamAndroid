package com.hstream.android

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoItem(
    val url: String,
    val title: String,
    val posterUrl: String
) : Parcelable
