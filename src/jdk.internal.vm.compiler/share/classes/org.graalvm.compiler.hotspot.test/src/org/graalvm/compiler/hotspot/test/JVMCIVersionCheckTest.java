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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck;
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

        for (int i = 0; i < 100; i++) {
            int minMajor = i;
            int minMinor = 100 - i;
            for (int j = 0; j < 100; j++) {
                int major = j;
                int minor = 100 - j;

                boolean ok = (major > minMajor) || (major == minMajor && minor >= minMinor);
                for (String sep : new String[]{".", "-b"}) {
                    String javaVmVersion = String.format("prefix-jvmci-%03d%s%03d-suffix", major, sep, minor);
                    if (ok) {
                        JVMCIVersionCheck.check(props, minMajor, minMinor, "1.8", javaVmVersion, false);
                    } else {
                        try {
                            JVMCIVersionCheck.check(props, minMajor, minMinor, "1.8", javaVmVersion, false);
                            Assert.fail("expected to fail checking " + javaVmVersion + " against " + minMajor + "." + minMinor);
                        } catch (InternalError e) {
                            // pass
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
                    JVMCIVersionCheck.check(props, 0, 59, "1.8", javaVmVersion, false);
                    Assert.fail("expected to fail checking " + javaVmVersion + " against 0.59");
                } catch (InternalError e) {
                    // pass
                }
            }
        }
    }
}
