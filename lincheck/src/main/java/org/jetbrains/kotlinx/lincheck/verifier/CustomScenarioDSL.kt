/*-
 * #%L
 * Lincheck
 * %%
 * Copyright (C) 2019 JetBrains s.r.o.
 * %%
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
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package org.jetbrains.kotlinx.lincheck.verifier

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.execution.*
import java.lang.IllegalStateException
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/**
 * Kotlin DSL for defining custom scenarios.
 *
 *
 * Example:
 * ```
 * scenario {
 *   initial {
 *     operation(CustomTest::offer, 1)
 *     operation(CustomTest::offer, 2)
 *   }
 *   parallel {
 *     thread {
 *       operation(CustomTest::poll)
 *     }
 *     thread {
 *       operation(CustomTest::poll)
 *     }
 *   }
 * }
 * ```
 */

/**
 * Generate ExecutionScenario using DSL
 */
fun scenario(block: DSLScenarioBuilder.() -> Unit): ExecutionScenario =
        DSLScenarioBuilder().apply(block).buildScenario()

/**
 * Create an actor from a function [f] and its arguments [args]
 */
fun actor(f: KFunction<*>, vararg args: Any?): Actor {
    val method = f.javaMethod
        ?: throw IllegalStateException("The function is a constructor or cannot be represented by a Java Method")
    require(method.exceptionTypes.all { Throwable::class.java.isAssignableFrom(it) }) { "Not all declared exceptions are Throwable" }
    return Actor(
        method = method,
        arguments = args.toList(),
        handledExceptions = (method.exceptionTypes as Array<Class<out Throwable>>).toList()
    )
}

@ScenarioDSLMarker
class DSLThreadScenario : ArrayList<Actor>() {
    /**
     * An operation to be executed
     */
    fun operation(f: KFunction<*>, vararg args: Any?) {
        add(actor(f, *args))
    }
}

@ScenarioDSLMarker
class DSLParallelScenario : ArrayList<DSLThreadScenario>() {
    /**
     * Define a sequence of operations to be executed in a separate thread
     */
    fun thread(block: DSLThreadScenario.() -> Unit) {
        add(DSLThreadScenario().apply(block))
    }
}

@ScenarioDSLMarker
class DSLScenarioBuilder {
    private val initial = mutableListOf<Actor>()
    private var parallel = mutableListOf<MutableList<Actor>>()
    private val post = mutableListOf<Actor>()

    /**
     * Define initial part of the execution
     */
    fun initial(block: DSLThreadScenario.() -> Unit) {
        initial.addAll(DSLThreadScenario().apply(block))
    }

    /**
     * Define parallel part of the execution
     */
    fun parallel(block: DSLParallelScenario.() -> Unit) {
        parallel.addAll(DSLParallelScenario().apply(block))
    }

    /**
     * Define post part of the execution
     */
    fun post(block: DSLThreadScenario.() -> Unit) {
        post.addAll(DSLThreadScenario().apply(block))
    }

    /**
     * Build an ExecutionScenario from previously initialized parts
     */
    fun buildScenario(): ExecutionScenario {
        return ExecutionScenario(
                initial,
                parallel,
                post
        )
    }
}

@DslMarker
private annotation class ScenarioDSLMarker