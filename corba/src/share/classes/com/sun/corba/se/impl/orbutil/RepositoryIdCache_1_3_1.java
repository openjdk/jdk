/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.util.Stack;
import java.util.Hashtable;
import java.util.EmptyStackException;
import java.util.Enumeration;

// Really limited pool - in this case just creating several at a time...
class RepositoryIdPool_1_3_1 extends Stack {

    private static int MAX_CACHE_SIZE = 4;
    private RepositoryIdCache_1_3_1 cache;

    public final synchronized RepositoryId_1_3_1 popId() {

        try {
            return (RepositoryId_1_3_1)super.pop();
        }
        catch(EmptyStackException e) {
            increasePool(5);
            return (RepositoryId_1_3_1)super.pop();
        }

    }

    // Pool management
    final void increasePool(int size) {
        //if (cache.size() <= MAX_CACHE_SIZE)
        for (int i = size; i > 0; i--)
            push(new RepositoryId_1_3_1());
        /*
          // _REVISIT_ This will not work w/out either thread tracing or weak references.  I am
          // betting that thread tracing almost completely negates benefit of reuse.  Until either
          // 1.2 only inclusion or proof to the contrary, I'll leave it this way...
          else {
          int numToReclaim = cache.size() / 2;
          Enumeration keys = cache.keys();
          Enumeration elements = cache.elements();
          for (int i = numToReclaim; i > 0; i--) {
          Object key = keys.nextElement();
          Object element = elements.nextElement();

          push(element);
          cache.remove(key);
          }
          }
        */
    }

    final void setCaches(RepositoryIdCache_1_3_1 cache) {
        this.cache = cache;
    }

}

public class RepositoryIdCache_1_3_1 extends Hashtable {

    private RepositoryIdPool_1_3_1 pool = new RepositoryIdPool_1_3_1();

    public RepositoryIdCache_1_3_1() {
        pool.setCaches(this);
    }

    public final synchronized RepositoryId_1_3_1 getId(String key) {
        RepositoryId_1_3_1 repId = (RepositoryId_1_3_1)super.get(key);

        if (repId != null)
            return repId;
        else {
            //repId = pool.popId().init(key);
            repId = new RepositoryId_1_3_1(key);
            put(key, repId);
            return repId;
        }

    }
}
