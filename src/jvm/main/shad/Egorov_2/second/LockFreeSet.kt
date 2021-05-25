/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2021 JetBrains s.r.o.
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

package shad.Egorov_2.second

import java.util.concurrent.atomic.AtomicMarkableReference

class LockFreeSet<T : Comparable<T>> : LockFreeSetInterface<T> {
    private val head = AtomicMarkableReference(
        Node(
            null,
            AtomicMarkableReference<Node<T>>(null, false)
        ), true
    )


    override val isEmpty: Boolean
        get() {
            var cur = head.reference.next.reference
            while (cur != null) {
                if (!isRemoved(cur))
                    return false
                cur = cur.next.reference
            }
            return true
        }

    override fun add(value: T): Boolean {
        val node = Node(value, AtomicMarkableReference<Node<T>>(null, false))

        while (true) {
            val (prev, current) = find(value)

            if (current != null) {
                return false
            } else {
                if (prev.next.compareAndSet(null, node, false, false)) {
                    return true
                }
            }
        }
    }

    override fun remove(value: T): Boolean {
        while (true) {
            val (prev, current) = find(value)

            if (current == null)
                return false

            val ref = current.next.reference
            if (current.next.compareAndSet(ref, ref, false, true)) {
                physicallyRemove(prev, current)
                return true
            }
        }
    }


    override fun contains(value: T): Boolean {
        var cur = head.reference.next.reference
        while (cur != null) {
            if (cur.value == value)
                return !isRemoved(cur)
            cur = cur.next.reference
        }
        return false
    }

    override fun iterator(): Iterator<T> {
        var firstSnapshot = takeSnapshot()
        var secondSnapshot = takeSnapshot()
        while (firstSnapshot != secondSnapshot) {
            firstSnapshot = takeSnapshot()
            secondSnapshot = takeSnapshot()
        }
        return firstSnapshot.iterator()
    }

    private fun takeSnapshot(): List<T> {
        var cur = head.reference.next.reference
        val set = mutableListOf<Node<T>>()

        while (cur != null) {
            set.add(cur)
            cur = cur.next.reference
        }

        return set.filter { ref -> !isRemoved(ref) }.map { ref -> ref.value!! }
    }

    private fun find(value: T): Pair<Node<T>, Node<T>?> {
        var prev: Node<T>
        var node: Node<T>?
        prev = head.reference
        node = prev.next.reference
        while (node != null) {
            //FIX 2xREMOVE
            if (isRemoved(node)) {
                physicallyRemove(prev, node)
            }
            if (!isRemoved(node) && value == node.value) {
                return Pair(prev, node)
            }
            prev = node
            node = node.next.reference
        }

        return Pair<Node<T>, Node<T>?>(prev, node)
    }

    private fun isRemoved(node: Node<T>): Boolean {
        return node.next.isMarked
    }

    private fun physicallyRemove(prev: Node<T>, node: Node<T>) {
        var next: Node<T>? = node
        do {
            next = next!!.next.reference
        } while (next != null && isRemoved(next))

        prev.next.compareAndSet(node, next, false, false)
    }

    companion object {
        private data class Node<T>(val value: T?, val next: AtomicMarkableReference<Node<T>>)
    }
}