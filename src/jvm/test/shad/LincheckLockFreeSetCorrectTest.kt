package ru.hse.egorov


import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingCTest
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.junit.Test
import shad.egorov_1.LockFreeSetCorrect

@Param(name = "key", gen = IntGen::class)
@ModelCheckingCTest
class LincheckLockFreeSetCorrectTest {
    private val set = LockFreeSetCorrect<Int>()

    @Operation
    fun add(@Param(name = "key") key: Int) = set.add(key)

    @Operation
    fun remove(@Param(name = "key") key: Int) = set.remove(key)

//    @Operation
//    fun contains(@Param(name = "key") key: Int) = set.contains(key)

    @Operation
    fun isEmpty() = set.isEmpty

//    @Operation
//    fun iterator(): List<Int> = set.iterator().asSequence().toList()

    @Test
    fun test() {
        LinChecker.check(LincheckLockFreeSetCorrectTest::class.java)
    }

    @Test
    fun runTest() {
//        val opts = StressOptions()
        val opts = ModelCheckingOptions()
            .requireStateEquivalenceImplCheck(false)
            .minimizeFailedScenario(true)
            .actorsBefore(0)
            .actorsAfter(0)
            .actorsPerThread(3)
            .invocationsPerIteration(1000)
            .iterations(1000)
            .threads(4)
            .logLevel(LoggingLevel.INFO)
        LinChecker.check(LincheckLockFreeSetCorrectTest::class.java, opts)
    }

}