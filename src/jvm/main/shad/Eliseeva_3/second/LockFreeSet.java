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

package shad.Eliseeva_3.second;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.stream.Collectors;

public class LockFreeSet<T extends Comparable<T>> implements LockFreeSetInterface<T> {
    private Node root = new Node(null);

    @Override
    public boolean add(T value) {
        Node node = new Node(value);
        while (true) {
            FindResult findResult = find(value);
            Node previousNode = findResult.previousNode;
            Node nextNode = findResult.nextNode;
            if (nextNode != null) {
                return false;
            }
            if (previousNode.next.compareAndSet(nextNode, node, Node.NOT_MARKED, Node.NOT_MARKED)) {
                return true;
            }
        }
    }

    private FindResult find(T value) {
        FindResult result = new FindResult();
        result.nextNode = root;
        while (result.nextNode != null) {
            boolean[] isMarked = new boolean[1];
            Node nextNode = result.nextNode.next.get(isMarked);
            result.previousNode = result.nextNode;
            result.nextNode = nextNode;
            if (isMarked[0] != Node.MARKED && (nextNode == null || nextNode.nodeValue.compareTo(value) == 0)) {
                return result;
            }
        }
        return result;
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            FindResult findResult = find(value);
            Node previousNode = findResult.previousNode;
            Node currentNode = findResult.nextNode;
            if (currentNode == null) {
                return false;
            }
            Node next = currentNode.next.getReference();
            if (currentNode.next.compareAndSet(next, next, Node.NOT_MARKED, Node.MARKED)) {
                previousNode.next.compareAndSet(currentNode, next, Node.NOT_MARKED, Node.NOT_MARKED);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        return find(value).nextNode != null;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    public Iterator<T> iterator() {
        return snapshot().iterator();
    }

    private class Node {
        private final T nodeValue;

        private static final boolean MARKED = true;
        private static final boolean NOT_MARKED = false;

        private AtomicMarkableReference<Node> next = new AtomicMarkableReference<>(null, NOT_MARKED);


        private Node(T nodeValue) {
            this.nodeValue = nodeValue;
        }
    }

    private class FindResult {
        private Node nextNode = null;
        private Node previousNode = null;
    }

    private List<T> snapshot() {
        while (true) {
            List<Node> list1 = getElements();
            List<Node> list2 = getElements();
            if (list1.size() != list2.size()) {
                 continue;
            }
            boolean isEqual = true;
            for (int i = 0; i < list1.size(); i++) {
                if (list1.get(i) != list2.get(i)) {
                    isEqual = false;
                    break;
                }
            }
            if (isEqual) {
                return list1.stream().map(x -> x.nodeValue).sorted().collect(Collectors.toList());
            }
        }
    }

    private List<Node> getElements() {
        List<Node> answer = new ArrayList<>();
        Node curNode = root;
        boolean[] isMarked = new boolean[1];
        Node nextNode;
        while ((nextNode = curNode.next.get(isMarked)) != null) {
            if (isMarked[0] == Node.MARKED) {
                return getElements();
            }
            curNode = nextNode;
            answer.add(curNode);
        }
        return answer;
    }
}
