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
 */

import java.util.concurrent.atomic.AtomicReference;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AtomicReference9Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(AtomicReference9Test.class);
    }

    /**
     * getPlain returns the last value set
     */
    public void testGetPlainSet() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.getPlain());
        ai.set(two);
        assertEquals(two, ai.getPlain());
        ai.set(m3);
        assertEquals(m3, ai.getPlain());
    }

    /**
     * getOpaque returns the last value set
     */
    public void testGetOpaqueSet() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.getOpaque());
        ai.set(two);
        assertEquals(two, ai.getOpaque());
        ai.set(m3);
        assertEquals(m3, ai.getOpaque());
    }

    /**
     * getAcquire returns the last value set
     */
    public void testGetAcquireSet() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.getAcquire());
        ai.set(two);
        assertEquals(two, ai.getAcquire());
        ai.set(m3);
        assertEquals(m3, ai.getAcquire());
    }

    /**
     * get returns the last value setPlain
     */
    public void testGetSetPlain() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.get());
        ai.setPlain(two);
        assertEquals(two, ai.get());
        ai.setPlain(m3);
        assertEquals(m3, ai.get());
    }

    /**
     * get returns the last value setOpaque
     */
    public void testGetSetOpaque() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.get());
        ai.setOpaque(two);
        assertEquals(two, ai.get());
        ai.setOpaque(m3);
        assertEquals(m3, ai.get());
    }

    /**
     * get returns the last value setRelease
     */
    public void testGetSetRelease() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.get());
        ai.setRelease(two);
        assertEquals(two, ai.get());
        ai.setRelease(m3);
        assertEquals(m3, ai.get());
    }

    /**
     * compareAndExchange succeeds in changing value if equal to
     * expected else fails
     */
    public void testCompareAndExchange() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.compareAndExchange(one, two));
        assertEquals(two, ai.compareAndExchange(two, m4));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchange(m5, seven));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchange(m4, seven));
        assertEquals(seven, ai.get());
    }

    /**
     * compareAndExchangeAcquire succeeds in changing value if equal to
     * expected else fails
     */
    public void testCompareAndExchangeAcquire() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.compareAndExchangeAcquire(one, two));
        assertEquals(two, ai.compareAndExchangeAcquire(two, m4));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchangeAcquire(m5, seven));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchangeAcquire(m4, seven));
        assertEquals(seven, ai.get());
    }

    /**
     * compareAndExchangeRelease succeeds in changing value if equal to
     * expected else fails
     */
    public void testCompareAndExchangeRelease() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        assertEquals(one, ai.compareAndExchangeRelease(one, two));
        assertEquals(two, ai.compareAndExchangeRelease(two, m4));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchangeRelease(m5, seven));
        assertEquals(m4, ai.get());
        assertEquals(m4, ai.compareAndExchangeRelease(m4, seven));
        assertEquals(seven, ai.get());
    }

    /**
     * repeated weakCompareAndSetVolatile succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSetVolatile() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        do {} while (!ai.weakCompareAndSetVolatile(one, two));
        do {} while (!ai.weakCompareAndSetVolatile(two, m4));
        assertEquals(m4, ai.get());
        do {} while (!ai.weakCompareAndSetVolatile(m4, seven));
        assertEquals(seven, ai.get());
    }

    /**
     * repeated weakCompareAndSetAcquire succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSetAcquire() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        do {} while (!ai.weakCompareAndSetAcquire(one, two));
        do {} while (!ai.weakCompareAndSetAcquire(two, m4));
        assertEquals(m4, ai.get());
        do {} while (!ai.weakCompareAndSetAcquire(m4, seven));
        assertEquals(seven, ai.get());
    }

    /**
     * repeated weakCompareAndSetRelease succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSetRelease() {
        AtomicReference<Integer> ai = new AtomicReference<>(one);
        do {} while (!ai.weakCompareAndSetRelease(one, two));
        do {} while (!ai.weakCompareAndSetRelease(two, m4));
        assertEquals(m4, ai.get());
        do {} while (!ai.weakCompareAndSetRelease(m4, seven));
        assertEquals(seven, ai.get());
    }

}
