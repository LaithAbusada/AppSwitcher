import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class ServiceLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        // Initialize the lifecycle state, for example, you can set it to STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun onServiceStarted() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    // Destroy the lifecycle
    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    fun onServiceStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
}
