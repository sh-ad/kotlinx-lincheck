package shad.Nikiforovskaya_8.first

import java.util.concurrent.atomic.AtomicMarkableReference

class ListNode<T>(private val key: T) {

    companion object {
        val UNMARKED = false
        val MARKED = true
    }

    val next : AtomicMarkableReference<ListNode<T>?> = AtomicMarkableReference(null, UNMARKED)

    fun nextNode() : ListNode<T>? {
        return next.reference
    }

    fun setNextNode(next : ListNode<T>?) : Unit {
        this.next.set(next, UNMARKED)
    }

    fun isMarked() : Boolean {
        return next.isMarked
    }

    fun getKey() : T {
        return key
    }

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != javaClass) {
            return false
        }
        other as ListNode<T>
        return next == other
    }

    override fun toString(): String {
        return "($key, ${isMarked()})"
    }
}