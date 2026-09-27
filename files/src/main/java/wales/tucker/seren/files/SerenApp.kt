package wales.tucker.seren.files

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import wales.tucker.seren.files.ops.OperationService

open class SerenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        // Each long job keeps the app running with a notification until it's done.
        MainScope().launch {
            container.operations.current.map { it != null }.distinctUntilChanged().filter { it }.collect {
                OperationService.start(this@SerenApp)
            }
        }
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
