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
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.common.JVMCIHelpers
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 * @run main jdk.test.lib.FileInstaller ../common/services/ ./META-INF/services/
 * @run main jdk.test.lib.FileInstaller ./JvmciNotifyInstallEventTest.config
 *     ./META-INF/services/jdk.vm.ci.hotspot.HotSpotVMEventListener
 * @run main ClassFileInstaller
 *     compiler.jvmci.common.JVMCIHelpers$EmptyHotspotCompiler
 *     compiler.jvmci.common.JVMCIHelpers$EmptyCompilerFactory
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 *     compiler.jvmci.common.CTVMUtilities
 *     compiler.jvmci.common.testcases.SimpleClass
 *     jdk.vm.ci.hotspot.CompilerToVMHelper
 *     jdk.test.lib.Asserts
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Djvmci.compiler=EmptyCompiler -Xbootclasspath/a:. -Xmixed
 *     -XX:+UseJVMCICompiler -XX:-BootstrapJVMCI
 *     -Dcompiler.jvmci.events.JvmciNotifyInstallEventTest.noevent=false
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-EnableJVMCI
 *     -Djvmci.compiler=EmptyCompiler -Xbootclasspath/a:. -Xmixed
 *     -Dcompiler.jvmci.events.JvmciNotifyInstallEventTest.noevent=true
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 */

package compiler.jvmci.events;

import compiler.jvmci.common.CTVMUtilities;
import compiler.jvmci.common.testcases.SimpleClass;
import jdk.test.lib.Asserts;
import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class JvmciNotifyInstallEventTest implements HotSpotVMEventListener {
    private static final String METHOD_NAME = "testMethod";
    private static final boolean IS_POSITIVE = !Boolean.getBoolean(
            "compiler.jvmci.events.JvmciNotifyInstallEventTest.noevent");
    private static volatile int gotInstallNotification = 0;

    public static void main(String args[]) {
        new JvmciNotifyInstallEventTest().runTest();
    }

    private void runTest() {
        if (gotInstallNotification != 0) {
            throw new Error("Got install notification before test actions");
        }
        HotSpotCodeCacheProvider codeCache = null;
        try {
            codeCache = (HotSpotCodeCacheProvider) HotSpotJVMCIRuntime.runtime()
                    .getHostJVMCIBackend().getCodeCache();
        } catch (InternalError ie) {
            if (IS_POSITIVE) {
                throw new AssertionError(
                        "Got unexpected InternalError trying to get code cache",
                        ie);
            }
            // passed
            return;
        }
        Asserts.assertTrue(IS_POSITIVE,
                    "Haven't caught InternalError in negative case");
        Method testMethod;
        try {
            testMethod = SimpleClass.class.getDeclaredMethod(METHOD_NAME);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: Can't find " + METHOD_NAME, e);
        }
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(SimpleClass.class, testMethod);
        CompilationResult compResult = new CompilationResult(METHOD_NAME);
        HotSpotCompilationRequest compRequest = new HotSpotCompilationRequest(method, -1, 0L);
        // to pass sanity check of default -1
        compResult.setTotalFrameSize(0);
        codeCache.installCode(compRequest, compResult, /* installedCode = */ null, /* speculationLog = */ null,
                /* isDefault = */ false);
        Asserts.assertEQ(gotInstallNotification, 1,
                "Got unexpected event count after 1st install attempt");
        // since "empty" compilation result is ok, a second attempt should be ok
        compResult = new CompilationResult(METHOD_NAME); // create another instance with fresh state
        compResult.setTotalFrameSize(0);
        codeCache.installCode(compRequest, compResult, /* installedCode = */ null, /* speculationLog = */ null,
                /* isDefault = */ false);
        Asserts.assertEQ(gotInstallNotification, 2,
                "Got unexpected event count after 2nd install attempt");
    }

    @Override
    public void notifyInstall(HotSpotCodeCacheProvider hotSpotCodeCacheProvider,
            InstalledCode installedCode, CompilationResult compResult) {
        gotInstallNotification++;
    }
}
