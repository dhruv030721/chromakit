package com.dhruv21.chromakit.core.pool

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe object pool for direct ByteBuffers to prevent Garbage Collector (GC)
 * pauses during high frame-rate streaming (30-60 FPS).
 *
 * Adheres to Object Pool Design Pattern.
 */
class BufferPool(
    private val bufferCapacity: Int,
    private val maxPoolSize: Int = 4
) {
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()

    /**
     * Obtains an existing direct buffer from the pool or allocates a new one if pool is empty.
     */
    fun acquire(): ByteBuffer {
        val buffer = pool.poll() ?: ByteBuffer.allocateDirect(bufferCapacity).order(ByteOrder.nativeOrder())
        buffer.clear()
        return buffer
    }

    /**
     * Returns a buffer to the pool for reuse.
     */
    fun release(buffer: ByteBuffer) {
        if (buffer.capacity() == bufferCapacity && pool.size < maxPoolSize) {
            buffer.clear()
            pool.offer(buffer)
        }
    }

    /**
     * Clears all cached buffers.
     */
    fun clear() {
        pool.clear()
    }
}
