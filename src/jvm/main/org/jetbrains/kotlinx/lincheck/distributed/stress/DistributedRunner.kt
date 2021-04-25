/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2020 JetBrains s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>
 */

package org.jetbrains.kotlinx.lincheck.distributed.stress

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.distributed.*
import org.jetbrains.kotlinx.lincheck.distributed.queue.FastQueue
import org.jetbrains.kotlinx.lincheck.execution.ExecutionResult
import org.jetbrains.kotlinx.lincheck.execution.ExecutionScenario
import org.jetbrains.kotlinx.lincheck.execution.HBClock
import org.jetbrains.kotlinx.lincheck.execution.ResultWithClock
import org.jetbrains.kotlinx.lincheck.runner.*
import org.jetbrains.kotlinx.lincheck.strategy.LincheckFailure
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.CancellationException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

inline fun withProbability(probability: Double, func: () -> Unit) {
    val rand = Random.nextDouble(0.0, 1.0)
    if (rand <= probability) {
        func()
    }
}

enum class LogLevel { NO_OUTPUT, ITERATION_NUMBER, MESSAGES, ALL_EVENTS, KICKED }

val logLevel = LogLevel.ALL_EVENTS
var debugLogs = FastQueue<String>()

fun logMessage(givenLogLevel: LogLevel, f: () -> String) {
    if (logLevel >= givenLogLevel) {
        val s = Thread.currentThread().name + " " + f()
        debugLogs.put(s)
        // println(s)
        System.out.flush()
    }
}

internal fun <T> AtomicArray<T?>.at(index: Int): T = this[index].value!!

open class DistributedRunner<Message, Log>(
    strategy: DistributedStrategy<Message, Log>,
    val testCfg: DistributedCTestConfiguration<Message, Log>,
    testClass: Class<*>,
    validationFunctions: List<Method>,
    stateRepresentationFunction: Method?
) : Runner(
    strategy, testClass,
    validationFunctions,
    stateRepresentationFunction
) {
    companion object {
        const val CONTEXT_SWITCH_PROBABILITY = 0.3
    }

    private val runnerHash = this.hashCode()
    private val context: DistributedRunnerContext<Message, Log> =
        DistributedRunnerContext(testCfg, scenario, runnerHash, stateRepresentationFunction)
    private lateinit var testNodeExecutions: Array<TestNodeExecution>
    private lateinit var environments: Array<EnvironmentImpl<Message, Log>>
    private val exception = atomic<Throwable?>(null)
    private val numberOfNodes = context.addressResolver.totalNumberOfNodes
    private val isRunning = atomic(false)

    override fun initialize() {
        super.initialize()
        testNodeExecutions = Array(context.addressResolver.nodesWithScenario) { t ->
            TestNodeExecutionGenerator.create(this, t, scenario.parallelExecution[t])
        }
        context.runner = this
    }

    private fun NodeDispatcher.createScope(): CoroutineScope {
        return CoroutineScope(this + AlreadyIncrementedCounter() + handler)
    }

    private fun reset() {
        exception.lazySet(null)
        isRunning.lazySet(false)
        debugLogs = FastQueue()
        context.reset()
        environments = Array(numberOfNodes) {
            EnvironmentImpl(context, it)
        }
        context.testInstances = Array(numberOfNodes) {
            context.addressResolver[it].getConstructor(Environment::class.java)
                .newInstance(environments[it]) as Node<Message>
        }
        context.testNodeExecutions = testNodeExecutions
        context.testNodeExecutions.forEachIndexed { t, ex ->
            ex.testInstance = context.testInstances[t]
            val actors = scenario.parallelExecution[t].size
            ex.results = arrayOfNulls(actors)
            ex.actorId = 0
            ex.clocks = arrayOfNulls(actors)
        }
    }

    override fun run(): InvocationResult {
        reset()
        repeat(numberOfNodes) { i ->
            val dispatcher = context.dispatchers[i]
            dispatcher.createScope().launch { runNode(i) }
            dispatcher.createScope().launch {
                receiveUnavailableNodes(i)
            }
            dispatcher.launchReceiveMessage(i)
        }
        isRunning.lazySet(true)
        try {
            runBlocking {
                withTimeout(testCfg.timeoutMs) {
                    context.taskCounter.signal.await()
                }
                logMessage(LogLevel.ALL_EVENTS) {
                    "Semaphore aqcuired"
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("-----------------------------------")
            debugLogs.toList().forEach { println(it) }
            //println(constructStateRepresentation())
            return DeadlockInvocationResult(collectThreadDump())
        }
        context.dispatchers.forEach { it.shutdown() }
        environments.forEach { it.isFinished = true }
        if (exception.value != null) {
            println(constructStateRepresentation())
            return UnexpectedExceptionInvocationResult(exception.value!!)
        }
        repeat(numberOfNodes) {
            context.logs[it] = environments[it].log
        }
        //context.probabilities.forEachIndexed{ i, p -> println("[$i]: ${p.curMsgCount}")}
        /*if (testCfg.supportRecovery == RecoveryMode.NO_RECOVERIES) {
            context.probabilities.forEach { it.setInitial() }
        }*/

        context.testInstances.forEach {
            executeValidationFunctions(it, validationFunctions) { functionName, exception ->
                val s = ExecutionScenario(
                    scenario.initExecution,
                    scenario.parallelExecution,
                    emptyList()
                )
                debugLogs.toList().forEach { println(it) }
                println("-----------------------------")
                println(constructStateRepresentation())
                return ValidationFailureInvocationResult(s, functionName, exception)
            }
        }

        context.testNodeExecutions.zip(scenario.parallelExecution).forEach { it.first.setSuspended(it.second) }
        val parallelResultsWithClock = context.testNodeExecutions.mapIndexed { i, ex ->
            //TODO add real vector clock
            // ex.results
            val fakeClock = Array(ex.results.size) {
                IntArray(numberOfNodes)
            }
            ex.results.zip(fakeClock).map { ResultWithClock(it.first!!, HBClock(it.second)) }
        }
        val results = ExecutionResult(
            emptyList(), null, parallelResultsWithClock, super.constructStateRepresentation(),
            emptyList(), null
        )
        //println(constructStateRepresentation())
        return CompletedInvocationResult(results)
    }

    private suspend fun handleException(iNode: Int, f: suspend () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            onFailure(iNode, e)
        }
    }

    //TODO: Maybe a better way?
    private val handler = CoroutineExceptionHandler { _, _ -> }


    private suspend fun receiveMessages(i: Int, sender: Int) {
        coroutineScope {
            val channel = context.messageHandler[sender, i]
            val testInstance = context.testInstances[i]
            try {
                while (true) {
                    try {
                        val m = channel.receive()
                        context.incClock(i)
                        val clock = context.maxClock(i, m.clock)
                        context.events.put(
                            i to
                                    MessageReceivedEvent(
                                        m.message,
                                        sender = sender,
                                        id = m.id,
                                        clock = clock,
                                        state = context.getStateRepresentation(i)
                                    )
                        )
                        launch {
                            handleException(i) {
                                testInstance.onMessage(m.message, sender)
                            }
                        }
                        withProbability(0.3) {
                            yield()
                        }
                    } catch (_: CancellationException) {
                        logMessage(LogLevel.ALL_EVENTS) {
                            "[$i]: Caught cancellation exception"
                        }
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                logMessage(LogLevel.ALL_EVENTS) {
                    "[$i]: Caught close exception"
                }
            }
        }
    }

    private suspend fun receiveUnavailableNodes(i: Int) {
        coroutineScope {
            val channel = context.failureNotifications[i]
            val testInstance = context.testInstances[i]
            try {
                check(Thread.currentThread() is NodeDispatcher.NodeTestThread)
                logMessage(LogLevel.ALL_EVENTS) {
                    "Receiving failed nodes for node $i..."
                }
                while (true) {
                    val p = channel.receive()
                    context.incClock(i)
                    val clock = context.maxClock(i, p.second)
                    context.events.put(
                        i to CrashNotificationEvent(
                            p.first,
                            clock,
                            context.testInstances[i].stateRepresentation()
                        )
                    )
                    launch {
                        handleException(i) {
                            testInstance.onNodeUnavailable(p.first)
                        }
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
            }
        }
    }

    private fun NodeDispatcher.launchReceiveMessage(i: Int) {
        repeat(numberOfNodes) {
            createScope().launch { receiveMessages(i, it) }
        }
    }

    private suspend fun runNode(iNode: Int) {
        handleException(iNode) {
            context.taskCounter.runSafely {
                context.testInstances[iNode].onStart()
            }
            if (iNode >= context.addressResolver.nodesWithScenario) {
                return@handleException
            }
            val testNodeExecution = context.testNodeExecutions[iNode]
            val scenarioSize = scenario.parallelExecution[iNode].size
            logMessage(LogLevel.ALL_EVENTS) {
                "[$iNode]: Start scenario, actorid is ${testNodeExecution.actorId}"
            }
            while (testNodeExecution.actorId < scenarioSize) {
                val i = testNodeExecution.actorId
                val actor = scenario.parallelExecution[iNode][i]
                context.incClock(iNode)
                context.events.put(
                    iNode to
                            OperationStartEvent(
                                i,
                                context.vectorClock[iNode].copyOf(),
                                context.getStateRepresentation(iNode)
                            )
                )
                try {
                    testNodeExecution.actorId++
                    logMessage(LogLevel.ALL_EVENTS) {
                        "[$iNode]: Operation $i started"
                    }
                    val res = if (!actor.blocking) context.taskCounter.runSafely {
                        testNodeExecution.runOperation(i)
                    } else testNodeExecution.runOperation(i)
                    testNodeExecution.clocks[i] = context.vectorClock[iNode].copyOf()
                    testNodeExecution.results[i] = if (actor.method.returnType == Void.TYPE) {
                        VoidResult
                    } else {
                        createLincheckResult(res)
                    }
                    logMessage(LogLevel.ALL_EVENTS) {
                        "[$iNode]: Wrote result $i ${context.testNodeExecutions[iNode].hashCode()} ${context.testNodeExecutions[iNode].results[i]}"
                    }
                } catch (e: Throwable) {
                    if (e is CrashError) {
                        throw e
                    }
                    if (e.javaClass in actor.handledExceptions) {
                        context.testNodeExecutions[iNode].results[i] = createExceptionResult(e.javaClass)
                    } else {
                        onFailure(iNode, e)
                        testNodeExecution.actorId = scenarioSize
                    }
                }
                withProbability(0.3) {
                    yield()
                }
            }
            context.testInstances[iNode].onScenarioFinish()
            logMessage(LogLevel.ALL_EVENTS) {
                "[$iNode]: Operations over"
            }
        }
    }

    override fun onFailure(iThread: Int, e: Throwable) {
        super.onFailure(iThread, e)
        if (exception.compareAndSet(null, e)) {
            context.taskCounter.signal.signal()
        }
    }

    suspend fun onNodeFailure(iNode: Int) {
        context.incClock(iNode)
        val clock = context.vectorClock[iNode].copyOf()
        context.events.put(
            iNode to NodeCrashEvent(
                clock,
                context.testInstances[iNode].stateRepresentation()
            )
        )
        context.failureNotifications.filterIndexed { index, _ -> index != iNode }.forEach {
            try {
                it.send(iNode to clock)
            } catch (_: ClosedSendChannelException) {
            }
        }
        logMessage(LogLevel.ALL_EVENTS) {
            "[$iNode]: Failure notifications sent"
        }
        //println("[$iNode]: failure on ${context.probabilities[iNode].curMsgCount}")
        context.messageHandler.close(iNode)
        context.failureNotifications[iNode].close()

        context.dispatchers[iNode].crash()
        environments[iNode].isFinished = true
        context.testNodeExecutions.getOrNull(iNode)?.crash(context.vectorClock[iNode].copyOf())
        if (testCfg.supportRecovery == RecoveryMode.ALL_NODES_RECOVER ||
            testCfg.supportRecovery == RecoveryMode.MIXED
            && context.probabilities[iNode].nodeRecovered()
        ) {
            val delta = context.initialTasksForNode
            context.taskCounter.add(delta)
            val logs = environments[iNode].log.toMutableList()
            context.messageHandler.reset(iNode)
            context.failureNotifications[iNode] = Channel(UNLIMITED)
            environments[iNode] = EnvironmentImpl(context, iNode, logs)
            context.testInstances[iNode] =
                context.addressResolver[iNode].getConstructor(Environment::class.java)
                    .newInstance(environments[iNode]) as Node<Message>
            context.testNodeExecutions.getOrNull(iNode)?.testInstance = context.testInstances[iNode]
            val dispatcher = NodeDispatcher(iNode, context.taskCounter, runnerHash)
            context.dispatchers[iNode] = dispatcher
            context.failureInfo.setRecovered(iNode)
            dispatcher.createScope().launch {
                logMessage(LogLevel.ALL_EVENTS) {
                    "[$iNode]: Launch recover"
                }
                handleException(iNode) {
                    context.events.put(
                        iNode to ProcessRecoveryEvent(
                            context.vectorClock[iNode].copyOf(),
                            context.testInstances[iNode].stateRepresentation()
                        )
                    )
                    context.testInstances[iNode].recover()
                    runNode(iNode)
                }
            }
            dispatcher.launchReceiveMessage(iNode)
            dispatcher.createScope().launch {
                logMessage(LogLevel.ALL_EVENTS) {
                    "[$iNode]: Launch receiving failures after recover"
                }
                receiveUnavailableNodes(iNode)
            }
        } else {
            context.testNodeExecutions.getOrNull(iNode)?.crashRemained()
        }
        suspendCoroutine<Unit> { }
    }

    override fun constructStateRepresentation(): String {
        return context.testInstances.mapIndexed { index, node ->
            index to stateRepresentationFunction?.let { getMethod(node, it) }
                ?.invoke(node) as String?
        }.filterNot { it.second.isNullOrBlank() }.joinToString(separator = "\n") { "STATE [${it.first}]: ${it.second}" }
    }

    private fun collectThreadDump() = Thread.getAllStackTraces().filter { (t, _) ->
        t is NodeDispatcher.NodeTestThread && t.runnerHash == runnerHash
    }

    fun storeEventsToFile(failure: LincheckFailure) {
        if (testCfg.logFilename == null) return
        File(testCfg.logFilename).printWriter().use { out ->
            out.println(failure)
            out.println()
            context.events.toList().forEach { p ->
                out.println("${p.first} # ${p.second}")
            }
        }
    }
}
