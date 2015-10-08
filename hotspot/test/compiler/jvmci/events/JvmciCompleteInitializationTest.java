/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library / /testlibrary
 * @build compiler.jvmci.common.JVMCIHelpers
 *     compiler.jvmci.events.JvmciCompleteInitializationTest
 * @run main jdk.test.lib.FileInstaller ../common/services/ ./META-INF/services/
 * @run main jdk.test.lib.FileInstaller ./JvmciCompleteInitializationTest.config
 *     ./META-INF/services/jdk.vm.ci.hotspot.HotSpotVMEventListener
 * @run main ClassFileInstaller
 *     compiler.jvmci.common.JVMCIHelpers$EmptyHotspotCompiler
 *     compiler.jvmci.common.JVMCIHelpers$EmptyCompilerFactory
 *     compiler.jvmci.events.JvmciCompleteInitializationTest
 *     jdk.test.lib.Asserts
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *     -Xbootclasspath/a:.
 *     -XX:+EnableJVMCI
 *     -Dcompiler.jvmci.events.JvmciCompleteInitializationTest.positive=true
 *     compiler.jvmci.events.JvmciCompleteInitializationTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *     -Xbootclasspath/a:.
 *     -XX:-EnableJVMCI
 *     -Dcompiler.jvmci.events.JvmciCompleteInitializationTest.positive=false
 *     compiler.jvmci.events.JvmciCompleteInitializationTest
 */

package compiler.jvmci.events;

import jdk.test.lib.Asserts;
import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

public class JvmciCompleteInitializationTest implements HotSpotVMEventListener {
    private static final boolean IS_POSITIVE = Boolean.getBoolean(
            "compiler.jvmci.events.JvmciCompleteInitializationTest.positive");
    private static volatile int completeInitializationCount = 0;
    private static volatile String errorMessage = "";

    public static void main(String args[]) {
        if (completeInitializationCount != 0) {
            throw new Error("Unexpected completeInitialization events"
                    + " count at start");
        }
        initializeRuntime();
        int expectedEventCount = IS_POSITIVE ? 1 : 0;
        Asserts.assertEQ(completeInitializationCount, expectedEventCount,
                "Unexpected completeInitialization events count"
                        + " after JVMCI init");
        initializeRuntime();
        Asserts.assertEQ(completeInitializationCount, expectedEventCount,
                "Unexpected completeInitialization events count"
                        + " after 2nd JVMCI init");
        Asserts.assertTrue(errorMessage.isEmpty(), errorMessage);
    }

    private static void initializeRuntime() {
        Error t = null;
        try {
            /* in case JVMCI disabled, an InternalError on initialization
               and NoClassDefFound on 2nd try */
            HotSpotJVMCIRuntime.runtime();
        } catch (Error e) {
            t = e;
        }
        if (IS_POSITIVE) {
            Asserts.assertNull(t, "Caught unexpected exception");
        } else {
            Asserts.assertNotNull(t, "Got no expected error");
        }
    }

    @Override
    public void completeInitialization(HotSpotJVMCIRuntime
            hotSpotJVMCIRuntime) {
        completeInitializationCount++;
        if (hotSpotJVMCIRuntime == null) {
            errorMessage += " HotSpotJVMCIRuntime is null.";
        }
    }
}
