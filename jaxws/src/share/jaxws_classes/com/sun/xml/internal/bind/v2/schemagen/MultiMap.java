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

package com.sun.xml.internal.bind.v2.schemagen;

import java.util.TreeMap;
import java.util.Map;

/**
 * A special {@link Map} that 'conceptually' stores a set of values for each key.
 *
 * <p>
 * When multiple values are stored, however, this class doesn't let the caller
 * see individual values, and instead it returns a specially designated "MANY" value,
 * which is given as a parameter to the constructor.
 *
 * @author Kohsuke Kawaguchi
 */
final class MultiMap<K extends Comparable<K>,V> extends TreeMap<K,V> {
    private final V many;

    public MultiMap(V many) {
        this.many = many;
    }

    @Override
    public V put(K key, V value) {
        V old = super.put(key, value);
        if(old!=null && !old.equals(value)) {
            // different value stored
            super.put(key,many);
        }
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }
}
