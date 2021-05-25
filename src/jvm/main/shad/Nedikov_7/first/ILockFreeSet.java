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

/**
 * Lock-Free множество.
 * @param <T> Тип ключей
 */
public interface ILockFreeSet<T extends Comparable<T>> extends Iterable<T> {
  /**
   * Добавить ключ к множеству
   *
   * Алгоритм должен быть как минимум lock-free
   *
   * @param value значение ключа
   * @return false если value уже существует в множестве, true если элемент был добавлен
   */
  boolean add(T value);


  /**
   * Удалить ключ из множества
   *
   * Алгоритм должен быть как минимум lock-free
   *
   * @param value значение ключа
   * @return false если ключ не был найден, true если ключ успешно удален
   */
  boolean remove(T value);


  /**
   * Проверка наличия ключа в множестве
   *
   * Алгоритм должен быть как минимум wait-free
   *
   * @param value значение ключа
   * @return true если элемент содержится в множестве, иначе - false
   */
  boolean contains(T value);


  /**
   * Проверка множества на пустоту
   *
   * Алгоритм должен быть как минимум lock-free
   *
   * @return true если множество пусто, иначе - false
   */
  boolean isEmpty();

  /**
   * Возвращает lock-free итератор для множества
   *
   * @return новый экземпляр итератор для множества
   */
  java.util.Iterator<T> iterator();
}