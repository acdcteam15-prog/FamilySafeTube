package com.example.familysafetube

/** Usage-category ids. These double as the suffix of the per-category limit keys ("cat_limit_<id>"). */
object Categories {
    const val YOUTUBE = "youtube"
    const val VIDEOS = "offline_videos"
    const val IMAGES = "images"
    val ALL = setOf(YOUTUBE, VIDEOS, IMAGES)
}
