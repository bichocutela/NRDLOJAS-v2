import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun main() = runBlocking {
    val flow = MutableSharedFlow<String>()
    launch {
        println("emitting")
        flow.emit("hello")
        println("emitted")
    }
    delay(100)
}
