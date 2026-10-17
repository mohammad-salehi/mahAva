package com.mahava.app.demo

import com.mahava.app.MahavaApplication

/** Release builds never contain the demo seeder; these calls do nothing. */
object DemoMode {
    const val enabled = false
    fun onAppStart(app: MahavaApplication) = Unit
    suspend fun handle(app: MahavaApplication, path: String): Boolean = false
}
