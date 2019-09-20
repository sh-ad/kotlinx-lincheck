package org.jetbrains.kotlinx.lincheck.test.dsl

import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.verifier.linearizability.LinearizabilityVerifier
import org.jetbrains.kotlinx.lincheck.scenario
import org.junit.Test
import java.lang.AssertionError

class LockFreeSetTest {
    @Test(expected = AssertionError::class)
    fun test() {
        val scenario = scenario {
            parallel {
                thread {
                    actor(LockFreeSet::snapshot)
                }

                thread {
                    repeat(2) {
                        for (key in 1..2) {
                            actor(LockFreeSet::add, key)
                            actor(LockFreeSet::remove, key)
                        }
                    }
                }
            }
        }

        val options = StressOptions()
                .verifier(LinearizabilityVerifier::class.java)
                .addCustomScenario(scenario)
                .invocationsPerIteration(1000000)
                .requireStateEquivalenceImplCheck(false)

        LinChecker.check(LockFreeSet::class.java, options)
    }
}
