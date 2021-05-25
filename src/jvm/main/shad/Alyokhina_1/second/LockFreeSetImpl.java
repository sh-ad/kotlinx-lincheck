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

package shad.Alyokhina_1.second;

import java.util.*;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.stream.Collectors;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private AtomicMarkableReference<Node> head = new AtomicMarkableReference<>(new Node(null, null), false);

    @Override
    public boolean add(T value) {
        Node node = new Node(value, null);
        while (true) {
            Map.Entry<Node, Node> prevAndCur = find(value);
            if (prevAndCur.getValue() != null) {
                return false;
            }
            if (prevAndCur.getKey().next.compareAndSet(null, node, false, false)) {
                return true;
            }
        }

    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Map.Entry<Node, Node> prevAndCurrent = find(value);
            final Node pred = prevAndCurrent.getKey();
            final Node cur = prevAndCurrent.getValue();
            if (cur != null) {
                Node next = cur.next.getReference();
                if (cur.next.compareAndSet(next, next, false, true)) {
                    pred.next.compareAndSet(cur, next, false, false);
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        return find(value).getValue() != null;
    }

    @Override
    public boolean isEmpty() {
        AtomicMarkableReference<Node> cur = head.getReference().next;
        while (cur.getReference() != null) {
            if (!cur.isMarked()) {
                return false;
            }
            cur = cur.getReference().next;
        }
        return true;
    }


    @Override
    public Iterator<T> iterator() {
        while (true) {
            List<Node> firstSnapshot = getValues();
            List<Node> secondSnapshot = getValues();
            if (firstSnapshot.equals(secondSnapshot)) {
                return secondSnapshot.stream().map(n -> n.value).collect(Collectors.toList()).iterator();
            }
        }
    }

    private Map.Entry<Node, Node> find(T value) {
        Node pred = head.getReference();
        Node cur = pred.next.getReference();
        while (cur != null) {
            if (!cur.next.isMarked() && value.equals(cur.value)) {
                return new AbstractMap.SimpleEntry<>(pred, cur);
            }
            pred = cur;
            cur = cur.next.getReference();
        }
        return new AbstractMap.SimpleEntry<>(pred, null);
    }


    private List<Node> getValues() {
        final List<Node> res = new ArrayList<>();
        Node cur = head.getReference().next.getReference();
        while (cur != null) {
            if (!cur.next.isMarked()) {
                res.add(cur);
            }
            cur = cur.next.getReference();
        }
        return res;
    }

    private class Node {
        private T value;
        private AtomicMarkableReference<Node> next;

        private Node(T value, AtomicMarkableReference<Node> next) {
            this.value = value;
            this.next = next == null ?
                    new AtomicMarkableReference<>(null, false)
                    : next;

        }

    }
}
