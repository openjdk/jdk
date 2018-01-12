/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 4358774
 * @run testng NullOutputStream
 * @summary Check for expected behavior of OutputStream.nullOutputStream().
 */
public class NullOutputStream {
    private static OutputStream openStream;
    private static OutputStream closedStream;

    @BeforeGroups(groups="open")
    public static void openStream() {
        openStream = OutputStream.nullOutputStream();
    }

    @BeforeGroups(groups="closed")
    public static void openAndCloseStream() {
        closedStream = OutputStream.nullOutputStream();
        try {
           closedStream.close();
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @AfterGroups(groups="open")
    public static void closeStream() {
        try {
            openStream.close();
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test(groups="open")
    public static void testOpen() {
        assertNotNull(openStream,
            "OutputStream.nullOutputStream() returned null");
    }

    @Test(groups="open")
    public static void testWrite() {
        try {
            openStream.write(62832);
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test(groups="open")
    public static void testWriteBII() {
        try {
            openStream.write(new byte[] {(byte)6}, 0, 1);
        } catch (Exception e) {
            fail("Unexpected IOException");
        }
    }

    @Test(groups="closed")
    public static void testWriteClosed() {
        try {
            closedStream.write(62832);
            fail("Expected IOException not thrown");
        } catch (IOException e) {
        }
    }

    @Test(groups="closed")
    public static void testWriteBIIClosed() {
        try {
            closedStream.write(new byte[] {(byte)6}, 0, 1);
            fail("Expected IOException not thrown");
        } catch (IOException e) {
        }
    }
}
