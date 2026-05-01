package org.example

data class Instance(val id: String, val recording: String, val lastRun: Long = System.currentTimeMillis())

class RecognitionHandler {
    val instances: MutableList<Instance> = mutableListOf()

    fun load(ids: Array<String>, recordings: Array<String>) {
        if (ids.isEmpty() || recordings.isEmpty() || ids.size != recordings.size) {
            return
        }

        flush()

        for (i in ids.indices) {
            instances.add(Instance(ids[i], recordings[i]))
        }
    }

    fun signal(id: String) : Boolean {
        val recording = instances.find { instance -> instance.id == id }?.recording
        if (recording == null) return false

        instances.find { instance -> instance.id == id }?.lastRun = System.currentTimeMillis()

        // Add call to TTS engine here

        return true
    }

    fun flush() {
        instances.clear()
    }
}