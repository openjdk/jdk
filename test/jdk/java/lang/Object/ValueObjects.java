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
 * @summary Basic test of Object methods on value objects
 * @enablePreview
 * @library /test/lib
 * @run junit ${test.main.class}
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

import jdk.test.lib.Utils;

class ValueObjects {

    /**
     * Test the Object.clone method on a value object. It should return an object that
     * is indistinguishable from the original.
     */
    @Test
    void testClone() throws Exception {
        value class V implements Cloneable {
            @Override
            protected V clone() throws CloneNotSupportedException {
                return (V) super.clone();
            }
        }

        var obj = new V();
        assertSame(obj, obj.clone());
    }

    /**
     * Test the Object.clone method on a value object when the value class does
     * not implement Cloneable.
     */
    @Test
    void testCloneNotSupportedException() throws Exception {
        value class V {
            @Override
            protected V clone() throws CloneNotSupportedException {
                return (V) super.clone();
            }
        }

        var obj = new V();
        assertThrows(CloneNotSupportedException.class, obj::clone);
    }

    /**
     * Test that the finalize method on a value class is not invoked by the GC.
     */
    @Test
    void testValueClassFinalize() throws Exception {
        value class V {
            CountDownLatch latch;
            V(CountDownLatch latch) {
                this.latch = latch;
            }
            @Override
            protected void finalize() {
                latch.countDown();
            }
        }

        var latch = new TimeoutAdjustingLatch();
        var obj = new V(latch);
        obj = null;
        for (int i = 0; i < 3; i++) {
            System.gc();
            // latch should not count down
            assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        }
    }

    /**
     * Test that the finalize method on an abstract value value is not invoked by the GC.
     */
    @Test
    void testAbstractValueClassFinalize() throws Exception {
        abstract value class AV {
            CountDownLatch latch;
            AV(CountDownLatch latch) {
                this.latch = latch;
            }
            @Override
            protected void finalize() {
                latch.countDown();
            }
        }
        /*identity*/ class C extends AV {
            C(CountDownLatch latch) {
                super(latch);
            }
        }

        var latch = new TimeoutAdjustingLatch();
        var obj = new C(latch);
        obj = null;
        for (int i = 0; i < 3; i++) {
            System.gc();
            // latch should not count down
            assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        }
    }

    /**
     * Test that the wait/notify methods on a value object throw IMSE.
     */
    @Test
    void testWaitNotify() {
        value class V {}
        Object obj = new V();
        assertThrows(IllegalMonitorStateException.class, () -> obj.wait());
        assertThrows(IllegalMonitorStateException.class, () -> obj.wait(1000));
        assertThrows(IllegalMonitorStateException.class, () -> obj.wait(1000, 10));
        assertThrows(IllegalMonitorStateException.class, () -> obj.notify());
        assertThrows(IllegalMonitorStateException.class, () -> obj.notifyAll());
    }

    /**
     * Test default toString method.
     */
    @Test
    void testToString() {
        value class V { }
        var obj = new V();
        String expected = V.class.getName() + "@" + Integer.toHexString(obj.hashCode());
        assertEquals(expected, obj.toString());
    }

    /**
     * A CountDownLatch that is created with a count of 1 and with a timed-await method
     * that adjusts the timeout based on the jtreg timeout factory configuration.
     */
    private static class TimeoutAdjustingLatch extends CountDownLatch {
        TimeoutAdjustingLatch() {
            super(1);
        }
        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return super.await(Utils.adjustTimeout(timeout), unit);
        }
    }
}
