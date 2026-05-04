/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.net.http;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An {@link Iterator} clone supporting checked exceptions.
 *
 * @param <E> the type of elements returned by this iterator
 */
interface CheckedIterator<E> {

    /**
     * {@return {@code true} if the iteration has more elements}
     * @throws Exception if operation fails
     */
    boolean hasNext() throws Exception;

    /**
     * {@return the next element in the iteration}
     *
     * @throws NoSuchElementException if the iteration has no more elements
     * @throws Exception if operation fails
     */
    E next() throws Exception;

    static <E> CheckedIterator<E> fromIterator(Iterator<E> iterator) {
        return new CheckedIterator<>() {

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public E next() {
                return iterator.next();
            }

        };
    }

}
