package com.photomatch.core

/** JNI bridge to the Rust core in /rust (libphotomatch_core.so). */
object NativeCore {
    init {
        System.loadLibrary("photomatch_core")
    }

    const val NONE = 0
    const val KEEP = 1
    const val NOPE = 2
    const val SUPER = 3
    const val DELETED = 4

    /** Thumbnail side used for perceptual hashing (32x32 grayscale). */
    const val HASH_SIDE = 32

    external fun storeOpen(dir: String): Long
    external fun storeClose(handle: Long)
    external fun storeSet(handle: Long, ids: LongArray, decision: Int)
    /** Interleaved [id, decision, id, decision, ...]. */
    external fun storeDecisions(handle: Long): LongArray
    external fun storeReset(handle: Long)

    external fun missingHashes(handle: Long, ids: LongArray): LongArray
    /** [pixels] = ids.size packed 32x32 grayscale thumbnails. */
    external fun hashAndStore(handle: Long, ids: LongArray, pixels: ByteArray): Int
    external fun similarGroups(handle: Long, ids: LongArray, times: LongArray, threshold: Int, windowMs: Long): IntArray
    external fun moments(times: LongArray, gapMs: Long): IntArray
}
