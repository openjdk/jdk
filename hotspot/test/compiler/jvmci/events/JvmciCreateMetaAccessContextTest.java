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
 * @compile ./MetaAccessWrapper.java
 * @build compiler.jvmci.common.JVMCIHelpers
 *     compiler.jvmci.events.JvmciCreateMetaAccessContextTest
 * @run main jdk.test.lib.FileInstaller ../common/services/ ./META-INF/services/
 * @run main jdk.test.lib.FileInstaller
 *     ./JvmciCreateMetaAccessContextTest.config
 *     ./META-INF/services/jdk.vm.ci.hotspot.HotSpotVMEventListener
 * @run main ClassFileInstaller
 *     compiler.jvmci.common.JVMCIHelpers$EmptyHotspotCompiler
 *     compiler.jvmci.common.JVMCIHelpers$EmptyCompilerFactory
 *     compiler.jvmci.events.JvmciCreateMetaAccessContextTest
 *     jdk.vm.ci.hotspot.MetaAccessWrapper
 *     jdk.test.lib.Asserts
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Xbootclasspath/a:.
 *     -Dcompiler.jvmci.events.JvmciCreateMetaAccessContextTest.providenull=true
 *     compiler.jvmci.events.JvmciCreateMetaAccessContextTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Xbootclasspath/a:.
 *     -Dcompiler.jvmci.events.JvmciCreateMetaAccessContextTest.providenull=false
 *     compiler.jvmci.events.JvmciCreateMetaAccessContextTest
 */

package compiler.jvmci.events;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.hotspot.MetaAccessWrapper;
import jdk.vm.ci.meta.JVMCIMetaAccessContext;
import jdk.test.lib.Asserts;

public class JvmciCreateMetaAccessContextTest
        implements HotSpotVMEventListener {
    private static final boolean PROVIDE_NULL_CONTEXT = Boolean.getBoolean(
            "compiler.jvmci.events.JvmciCreateMetaAccessContextTest"
                    + ".providenull");
    private static volatile int createMetaAccessContextCount = 0;
    private static volatile String errorMessage = "";

    public static void main(String args[]) {
        if (createMetaAccessContextCount != 0) {
            throw new Error("Unexpected createMetaAccessContextevents count"
                    + " at test start");
        }
        JVMCIMetaAccessContext context;
        context = HotSpotJVMCIRuntime.runtime().getMetaAccessContext();
        Asserts.assertNotNull(context,
                "JVMCIMetaAccessContext is null after 1st request");
        Asserts.assertEQ(createMetaAccessContextCount, 1,
                "Unexpected createMetaAccessContext events count after 1st"
                        + " JVMCI runtime request");
        context = HotSpotJVMCIRuntime.runtime().getMetaAccessContext();
        Asserts.assertNotNull(context,
                "JVMCIMetaAccessContext is null after 2nd request");
        Asserts.assertEQ(createMetaAccessContextCount, 1,
                "Unexpected createMetaAccessContext events count after 2nd"
                        + " JVMCI runtime request");
        Asserts.assertTrue(errorMessage.isEmpty(), errorMessage);
        if (PROVIDE_NULL_CONTEXT) {
            Asserts.assertFalse(context instanceof MetaAccessWrapper,
                    "Got unexpected context: " + context.getClass());
        } else {
            Asserts.assertTrue(context instanceof MetaAccessWrapper,
                    "Got unexpected context: " + context.getClass());
        }
    }

    @Override
    public JVMCIMetaAccessContext createMetaAccessContext(HotSpotJVMCIRuntime
            hotSpotJVMCIRuntime) {
        createMetaAccessContextCount++;
        if (hotSpotJVMCIRuntime == null) {
            errorMessage += " HotSpotJVMCIRuntime is null.";
        }
        if (PROVIDE_NULL_CONTEXT) {
            return null;
        }
        return new MetaAccessWrapper();
    }
}
