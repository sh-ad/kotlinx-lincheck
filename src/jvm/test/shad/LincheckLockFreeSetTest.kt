package shad


import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingCTest
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.junit.Test

@Param(name = "key", conf = "1:3", gen = IntGen::class)
//@Param(name = "key", conf = "-10:10", gen = IntGen::class)
@ModelCheckingCTest
class LincheckLockFreeSetTest {
    private val set = shad.Yutman_10.second.LockFreeSetImpl<Int>()

    @Operation()
    fun add(@Param(name = "key") key: Int) = set.add(key)

    @Operation()
    fun remove(@Param(name = "key") key: Int) = set.remove(key)

    @Operation
    fun contains(@Param(name = "key") key: Int) = set.contains(key)

    @Operation()
    fun isEmpty() = set.isEmpty

    @Operation
    fun iterator(): List<Int> = set.iterator().asSequence().toList()

    @Test
    fun runTest_1() {
//        val opts = StressOptions()
        val opts = ModelCheckingOptions()
            .requireStateEquivalenceImplCheck(false)
            .actorsBefore(0)
            .actorsAfter(0)
            .iterations(1000)
            .actorsPerThread(10)
            .invocationsPerIteration(10000)
            .threads(3)
            .logLevel(LoggingLevel.INFO)
//            .executionGenerator(SeparateOperationThreadExecutionGenerator::class.java)
        LinChecker.check(LincheckLockFreeSetTest::class.java, opts)
    }

    @Test
    fun runTest_2() {
//        val opts = StressOptions()
        val opts = ModelCheckingOptions()
            .requireStateEquivalenceImplCheck(false)
            .actorsBefore(0)
            .actorsAfter(0)
            .iterations(5)
            .actorsPerThread(5)
            .invocationsPerIteration(100000)
            .threads(4)
            .logLevel(LoggingLevel.INFO)
        LinChecker.check(LincheckLockFreeSetTest::class.java, opts)
    }
}