package com.everettpenne.skulk.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** A tap on the tab that is already showing. The visible list listens and scrolls to its top (or refreshes, when it is already there). */
object TabReselect {
    val events = MutableSharedFlow<Tab>(extraBufferCapacity = 1)
}
