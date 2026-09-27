package wales.tucker.seren.edit

import android.app.Application

open class SerenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
