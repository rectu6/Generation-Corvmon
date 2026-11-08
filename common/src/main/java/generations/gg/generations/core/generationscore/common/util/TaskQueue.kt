package generations.gg.generations.core.generationscore.common.util

import generations.gg.generations.core.generationscore.common.GenerationsCore
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class TaskQueue {
    private val taskQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    private val workerThread: Thread
    private val running = AtomicBoolean(true)

    init {
        workerThread = Thread { this.processTasks() }
        workerThread.isDaemon = true
        workerThread.start()
    }

    private fun processTasks() {
        while (true) {
            // Waits for a task: the previous loop polled only when the queue was not
            // empty, so it spun on a CPU core whenever there was nothing to load.
            val task = try {
                taskQueue.take()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            try {
                task.run()
            } catch (e: Throwable) {
                // One model must not stop the loading of all the others: an Error
                // used to end this thread, and no model loaded until a restart.
                GenerationsCore.LOGGER.error("Model loading task failed", e)
            }
        }
    }

    fun addTask(task: Runnable) {
        taskQueue.offer(task)
    }

    fun start() {
        running.set(true)
    }
}
