/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 4473440
 * @summary iterators on collection views of empty map weren't fail-fast.
 */

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EmptyMapIterator {
    public static void main(String[] args) throws Exception {
        testStringKeys();
        testIntegerKeys();
    }

    private static void testStringKeys() throws Exception {
        HashMap map = new HashMap();
        Iterator iter = map.entrySet().iterator();
        map.put("key", "value");
        expectConcurrentModification(iter);
    }

    private static void testIntegerKeys() throws Exception {
        HashMap<Integer,Integer> map = new HashMap<>();
        Iterator<Map.Entry<Integer,Integer>> iter = map.entrySet().iterator();
        map.put(Integer.valueOf(1), Integer.valueOf(2));
        expectConcurrentModification(iter);
    }

    private static void expectConcurrentModification(Iterator<?> iter) throws Exception {
        try {
            iter.next();
            throw new Exception("No exception thrown");
        } catch (ConcurrentModificationException e) {
        }
    }
}
