/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.MemoryUsage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ToStringTest {

    @Test
    public void testInitUndefined() {
        MemoryUsage mu = new MemoryUsage(-1, 1024, 2048, 4096);
        String result = mu.toString();
        assertTrue(result.contains("init = N/A"),
                   "Should show 'init = N/A' for undefined init, but got: " + result);
        assertFalse(result.contains("init = -1"),
                    "Should not show 'init = -1' for undefined init, but got: " + result);
    }

    @Test
    public void testMaxUndefined() {
        MemoryUsage mu = new MemoryUsage(1024, 2048, 4096, -1);
        String result = mu.toString();
        assertTrue(result.contains("max = N/A"),
                   "Should show 'max = N/A' for undefined max, but got: " + result);
        assertFalse(result.contains("max = -1"),
                    "Should not show 'max = -1' for undefined max, but got: " + result);
    }

    @Test
    public void testBothUndefined() {
        MemoryUsage mu = new MemoryUsage(-1, 1024, 2048, -1);
        String result = mu.toString();
        assertTrue(result.contains("init = N/A"),
                   "Should show 'init = N/A' when both are undefined, but got: " + result);
        assertTrue(result.contains("max = N/A"),
                   "Should show 'max = N/A' when both are undefined, but got: " + result);
        assertFalse(result.contains("init = -1") || result.contains("max = -1"),
                    "Should not show '-1' for undefined values, but got: " + result);
    }

    @Test
    public void testAllValid() {
        MemoryUsage mu = new MemoryUsage(1024, 2048, 4096, 8192);
        String result = mu.toString();
        assertTrue(result.contains("init = 1024"),
                   "Should show init value for valid init, but got: " + result);
        assertTrue(result.contains("used = 2048"),
                   "Should show used value, but got: " + result);
        assertTrue(result.contains("committed = 4096"),
                   "Should show committed value, but got: " + result);
        assertTrue(result.contains("max = 8192"),
                   "Should show max value for valid max, but got: " + result);
        assertFalse(result.contains("N/A"),
                    "Should not show 'N/A' for valid values, but got: " + result);
    }

    @Test
    public void testZeroValues() {
        MemoryUsage mu = new MemoryUsage(0, 0, 0, 0);
        String result = mu.toString();
        assertTrue(result.contains("init = 0") && result.contains("used = 0") &&
                   result.contains("committed = 0") && result.contains("max = 0"),
                   "Should show zero values correctly, but got: " + result);
    }
}

