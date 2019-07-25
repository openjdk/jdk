/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version2;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck.Version3;
import org.junit.Assert;
import org.junit.Test;

public class JVMCIVersionCheckTest extends GraalCompilerTest {

    @Test
    public void test01() {
        Properties sprops = System.getProperties();
        Map<String, String> props = new HashMap<>(sprops.size());
        for (String name : sprops.stringPropertyNames()) {
            props.put(name, sprops.getProperty(name));
        }

        long seed = Long.getLong("test.seed", System.nanoTime());
        Random random = new Random(seed);

        for (int i = 0; i < 50; i++) {
            int minMajor = i;
            int minMinor = 50 - i;
            for (int j = 0; j < 50; j++) {
                int major = j;
                int minor = 50 - j;

                for (int k = 0; k < 30; k++) {
                    int minBuild = random.nextInt(100);
                    int build = random.nextInt(100);

                    for (Version version : new Version[]{new Version2(major, minor), new Version3(major, minor, build)}) {
                        for (Version minVersion : new Version[]{new Version2(minMajor, minMinor), new Version3(minMajor, minMinor, minBuild)}) {
                            String javaVmVersion = String.format("prefix-jvmci-%s-suffix", version);
                            if (!version.isLessThan(minVersion)) {
                                try {
                                    JVMCIVersionCheck.check(props, minVersion, "1.8", javaVmVersion, false);
                                } catch (InternalError e) {
                                    throw new AssertionError("Failed " + JVMCIVersionCheckTest.class.getSimpleName() + " with -Dtest.seed=" + seed, e);
                                }
                            } else {
                                try {
                                    JVMCIVersionCheck.check(props, minVersion, "1.8", javaVmVersion, false);
                                    Assert.fail("expected to fail checking " + javaVmVersion + " against " + minVersion + " (-Dtest.seed=" + seed + ")");
                                } catch (InternalError e) {
                                    // pass
                                }
                            }
                        }
                    }
                }
            }
        }

        // Test handling of version components bigger than Integer.MAX_VALUE
        for (String sep : new String[]{".", "-b"}) {
            for (String version : new String[]{"0" + sep + Long.MAX_VALUE, Long.MAX_VALUE + sep + 0}) {
                String javaVmVersion = String.format("prefix-jvmci-%s-suffix", version);
                try {
                    Version2 minVersion = new Version2(0, 59);
                    JVMCIVersionCheck.check(props, minVersion, "1.8", javaVmVersion, false);
                    Assert.fail("expected to fail checking " + javaVmVersion + " against " + minVersion);
                } catch (InternalError e) {
                    // pass
                }
            }
        }
    }
}
