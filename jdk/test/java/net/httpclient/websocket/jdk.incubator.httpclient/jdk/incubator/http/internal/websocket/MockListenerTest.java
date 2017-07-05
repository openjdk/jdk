/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.http.internal.websocket;

import org.testng.annotations.Test;

import jdk.incubator.http.internal.websocket.TestSupport.AssertionFailedException;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.http.internal.websocket.TestSupport.assertThrows;
import static jdk.incubator.http.internal.websocket.TestSupport.checkExpectations;

public class MockListenerTest {

    @Test
    public void testPass01() {
        MockListener l = new MockListener.Builder().build();
        checkExpectations(1, TimeUnit.SECONDS, l);
    }

    @Test
    public void testPass02() {
        MockListener l = new MockListener.Builder()
                .expectOnOpen(ws -> ws == null)
                .build();
        l.onOpen(null);
        checkExpectations(1, TimeUnit.SECONDS, l);
    }

    @Test
    public void testPass03() {
        MockListener l = new MockListener.Builder()
                .expectOnOpen(ws -> ws == null)
                .expectOnClose((ws, code, reason) ->
                        ws == null && code == 1002 && "blah".equals(reason))
                .build();
        l.onOpen(null);
        l.onClose(null, 1002, "blah");
        checkExpectations(1, TimeUnit.SECONDS, l);
    }

    @Test
    public void testFail01() {
        MockListener l = new MockListener.Builder()
                .expectOnOpen(ws -> ws != null)
                .build();
        l.onOpen(null);
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, l));
    }

    @Test
    public void testFail02() {
        MockListener l = new MockListener.Builder()
                .expectOnOpen(ws -> true)
                .build();
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, l));
    }

    @Test
    public void testFail03() {
        MockListener l = new MockListener.Builder()
                .expectOnOpen(ws -> true)
                .build();
        l.onOpen(null);
        l.onClose(null, 1002, "");
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, l));
    }

    @Test
    public void testFail04() {
        MockListener l = new MockListener.Builder().build();
        l.onClose(null, 1002, "");
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, l));
    }
}
