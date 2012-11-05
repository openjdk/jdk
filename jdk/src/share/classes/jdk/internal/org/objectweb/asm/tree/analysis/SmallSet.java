/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.tree.analysis;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A set of at most two elements.
 *
 * @author Eric Bruneton
 */
class SmallSet<E> extends AbstractSet<E> implements Iterator<E> {

    // if e1 is null, e2 must be null; otherwise e2 must be different from e1

    E e1, e2;

    static final <T> Set<T> emptySet() {
        return new SmallSet<T>(null, null);
    }

    SmallSet(final E e1, final E e2) {
        this.e1 = e1;
        this.e2 = e2;
    }

    // -------------------------------------------------------------------------
    // Implementation of inherited abstract methods
    // -------------------------------------------------------------------------

    @Override
    public Iterator<E> iterator() {
        return new SmallSet<E>(e1, e2);
    }

    @Override
    public int size() {
        return e1 == null ? 0 : (e2 == null ? 1 : 2);
    }

    // -------------------------------------------------------------------------
    // Implementation of the Iterator interface
    // -------------------------------------------------------------------------

    public boolean hasNext() {
        return e1 != null;
    }

    public E next() {
        if (e1 == null) {
            throw new NoSuchElementException();
        }
        E e = e1;
        e1 = e2;
        e2 = null;
        return e;
    }

    public void remove() {
    }

    // -------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------

    Set<E> union(final SmallSet<E> s) {
        if ((s.e1 == e1 && s.e2 == e2) || (s.e1 == e2 && s.e2 == e1)) {
            return this; // if the two sets are equal, return this
        }
        if (s.e1 == null) {
            return this; // if s is empty, return this
        }
        if (e1 == null) {
            return s; // if this is empty, return s
        }
        if (s.e2 == null) { // s contains exactly one element
            if (e2 == null) {
                return new SmallSet<E>(e1, s.e1); // necessarily e1 != s.e1
            } else if (s.e1 == e1 || s.e1 == e2) { // s is included in this
                return this;
            }
        }
        if (e2 == null) { // this contains exactly one element
            // if (s.e2 == null) { // cannot happen
            // return new SmallSet(e1, s.e1); // necessarily e1 != s.e1
            // } else
            if (e1 == s.e1 || e1 == s.e2) { // this in included in s
                return s;
            }
        }
        // here we know that there are at least 3 distinct elements
        HashSet<E> r = new HashSet<E>(4);
        r.add(e1);
        if (e2 != null) {
            r.add(e2);
        }
        r.add(s.e1);
        if (s.e2 != null) {
            r.add(s.e2);
        }
        return r;
    }
}
