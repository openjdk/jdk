/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5017904 6356890
 * @summary Test empty iterators, enumerations, and collections
 */

import java.util.*;
import static java.util.Collections.*;

public class EmptyIterator {

    void test(String[] args) throws Throwable {
        testEmptyCollection(Collections.<Object>emptyList());
        testEmptyCollection(Collections.<Object>emptySet());
        testEmptyCollection(new java.util.concurrent.
                            SynchronousQueue<Object>());

        testEmptyMap(Collections.<Object, Object>emptyMap());

        Hashtable<Object, Object> emptyTable = new Hashtable<Object, Object>();
        testEmptyEnumeration(emptyTable.keys());
        testEmptyEnumeration(emptyTable.elements());
        testEmptyIterator(emptyTable.keySet().iterator());
        testEmptyIterator(emptyTable.values().iterator());
        testEmptyIterator(emptyTable.entrySet().iterator());

        testEmptyEnumeration(javax.swing.tree.DefaultMutableTreeNode
                             .EMPTY_ENUMERATION);
        testEmptyEnumeration(javax.swing.text.SimpleAttributeSet
                             .EMPTY.getAttributeNames());

        @SuppressWarnings("unchecked") Iterator<?> x =
            new sun.tools.java.MethodSet()
            .lookupName(sun.tools.java.Identifier.lookup(""));
        testEmptyIterator(x);
    }

    <T> void testEmptyEnumeration(final Enumeration<T> e) {
        check(e == emptyEnumeration());
        check(! e.hasMoreElements());
        THROWS(NoSuchElementException.class,
               new F(){void f(){ e.nextElement(); }});
    }

    <T> void testEmptyIterator(final Iterator<T> it) {
        check(it == emptyIterator());
        check(! it.hasNext());
        THROWS(NoSuchElementException.class,
               new F(){void f(){ it.next(); }});
        THROWS(IllegalStateException.class,
               new F(){void f(){ it.remove(); }});
    }

    void testEmptyMap(Map<Object, Object> m) {
        check(m == emptyMap());
        check(m.entrySet().iterator() ==
              Collections.<Map.Entry<Object,Object>>emptyIterator());
        check(m.values().iterator() == emptyIterator());
        check(m.keySet().iterator() == emptyIterator());
        equal(m, unmodifiableMap(m));

        testEmptyCollection(m.keySet());
        testEmptyCollection(m.entrySet());
        testEmptyCollection(m.values());
    }

    <E> void testToArray(final Collection<E> c) {
        Object[] a = c.toArray();
        equal(a.length, 0);
        equal(a.getClass().getComponentType(), Object.class);
        THROWS(NullPointerException.class,
               new F(){void f(){ c.toArray((Object[])null); }});

        {
            String[] t = new String[0];
            check(c.toArray(t) == t);
        }

        {
            String[] t = nCopies(10, "").toArray(new String[0]);
            check(c.toArray(t) == t);
            check(t[0] == null);
            for (int i=1; i<t.length; i++)
                check(t[i] == "");
        }
    }

    <E> void testEmptyCollection(final Collection<E> c) {
        testEmptyIterator(c.iterator());

        check(c.iterator() == emptyIterator());
        if (c instanceof List)
            check(((List<?>)c).listIterator() == emptyListIterator());

        testToArray(c);
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
        new EmptyIterator().instanceMain(args);}
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
