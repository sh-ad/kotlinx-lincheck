package shad.Fedotov_5.first

import java.util.concurrent.atomic.AtomicStampedReference

/**
 * Lock-Free РјРЅРѕР¶РµСЃС‚РІРѕ.
 * @param <T> РўРёРї РєР»СЋС‡РµР№
</T>
 */
class LockFreeSet<T : Comparable<T>> {

    private val head = Node(Node<T>(null, null), null)


    /**
     * РџСЂРѕРІРµСЂРєР° РјРЅРѕР¶РµСЃС‚РІР° РЅР° РїСѓСЃС‚РѕС‚Сѓ
     *
     * РђР»РіРѕСЂРёС‚Рј РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєР°Рє РјРёРЅРёРјСѓРј lock-free
     *
     * @return true РµСЃР»Рё РјРЅРѕР¶РµСЃС‚РІРѕ РїСѓСЃС‚Рѕ, РёРЅР°С‡Рµ - false
     */
    val isEmpty: Boolean
        get() {
        return makeSnapshot().isEmpty()
    }


    /**
     * Р”РѕР±Р°РІРёС‚СЊ РєР»СЋС‡ Рє РјРЅРѕР¶РµСЃС‚РІСѓ
     *
     * РђР»РіРѕСЂРёС‚Рј РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєР°Рє РјРёРЅРёРјСѓРј lock-free
     *
     * @param value Р·РЅР°С‡РµРЅРёРµ РєР»СЋС‡Р°
     * @return false РµСЃР»Рё value СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓРµС‚ РІ РјРЅРѕР¶РµСЃС‚РІРµ, true РµСЃР»Рё СЌР»РµРјРµРЅС‚ Р±С‹Р» РґРѕР±Р°РІР»РµРЅ
     */
    fun add(value: T): Boolean {
        while (true) {
            val (pred, curr) = find(value)
            if (curr.data != null && curr.data == value) {
                return false
            } else {
                val newNode = Node(null, value)
                newNode.next.set(curr, NOT_DELETED)
                val oldVersion = pred.version
                if (pred.next.compareAndSet(curr, newNode, oldVersion * 2, (oldVersion + 1) * 2)) {
                    return true
                }
            }
        }
    }


    /**
     * РЈРґР°Р»РёС‚СЊ РєР»СЋС‡ РёР· РјРЅРѕР¶РµСЃС‚РІР°
     *
     * РђР»РіРѕСЂРёС‚Рј РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєР°Рє РјРёРЅРёРјСѓРј lock-free
     *
     * @param value Р·РЅР°С‡РµРЅРёРµ РєР»СЋС‡Р°
     * @return false РµСЃР»Рё РєР»СЋС‡ РЅРµ Р±С‹Р» РЅР°Р№РґРµРЅ, true РµСЃР»Рё РєР»СЋС‡ СѓСЃРїРµС€РЅРѕ СѓРґР°Р»РµРЅ
     */
    fun remove(value: T): Boolean {
        while (true) {
            val (pred, curr) = find(value)
            if (curr.data != value) {
                return false
            } else {
                val succ = curr.next.reference
                val currVersion = curr.version
                val predVersion = pred.version
                if (curr.next.compareAndSet(succ, succ, currVersion * 2, (currVersion + 1) * 2 + 1)) {
                    pred.next.compareAndSet(curr, succ, predVersion * 2, (predVersion + 1) * 2)
                    return true
                }
            }
        }
    }


    /**
     * РџСЂРѕРІРµСЂРєР° РЅР°Р»РёС‡РёСЏ РєР»СЋС‡Р° РІ РјРЅРѕР¶РµСЃС‚РІРµ
     *
     * РђР»РіРѕСЂРёС‚Рј РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєР°Рє РјРёРЅРёРјСѓРј wait-free
     *
     * @param value Р·РЅР°С‡РµРЅРёРµ РєР»СЋС‡Р°
     * @return true РµСЃР»Рё СЌР»РµРјРµРЅС‚ СЃРѕРґРµСЂР¶РёС‚СЃСЏ РІ РјРЅРѕР¶РµСЃС‚РІРµ, РёРЅР°С‡Рµ - false
     */
    operator fun contains(value: T): Boolean {
        var curr = head.next.reference
        while (true) {
            val succ = curr.next.reference
            if (curr.next.stamp % 2 == DELETED) {
                if (curr.data == value) {
                    return false
                }
                curr = succ
            } else {
                val data = curr.data
                if (data == value) {
                    return true
                }
                if (data == null || data > value) {
                    return false
                } else {
                    curr = succ
                }
            }
        }
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ lock-free РёС‚РµСЂР°С‚РѕСЂ РґР»СЏ РјРЅРѕР¶РµСЃС‚РІР°
     *
     * @return РЅРѕРІС‹Р№ СЌРєР·РµРјРїР»СЏСЂ РёС‚РµСЂР°С‚РѕСЂ РґР»СЏ РјРЅРѕР¶РµСЃС‚РІР°
     */
    operator fun iterator(): Iterator<T> {
        return makeSnapshot().listIterator()
    }

    fun print() {
        var curr = head.next.reference
        while (true) {
            val data = curr.data
            if (data == null) {
                print("--\n")
                return
            } else {
                print("--$data")
            }
            curr = curr.next.reference
        }
    }

    private fun find(value: T): Pair<Node<T>, Node<T>> {
        while (true) {
            var pred = head
            var curr = pred.next.reference
            while (true) {
                val succ = curr.next.reference
                if (curr.next.stamp % 2 == DELETED) {
                    val predVersion = pred.version
                    if (!pred.next.compareAndSet(curr, succ, predVersion * 2, predVersion * 2)) {
                        break
                    }
                    curr = succ
                } else {
                    val data = curr.data
                    if (data == null || data >= value) {
                        return Pair(pred, curr)
                    } else {
                        pred = curr
                        curr = succ
                    }
                }
            }

        }
    }

    private fun makeSnapshot(): List<T> {
        var first = unSafeSnapshot()
        while (true) {
            val second = unSafeSnapshot()
            if (first == second) {
                return second.map { it.second }
            } else {
                first = second
            }
        }
    }

    private fun unSafeSnapshot(): List<Pair<Int, T>> {
        val snapshot = mutableListOf<Pair<Int, T>>()
        var curr = head.next.reference
        while (true) {
            val data = curr.data
            if (data == null) {
                return snapshot
            } else if (curr.next.stamp % 2 == NOT_DELETED) {
                snapshot.add(Pair(curr.version, data))
            }
            curr = curr.next.reference
        }
    }


    private class Node<T>(nextNode: Node<T>?, val data: T?) {
        val next: AtomicStampedReference<Node<T>> = AtomicStampedReference<Node<T>>(nextNode, NOT_DELETED)
        val version: Int
            get() = next.stamp / 2
    }

    companion object {
        const val DELETED = 1
        const val NOT_DELETED = 0
    }
}