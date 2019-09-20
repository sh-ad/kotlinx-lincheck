package org.jetbrains.kotlinx.lincheck.test.dsl

import org.jetbrains.kotlinx.lincheck.scenario
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomScenariosDSLTest {
    @Test
    fun testMinimalScenario() {
        val scenario = scenario {}
        assertEquals(0, scenario.initExecution.size)
        assertEquals(0, scenario.parallelExecution.size)
        assertEquals(0, scenario.postExecution.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInitialPartRedeclaration() {
        scenario {
            initial {}
            initial {
                actor(Object::hashCode)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParallelPartRedeclaration() {
        scenario {
            parallel {}
            parallel {}
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPostPartRedeclaration() {
        scenario {
            post {
                actor(Object::hashCode)
            }
            post {}
        }
    }

    @Test
    fun testAverageScenario() {
        val scenario = scenario {
            initial {
                repeat(2) { actor(Object::hashCode) }
            }
            parallel {
                repeat(2) {
                    thread {
                        repeat(5 + it) {
                            actor(Object::equals, this)
                        }
                    }
                }
            }
            post {
                repeat(3) { actor(Object::toString) }
            }
        }
        assertEquals(2, scenario.initExecution.size)
        assertEquals(3, scenario.postExecution.size)
        assertEquals(2, scenario.parallelExecution.size)
        assertEquals(5, scenario.parallelExecution[0].size)
        assertEquals(6, scenario.parallelExecution[1].size)
    }
}