/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;
import org.testng.Assert;

import java.util.Locale;

import jdk.internal.misc.OperatingSystem;

import static jdk.internal.misc.OperatingSystem.AIX;
import static jdk.internal.misc.OperatingSystem.Linux;
import static jdk.internal.misc.OperatingSystem.MacOSX;
import static jdk.internal.misc.OperatingSystem.Windows;

/**
 * @test
 * @summary test platform enum
 * @modules java.base/jdk.internal.misc
 * @run testng PlatformTest
 */

@Test
public class PlatformTest {
    /**
     * Test consistency of System property "os.name" with OperatingSystem.current().
     */
    @Test
    void test1() {
        String osName = System.getProperty("os.name").substring(0, 3).toLowerCase(Locale.ROOT);
        OperatingSystem os = switch (osName) {
            case "win" -> Windows;
            case "lin" -> Linux;
            case "mac" -> MacOSX;
            case "aix" -> AIX;
            default    -> throw new RuntimeException("unknown OS kind: " + osName);
        };
        Assert.assertEquals(os, OperatingSystem.current(), "mismatch in OperatingSystem.current vs " + osName);
    }

    @Test
    void Test3() {
        int count = 0;
        for (OperatingSystem os : OperatingSystem.values()) {
            System.out.println("os: " + os + ", current: " + os.isCurrent());
            if  (os.isCurrent()) {
                count++;
            }
        }
        Assert.assertEquals(count, 1, "More than 1 OperatingSystem is 'current()'");
    }

 }
