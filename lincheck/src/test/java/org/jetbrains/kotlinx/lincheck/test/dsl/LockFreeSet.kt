package org.jetbrains.kotlinx.lincheck.test.dsl

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LockFreeSet {
    private val head = Node(Int.MIN_VALUE, null, true) // dummy node

    fun add(key: Int): Boolean {
        var node = head

        while (true) {
            while (true) {
                node = node.next.get() ?: break
                if (node.key == key) {
                    return if (node.isDeleted.get())
                        node.isDeleted.compareAndSet(true, false)
                    else
                        false
                }
            }

            val newNode = Node(key, null, false)
            if (node.next.compareAndSet(null, newNode))
                return true
        }
    }

    fun remove(key: Int): Boolean {
        var node = head
        while (true) {
            node = node.next.get() ?: break
            if (node.key == key) {
                return if (node.isDeleted.get())
                    false
                else
                    node.isDeleted.compareAndSet(false, true)
            }
        }

        return false
    }

    //incorrect operation implementation, however the minimal needed wrong execution sequence is quite large
    fun snapshot(): List<Int> {
        while (true) {
            val firstSnapshot = doSnapshot()
            val secondSnapshot = doSnapshot()

            if (firstSnapshot == secondSnapshot)
                return firstSnapshot
        }
    }

    private fun doSnapshot(): List<Int> {
        val snapshot = mutableListOf<Int>()

        var node = head

        while (true) {
            node = node.next.get() ?: break
            if (!node.isDeleted.get())
                snapshot.add(node.key)
        }

        return snapshot
    }

    private inner class Node(val key: Int, next: Node?, initialMark: Boolean) {
        val next: AtomicReference<Node?> = AtomicReference(next)
        val isDeleted = AtomicBoolean(initialMark)
    }
}