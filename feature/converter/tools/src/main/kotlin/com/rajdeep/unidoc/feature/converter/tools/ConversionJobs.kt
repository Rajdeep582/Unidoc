package com.rajdeep.unidoc.feature.converter.tools

import com.rajdeep.unidoc.core.common.di.ApplicationScope
import com.rajdeep.unidoc.core.model.ToolProgress
import com.rajdeep.unidoc.core.model.ToolRequest
import com.rajdeep.unidoc.core.model.ToolRunner
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * App-scoped conversion jobs: a job keeps running when the user leaves the tool screen (or the
 * screen is recreated) and the result is picked up again by job id.
 */
@Singleton
class ConversionJobs @Inject constructor(private val runner: ToolRunner, @param:ApplicationScope private val scope: CoroutineScope) {
    private class Entry(val state: MutableStateFlow<ToolProgress>, var job: Job? = null)

    private val jobs = ConcurrentHashMap<String, Entry>()

    fun start(request: ToolRequest): String {
        val id = UUID.randomUUID().toString()
        val entry = Entry(MutableStateFlow(ToolProgress.Running(null, "Preparing")))
        jobs[id] = entry
        entry.job = scope.launch {
            runner.run(request)
                .catch { e -> if (e !is CancellationException) emit(ToolProgress.Failed(e.message ?: "Something went wrong")) }
                .collect { entry.state.value = it }
        }
        // Keep only the latest few finished jobs in memory.
        if (jobs.size > 8) {
            jobs.entries.filter { it.key != id && it.value.job?.isActive != true }.drop(4).forEach { jobs.remove(it.key) }
        }
        return id
    }

    fun observe(id: String): StateFlow<ToolProgress>? = jobs[id]?.state?.asStateFlow()

    fun cancel(id: String) {
        jobs.remove(id)?.job?.cancel()
    }
}
