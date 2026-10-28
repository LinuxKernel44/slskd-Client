package com.linuxkernel44.slskdclient

import android.app.Application
import android.content.Context
import com.linuxkernel44.slskdclient.data.Graph

class SlskdApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}

val Context.graph: Graph get() = (applicationContext as SlskdApp).graph
