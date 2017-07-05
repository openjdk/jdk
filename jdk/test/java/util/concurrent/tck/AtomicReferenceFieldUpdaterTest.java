/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AtomicReferenceFieldUpdaterTest extends JSR166TestCase {
    volatile Integer x = null;
    protected volatile Integer protectedField;
    private volatile Integer privateField;
    Object z;
    Integer w;
    volatile int i;

    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(AtomicReferenceFieldUpdaterTest.class);
    }

    // for testing subclass access
    static class AtomicReferenceFieldUpdaterTestSubclass extends AtomicReferenceFieldUpdaterTest {
        public void checkPrivateAccess() {
            try {
                AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest,Integer> a =
                    AtomicReferenceFieldUpdater.newUpdater
                    (AtomicReferenceFieldUpdaterTest.class, Integer.class, "privateField");
                shouldThrow();
            } catch (RuntimeException success) {
                assertNotNull(success.getCause());
            }
        }

        public void checkCompareAndSetProtectedSub() {
            AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest,Integer> a =
                AtomicReferenceFieldUpdater.newUpdater
                (AtomicReferenceFieldUpdaterTest.class, Integer.class, "protectedField");
            this.protectedField = one;
            assertTrue(a.compareAndSet(this, one, two));
            assertTrue(a.compareAndSet(this, two, m4));
            assertSame(m4, a.get(this));
            assertFalse(a.compareAndSet(this, m5, seven));
            assertFalse(seven == a.get(this));
            assertTrue(a.compareAndSet(this, m4, seven));
            assertSame(seven, a.get(this));
        }
    }

    static class UnrelatedClass {
        public void checkPackageAccess(AtomicReferenceFieldUpdaterTest obj) {
            obj.x = one;
            AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest,Integer> a =
                AtomicReferenceFieldUpdater.newUpdater
                (AtomicReferenceFieldUpdaterTest.class, Integer.class, "x");
            assertSame(one, a.get(obj));
            assertTrue(a.compareAndSet(obj, one, two));
            assertSame(two, a.get(obj));
        }

        public void checkPrivateAccess(AtomicReferenceFieldUpdaterTest obj) {
            try {
                AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest,Integer> a =
                    AtomicReferenceFieldUpdater.newUpdater
                    (AtomicReferenceFieldUpdaterTest.class, Integer.class, "privateField");
                throw new AssertionError("should throw");
            } catch (RuntimeException success) {
                assertNotNull(success.getCause());
            }
        }
    }

    static AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> updaterFor(String fieldName) {
        return AtomicReferenceFieldUpdater.newUpdater
            (AtomicReferenceFieldUpdaterTest.class, Integer.class, fieldName);
    }

    /**
     * Construction with non-existent field throws RuntimeException
     */
    public void testConstructor() {
        try {
            updaterFor("y");
            shouldThrow();
        } catch (RuntimeException success) {
            assertNotNull(success.getCause());
        }
    }

    /**
     * construction with field not of given type throws ClassCastException
     */
    public void testConstructor2() {
        try {
            updaterFor("z");
            shouldThrow();
        } catch (ClassCastException success) {}
    }

    /**
     * Constructor with non-volatile field throws IllegalArgumentException
     */
    public void testConstructor3() {
        try {
            updaterFor("w");
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Constructor with non-reference field throws ClassCastException
     */
    public void testConstructor4() {
        try {
            updaterFor("i");
            shouldThrow();
        } catch (ClassCastException success) {}
    }

    /**
     * construction using private field from subclass throws RuntimeException
     */
    public void testPrivateFieldInSubclass() {
        AtomicReferenceFieldUpdaterTestSubclass s =
            new AtomicReferenceFieldUpdaterTestSubclass();
        s.checkPrivateAccess();
    }

    /**
     * construction from unrelated class; package access is allowed,
     * private access is not
     */
    public void testUnrelatedClassAccess() {
        new UnrelatedClass().checkPackageAccess(this);
        new UnrelatedClass().checkPrivateAccess(this);
    }

    /**
     * get returns the last value set or assigned
     */
    public void testGetSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");
        x = one;
        assertSame(one, a.get(this));
        a.set(this, two);
        assertSame(two, a.get(this));
        a.set(this, m3);
        assertSame(m3, a.get(this));
    }

    /**
     * get returns the last value lazySet by same thread
     */
    public void testGetLazySet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");
        x = one;
        assertSame(one, a.get(this));
        a.lazySet(this, two);
        assertSame(two, a.get(this));
        a.lazySet(this, m3);
        assertSame(m3, a.get(this));
    }

    /**
     * compareAndSet succeeds in changing value if equal to expected else fails
     */
    public void testCompareAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");
        x = one;
        assertTrue(a.compareAndSet(this, one, two));
        assertTrue(a.compareAndSet(this, two, m4));
        assertSame(m4, a.get(this));
        assertFalse(a.compareAndSet(this, m5, seven));
        assertFalse(seven == a.get(this));
        assertTrue(a.compareAndSet(this, m4, seven));
        assertSame(seven, a.get(this));
    }

    /**
     * compareAndSet in one thread enables another waiting for value
     * to succeed
     */
    public void testCompareAndSetInMultipleThreads() throws Exception {
        x = one;
        final AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");

        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                while (!a.compareAndSet(AtomicReferenceFieldUpdaterTest.this, two, three))
                    Thread.yield();
            }});

        t.start();
        assertTrue(a.compareAndSet(this, one, two));
        t.join(LONG_DELAY_MS);
        assertFalse(t.isAlive());
        assertSame(three, a.get(this));
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");
        x = one;
        do {} while (!a.weakCompareAndSet(this, one, two));
        do {} while (!a.weakCompareAndSet(this, two, m4));
        assertSame(m4, a.get(this));
        do {} while (!a.weakCompareAndSet(this, m4, seven));
        assertSame(seven, a.get(this));
    }

    /**
     * getAndSet returns previous value and sets to given value
     */
    public void testGetAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer> a;
        a = updaterFor("x");
        x = one;
        assertSame(one, a.getAndSet(this, zero));
        assertSame(zero, a.getAndSet(this, m10));
        assertSame(m10, a.getAndSet(this, 1));
    }

}
