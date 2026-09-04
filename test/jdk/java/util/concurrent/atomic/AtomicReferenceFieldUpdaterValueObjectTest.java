/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388310
 * @summary AtomicReferenceFieldUpdater does not perform a substitutability check
 * @enablePreview
 * @run junit ${test.main.class}
 */

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AtomicReferenceFieldUpdaterValueObjectTest {
    volatile Integer x = null;

    static AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> updaterFor(String fieldName) {
        return AtomicReferenceFieldUpdater.newUpdater
            (AtomicReferenceFieldUpdaterValueObjectTest.class, Integer.class, fieldName);
    }

    /**
     * get returns the last value set or assigned
     */
    @Test
    public void testGetSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");
        x = new Integer(1);
        assertSame(new Integer(1), a.get(this));
        a.set(this, new Integer(2));
        assertSame(new Integer(2), a.get(this));
        a.set(this, new Integer(-3));
        assertSame(new Integer(-3), a.get(this));
    }

    /**
     * get returns the last value lazySet by same thread
     */
    @Test
    public void testGetLazySet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");
        x = new Integer(1);
        assertSame(new Integer(1), a.get(this));
        a.lazySet(this, new Integer(2));
        assertSame(new Integer(2), a.get(this));
        a.lazySet(this, new Integer(-3));
        assertSame(new Integer(-3), a.get(this));
    }

    /**
     * compareAndSet succeeds in changing value if same as expected else fails
     */
    @Test
    public void testCompareAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");
        x = new Integer(1);
        assertTrue(a.compareAndSet(this, new Integer(1), new Integer(2)));
        assertTrue(a.compareAndSet(this, new Integer(2), new Integer(-4)));
        assertSame(new Integer(-4), a.get(this));
        assertFalse(a.compareAndSet(this, new Integer(-5), new Integer(7)));
        assertNotSame(new Integer(7), a.get(this));
        assertSame(new Integer(-4), a.get(this));
        assertTrue(a.compareAndSet(this, new Integer(-4), new Integer(7)));
        assertSame(new Integer(7), a.get(this));
    }

    /**
     * compareAndSet in new Integer(1) thread enables another waiting for value
     * to succeed
     */
    @Test
    public void testCompareAndSetInMultipleThreads() throws Exception {
        x = new Integer(1);
        final AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");

        Thread t = Thread.startVirtualThread(() -> {
            while (!a.compareAndSet(AtomicReferenceFieldUpdaterValueObjectTest.this, new Integer(2), new Integer(3)))
                Thread.yield();
        });

        assertTrue(a.compareAndSet(this, new Integer(1), new Integer(2)));
        t.join();
        assertFalse(t.isAlive());
        assertSame(new Integer(3), a.get(this));
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when same as expected
     */
    @Test
    public void testWeakCompareAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");
        x = new Integer(1);
        do {} while (!a.weakCompareAndSet(this, new Integer(1), new Integer(2)));
        do {} while (!a.weakCompareAndSet(this, new Integer(2), new Integer(-4)));
        assertSame(new Integer(-4), a.get(this));
        do {} while (!a.weakCompareAndSet(this, new Integer(-4), new Integer(7)));
        assertSame(new Integer(7), a.get(this));
    }

    /**
     * getAndSet returns previous value and sets to given value
     */
    @Test
    public void testGetAndSet() {
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterValueObjectTest, Integer> a;
        a = updaterFor("x");
        x = new Integer(1);
        assertSame(new Integer(1), a.getAndSet(this, new Integer(0)));
        assertSame(new Integer(0), a.getAndSet(this, new Integer(-10)));
        assertSame(new Integer(-10), a.getAndSet(this, new Integer(1)));
    }

}
