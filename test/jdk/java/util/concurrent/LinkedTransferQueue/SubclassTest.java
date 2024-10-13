/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8323659
 * @summary Ensures that the implementation of LTQ add and put methods does
 *  not call overridable offer. This test specifically asserts implementation
 *  details of LTQ. It's not that such impl details cannot change, just that
 *  such a change should be deliberately done with suitable consideration
 *  to compatibility.
 * @run testng SubclassTest
 */

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

@Test
public class SubclassTest {

    public void testPut() {
        var queue = new TestLinkedTransferQueue();
        queue.put(new Object());
        assertEquals(queue.size(), 1);
    }

    public void testAdd() {
        var queue = new TestLinkedTransferQueue();
        queue.add(new Object());
        assertEquals(queue.size(), 1);
    }

    public void testTimedOffer() {
        var queue = new TestLinkedTransferQueue();
        queue.offer(new Object(), 60, TimeUnit.SECONDS);
        assertEquals(queue.size(), 1);
    }

    static class TestLinkedTransferQueue extends LinkedTransferQueue<Object> {
        @Override
        public boolean offer(Object obj) {
            return false;  //  simulate fails to add the given obj
        }
    }
}
