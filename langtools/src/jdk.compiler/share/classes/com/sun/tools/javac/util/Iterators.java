/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/** Utilities for Iterators.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Iterators {

    public static <I, O> Iterator<O> createCompoundIterator(Iterable<I> inputs, Function<I, Iterator<O>> convertor) {
        return new CompoundIterator<>(inputs, convertor);
    }

    private static class CompoundIterator<I, O> implements Iterator<O> {

        private final Iterator<I> inputs;
        private final Function<I, Iterator<O>> convertor;
        private Iterator<O> currentIterator;

        public CompoundIterator(Iterable<I> inputs, Function<I, Iterator<O>> convertor) {
            this.inputs = inputs.iterator();
            this.convertor = convertor;
        }

        public boolean hasNext() {
            while (inputs.hasNext() && (currentIterator == null || !currentIterator.hasNext())) {
                currentIterator = convertor.apply(inputs.next());
            }
            return currentIterator != null && currentIterator.hasNext();
        }

        public O next() {
            if (!hasNext())
                throw new NoSuchElementException();

            return currentIterator.next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
