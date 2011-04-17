/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6499848
 * @ignore until 6842353 is resolved
 * @summary Check that iterators work properly in the presence of
 *          concurrent finalization and removal of elements.
 */

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class GCDuringIteration {
    private static void waitForFinalizersToRun() {
        for (int i = 0; i < 2; i++)
            tryWaitForFinalizersToRun();
    }

    private static void tryWaitForFinalizersToRun() {
        System.gc();
        final CountDownLatch fin = new CountDownLatch(1);
        new Object() { protected void finalize() { fin.countDown(); }};
        System.gc();
        try { fin.await(); }
        catch (InterruptedException ie) { throw new Error(ie); }
    }

    // A class with the traditional pessimal hashCode implementation,
    // to ensure that all instances end up in the same bucket.
    static class Foo { public int hashCode() { return 42; }}

    <K,V> void put(Map<K,V> map, K k, V v) {
        check(! map.containsKey(k));
        equal(map.get(k), null);
        equal(map.put(k, v), null);
        equal(map.get(k), v);
        check(map.containsKey(k));
        equal(map.put(k, v), v);
        equal(map.get(k), v);
        check(map.containsKey(k));
        check(! map.isEmpty());
        equal(map.keySet().iterator().next(), k);
        equal(map.values().iterator().next(), v);
    }

    void checkIterator(final Iterator<Map.Entry<Foo, Integer>> it, int first) {
        final Random rnd = new Random();
        for (int i = first; i >= 0; --i) {
            if (rnd.nextBoolean()) check(it.hasNext());
            equal(it.next().getValue(), i);
        }
        if (rnd.nextBoolean())
            THROWS(NoSuchElementException.class,
                   new F(){void f(){it.next();}});
        if (rnd.nextBoolean())
            check(! it.hasNext());
    }

    <K,V> V firstValue(Map<K,V> map) {
        return map.values().iterator().next();
    }

    void test(String[] args) throws Throwable {
        final int n = 10;
        // Create array of strong refs
        final Foo[] foos = new Foo[2*n];
        final Map<Foo,Integer> map = new WeakHashMap<Foo,Integer>(foos.length);
        check(map.isEmpty());
        equal(map.size(), 0);

        for (int i = 0; i < foos.length; i++) {
            Foo foo = new Foo();
            foos[i] = foo;
            put(map, foo, i);
        }
        equal(map.size(), foos.length);

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            foos[first] = null;
            for (int i = 0; i < 10 && map.size() != first; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first);
            checkIterator(it, first-1);
            equal(map.size(), first);
            equal(firstValue(map), first-1);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.next();          // protects first entry
            System.out.println(map.values());
            foos[first] = null;
            tryWaitForFinalizersToRun()
            equal(map.size(), first+1);
            System.out.println(map.values());
            checkIterator(it, first-1);
            // first entry no longer protected
            for (int i = 0; i < 10 && map.size() != first; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first);
            equal(firstValue(map), first-1);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.next();          // protects first entry
            System.out.println(map.values());
            foos[first] = foos[first-1] = null;
            tryWaitForFinalizersToRun();
            equal(map.size(), first);
            equal(firstValue(map), first);
            System.out.println(map.values());
            checkIterator(it, first-2);
            // first entry no longer protected
            for (int i = 0; i < 10 && map.size() != first-1; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first-1);
            equal(firstValue(map), first-2);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.next();          // protects first entry
            it.hasNext();       // protects second entry
            System.out.println(map.values());
            foos[first] = foos[first-1] = null;
            tryWaitForFinalizersToRun();
            equal(firstValue(map), first);
            equal(map.size(), first+1);
            System.out.println(map.values());
            checkIterator(it, first-1);
            // first entry no longer protected
            for (int i = 0; i < 10 && map.size() != first-1; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first-1);
            equal(firstValue(map), first-2);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.next();          // protects first entry
            System.out.println(map.values());
            foos[first] = foos[first-1] = null;
            tryWaitForFinalizersToRun();
            it.remove();
            equal(firstValue(map), first-2);
            equal(map.size(), first-1);
            System.out.println(map.values());
            checkIterator(it, first-2);
            // first entry no longer protected
            for (int i = 0; i < 10 && map.size() != first-1; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first-1);
            equal(firstValue(map), first-2);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.next();          // protects first entry
            it.remove();
            it.hasNext();       // protects second entry
            System.out.println(map.values());
            foos[first] = foos[first-1] = null;
            tryWaitForFinalizersToRun();
            equal(firstValue(map), first-1);
            equal(map.size(), first);
            System.out.println(map.values());
            checkIterator(it, first-1);
            for (int i = 0; i < 10 && map.size() != first-1; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), first-1);
            equal(firstValue(map), first-2);
        }

        {
            int first = firstValue(map);
            final Iterator<Map.Entry<Foo,Integer>> it = map.entrySet().iterator();
            it.hasNext();       // protects first entry
            Arrays.fill(foos, null);
            tryWaitForFinalizersToRun();
            equal(map.size(), 1);
            System.out.println(map.values());
            equal(it.next().getValue(), first);
            check(! it.hasNext());
            for (int i = 0; i < 10 && map.size() != 0; i++)
                tryWaitForFinalizersToRun();
            equal(map.size(), 0);
            check(map.isEmpty());
        }
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new GCDuringIteration().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    abstract class F {abstract void f() throws Throwable;}
    void THROWS(Class<? extends Throwable> k, F... fs) {
        for (F f : fs)
            try {f.f(); fail("Expected " + k.getName() + " not thrown");}
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
}
