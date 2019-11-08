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
