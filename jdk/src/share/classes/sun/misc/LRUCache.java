/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;

/**
 * Utility class for small LRU caches.
 *
 * @author Mark Reinhold
 */
public abstract class LRUCache<N,V> {

    private V[] oa = null;
    private final int size;

    public LRUCache(int size) {
        this.size = size;
    }

    abstract protected V create(N name);

    abstract protected boolean hasName(V ob, N name);

    public static void moveToFront(Object[] oa, int i) {
        Object ob = oa[i];
        for (int j = i; j > 0; j--)
            oa[j] = oa[j - 1];
        oa[0] = ob;
    }

    public V forName(N name) {
        if (oa == null) {
            oa = (V[])new Object[size];
        } else {
            for (int i = 0; i < oa.length; i++) {
                V ob = oa[i];
                if (ob == null)
                    continue;
                if (hasName(ob, name)) {
                    if (i > 0)
                        moveToFront(oa, i);
                    return ob;
                }
            }
        }

        // Create a new object
        V ob = create(name);
        oa[oa.length - 1] = ob;
        moveToFront(oa, oa.length - 1);
        return ob;
    }

}
