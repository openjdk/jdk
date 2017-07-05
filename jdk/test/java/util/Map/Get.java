/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6306829
 * @summary Verify assertions in get() javadocs
 * @author Martin Buchholz
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Get {

    private static void realMain(String[] args) throws Throwable {
        testMap(new Hashtable<Character,Boolean>());
        testMap(new HashMap<Character,Boolean>());
        testMap(new IdentityHashMap<Character,Boolean>());
        testMap(new LinkedHashMap<Character,Boolean>());
        testMap(new ConcurrentHashMap<Character,Boolean>());
        testMap(new WeakHashMap<Character,Boolean>());
        testMap(new TreeMap<Character,Boolean>());
        testMap(new ConcurrentSkipListMap<Character,Boolean>());
    }

    private static void put(Map<Character,Boolean> m,
                            Character key, Boolean value,
                            Boolean oldValue) {
        if (oldValue != null) {
            check(m.containsValue(oldValue));
            check(m.values().contains(oldValue));
        }
        equal(m.put(key, value), oldValue);
        equal(m.get(key), value);
        check(m.containsKey(key));
        check(m.keySet().contains(key));
        check(m.containsValue(value));
        check(m.values().contains(value));
        check(! m.isEmpty());
    }

    private static void testMap(Map<Character,Boolean> m) {
        // We verify following assertions in get(Object) method javadocs
        boolean permitsNullKeys = (! (m instanceof ConcurrentMap ||
                                      m instanceof Hashtable     ||
                                      m instanceof SortedMap));
        boolean permitsNullValues = (! (m instanceof ConcurrentMap ||
                                        m instanceof Hashtable));
        boolean usesIdentity = m instanceof IdentityHashMap;

        System.out.println(m.getClass());
        put(m, 'A', true,  null);
        put(m, 'A', false, true);       // Guaranteed identical by JLS
        put(m, 'B', true,  null);
        put(m, new Character('A'), false, usesIdentity ? null : false);
        if (permitsNullKeys) {
            try {
                put(m, null, true,  null);
                put(m, null, false, true);
            }
            catch (Throwable t) { unexpected(t); }
        } else {
            try { m.get(null); fail(); }
            catch (NullPointerException e) {}
            catch (Throwable t) { unexpected(t); }

            try { m.put(null, true); fail(); }
            catch (NullPointerException e) {}
            catch (Throwable t) { unexpected(t); }
        }
        if (permitsNullValues) {
            try {
                put(m, 'C', null, null);
                put(m, 'C', true, null);
                put(m, 'C', null, true);
            }
            catch (Throwable t) { unexpected(t); }
        } else {
            try { m.put('A', null); fail(); }
            catch (NullPointerException e) {}
            catch (Throwable t) { unexpected(t); }

            try { m.put('C', null); fail(); }
            catch (NullPointerException e) {}
            catch (Throwable t) { unexpected(t); }
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() { passed++; }
    static void fail() { failed++; Thread.dumpStack(); }
    static void fail(String msg) { System.out.println(msg); fail(); }
    static void unexpected(Throwable t) { failed++; t.printStackTrace(); }
    static void check(boolean cond) { if (cond) pass(); else fail(); }
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else {System.out.println(x + " not equal to " + y); fail(); }}

    public static void main(String[] args) throws Throwable {
        try { realMain(args); } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Exception("Some tests failed");
    }
}
