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

package shad.Nedikov_7.first;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeSet<T extends Comparable<T>> implements ILockFreeSet<T> {
  private SetNode<T> head = new SetNode<>();
  private AtomicInteger listSize = new AtomicInteger(0);

  @Override
  public boolean add(T value) {
    if (value == null) return false;
    SetNode<T> valNode = new SetNode<>(value);
    SetNode<T> node = head;
    listSize.incrementAndGet();
    while (!node.next.compareAndSet(null, valNode)) {
      node = node.next.get();
      if (node.has(value)) {
        listSize.decrementAndGet();
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean remove(T value) {
    if (value == null) return false;
    SetNode<T> node = head.next.get();
    while (node != null) {
      if (node.elem.compareTo(value) == 0) {
        if (node.deleted.compareAndSet(false, true)) {
          return true;
        }
      }
      node = nextNode(node);
    }
    return false;
  }

  @Override
  public boolean contains(T value) {
    if (value == null) return false;
    int n = listSize.get();
    SetNode<T> node = head.next.get();
    for (int i = 0; node != null && i < n; i++) {
      if (node.has(value)) {
        return true;
      }
      node = nextNode(node);
    }
    return false;
  }

  // delete deleted nodes
  private SetNode<T> nextNode(SetNode<T> node) {
    SetNode<T> nodeNext = node.next.get();
    if (nodeNext == null) return null;
    if (!nodeNext.deleted.get()) return nodeNext;
    if (node.next.compareAndSet(nodeNext, nodeNext.next.get())) {
      listSize.decrementAndGet();
    }
    return node.next.get();
  }

  @Override
  public boolean isEmpty() {
    return !iterator().hasNext();
  }

  @Override
  public Iterator<T> iterator() {
    List<SnapNode<T>> prev = snapshot();
    while (true) {
      List<SnapNode<T>> cur = snapshot();
      if (compareSnapshots(prev, cur)) {
        return cleanupSnapshot(prev).iterator();
      }
      prev = cur;
    }
  }

  private List<T> cleanupSnapshot(List<SnapNode<T>> a) {
    List<T> result = new ArrayList<T>();
    for (SnapNode<T> x : a) {
      if (!x.deleted) {
        result.add(x.link.elem);
      }
    }
    return result;
  }

  private boolean compareSnapshots(List<SnapNode<T>> a, List<SnapNode<T>> b) {
    int n = a.size();
    if (n != b.size()) return false;
    for (int i = 0; i < n; i++) {
      if (!a.get(i).eq(b.get(i))) return false;
    }
    return true;
  }

  private List<SnapNode<T>> snapshot() {
    List<SnapNode<T>> result = new ArrayList<SnapNode<T>>();
    SetNode<T> node = head.next.get();
    while (node != null) {
      result.add(node.copyLW());
      node = node.next.get();
    }
    return result;
  }


  private static class SetNode<T extends Comparable<T>> {
    AtomicReference<SetNode<T>> next = new AtomicReference<>(null); // всегда указывает на следующего соседа или на null
    AtomicBoolean deleted = new AtomicBoolean(false); // меняется только с false на true, но не обратно
    final T elem; // null только в голове

    SetNode(T elem) {
      this.elem = elem;
    }

    SetNode() {
      this(null);
    }

    SnapNode<T> copyLW() {
      return new SnapNode<T>(this, deleted.get());
    }

    boolean has(T value) {
      return !deleted.get() && elem.compareTo(value) == 0;
    }
  }


  private static class SnapNode<T extends Comparable<T>> {
    final boolean deleted;
    final SetNode<T> link;

    SnapNode(SetNode<T> link, boolean deleted) {
      this.link = link;
      this.deleted = deleted;
    }

    boolean eq(SnapNode<T> node) {
      return deleted == node.deleted && link == node.link;
    }
  }
}
