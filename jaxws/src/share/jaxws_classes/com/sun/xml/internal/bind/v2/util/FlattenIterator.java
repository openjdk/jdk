/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.util;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * {@link Iterator} that walks over a map of maps.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.0
 */
public final class FlattenIterator<T> implements Iterator<T> {

    private final Iterator<? extends Map<?,? extends T>> parent;
    private Iterator<? extends T> child = null;
    private T next;

    public FlattenIterator( Iterable<? extends Map<?,? extends T>> core ) {
        this.parent = core.iterator();
    }


    public void remove() {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext() {
        getNext();
        return next!=null;
    }

    public T next() {
        T r = next;
        next = null;
        if(r==null)
            throw new NoSuchElementException();
        return r;
    }

    private void getNext() {
        if(next!=null)  return;

        if(child!=null && child.hasNext()) {
            next = child.next();
            return;
        }
        // child is empty
        if(parent.hasNext()) {
            child = parent.next().values().iterator();
            getNext();
        }
        // else
        //      no more object
    }
}
