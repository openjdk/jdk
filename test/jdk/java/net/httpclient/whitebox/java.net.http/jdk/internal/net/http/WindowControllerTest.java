/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import static jdk.internal.net.http.frame.SettingsFrame.DEFAULT_INITIAL_WINDOW_SIZE;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WindowControllerTest {

    @Test
    public void testConnectionWindowOverflow() {
        WindowController wc = new WindowController();
        assertEquals(DEFAULT_INITIAL_WINDOW_SIZE, wc.connectionWindowSize());
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(DEFAULT_INITIAL_WINDOW_SIZE, wc.connectionWindowSize());

        wc.registerStream(1, DEFAULT_INITIAL_WINDOW_SIZE);
        wc.tryAcquire(DEFAULT_INITIAL_WINDOW_SIZE - 1, 1, null);
        assertEquals(1, wc.connectionWindowSize());
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(1, wc.connectionWindowSize());

        wc.increaseConnectionWindow(Integer.MAX_VALUE - 1 -1);
        assertEquals(Integer.MAX_VALUE - 1, wc.connectionWindowSize());
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 1, wc.connectionWindowSize());

        wc.increaseConnectionWindow(1);
        assertEquals(Integer.MAX_VALUE, wc.connectionWindowSize());
        assertEquals(false, wc.increaseConnectionWindow(1));
        assertEquals(false, wc.increaseConnectionWindow(100));
        assertEquals(false, wc.increaseConnectionWindow(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, wc.connectionWindowSize());
    }

    @Test
    public void testStreamWindowOverflow() {
        WindowController wc = new WindowController();
        wc.registerStream(1, DEFAULT_INITIAL_WINDOW_SIZE);
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 1));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 1));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 1));

        wc.registerStream(3, DEFAULT_INITIAL_WINDOW_SIZE);
        assertEquals(true, wc.increaseStreamWindow(100, 3));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 3));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 3));

        wc.registerStream(5, 0);
        assertEquals(true, wc.increaseStreamWindow(Integer.MAX_VALUE, 5));
        assertEquals(false, wc.increaseStreamWindow(1, 5));
        assertEquals(false, wc.increaseStreamWindow(1, 5));
        assertEquals(false, wc.increaseStreamWindow(10, 5));

        wc.registerStream(7, -1);
        assertEquals(true, wc.increaseStreamWindow(Integer.MAX_VALUE, 7));
        assertEquals(true, wc.increaseStreamWindow(1, 7));
        assertEquals(false, wc.increaseStreamWindow(1, 7));
        assertEquals(false, wc.increaseStreamWindow(10, 7));

        wc.registerStream(9, -1);
        assertEquals(true, wc.increaseStreamWindow(1, 9));
        assertEquals(true, wc.increaseStreamWindow(1, 9));
        assertEquals(true, wc.increaseStreamWindow(1, 9));
        assertEquals(true, wc.increaseStreamWindow(10, 9));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 9));

        wc.registerStream(11, -10);
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(true, wc.increaseStreamWindow(1, 11));
        assertEquals(1, wc.streamWindowSize(11));
        assertEquals(false, wc.increaseStreamWindow(Integer.MAX_VALUE, 11));
        assertEquals(1, wc.streamWindowSize(11));
    }

    @Test
    public void testStreamAdjustment() {
        WindowController wc = new WindowController();
        wc.registerStream(1, 100);
        wc.registerStream(3, 100);
        wc.registerStream(5, 100);

        // simulate some stream send activity before receiving the server's
        // SETTINGS frame, and staying within the connection window size
        wc.tryAcquire(49, 1 , null);
        wc.tryAcquire(50, 3 , null);
        wc.tryAcquire(51, 5 , null);

        wc.adjustActiveStreams(-200);
        assertEquals(-149, wc.streamWindowSize(1));
        assertEquals(-150, wc.streamWindowSize(3));
        assertEquals(-151, wc.streamWindowSize(5));
    }

    static final Class<InternalError> IE = InternalError.class;

    @Test
    public void testRemoveStream() {
        WindowController wc = new WindowController();
        wc.registerStream(1, 999);
        wc.removeStream(1);
        assertThrows(IE, () -> wc.tryAcquire(5, 1, null));

        wc.registerStream(3, 999);
        wc.tryAcquire(998, 3, null);
        wc.removeStream(3);
        assertThrows(IE, () -> wc.tryAcquire(5, 1, null));
    }
}
