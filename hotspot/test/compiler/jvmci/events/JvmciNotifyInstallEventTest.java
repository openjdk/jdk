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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.meta
 *          jdk.vm.ci/jdk.vm.ci.runtime
 * @ignore 8144964
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
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
 *     jdk.test.lib.Asserts
 *     jdk.test.lib.Utils
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Djvmci.compiler=EmptyCompiler -Xbootclasspath/a:. -Xmixed
 *     -XX:+UseJVMCICompiler -XX:-BootstrapJVMCI
 *     -Dcompiler.jvmci.events.JvmciNotifyInstallEventTest.failoninit=false
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Djvmci.compiler=EmptyCompiler -Xbootclasspath/a:. -Xmixed
 *     -XX:+UseJVMCICompiler -XX:-BootstrapJVMCI -XX:JVMCINMethodSizeLimit=0
 *     -Dcompiler.jvmci.events.JvmciNotifyInstallEventTest.failoninit=false
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-EnableJVMCI
 *     -Djvmci.compiler=EmptyCompiler -Xbootclasspath/a:. -Xmixed
 *     -Dcompiler.jvmci.events.JvmciNotifyInstallEventTest.failoninit=true
 *     compiler.jvmci.events.JvmciNotifyInstallEventTest
 */

package compiler.jvmci.events;

import compiler.jvmci.common.CTVMUtilities;
import compiler.jvmci.common.testcases.SimpleClass;
import jdk.test.lib.Asserts;
import java.lang.reflect.Method;
import jdk.test.lib.Utils;
import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledCode.Comment;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class JvmciNotifyInstallEventTest implements HotSpotVMEventListener {
    private static final String METHOD_NAME = "testMethod";
    private static final boolean FAIL_ON_INIT = !Boolean.getBoolean(
            "compiler.jvmci.events.JvmciNotifyInstallEventTest.failoninit");
    private static volatile int gotInstallNotification = 0;

    public static void main(String args[]) {
        new JvmciNotifyInstallEventTest().runTest();
    }

    private void runTest() {
        if (gotInstallNotification != 0) {
            throw new Error("Got install notification before test actions");
        }
        HotSpotCodeCacheProvider codeCache;
        try {
            codeCache = (HotSpotCodeCacheProvider) HotSpotJVMCIRuntime.runtime()
                    .getHostJVMCIBackend().getCodeCache();
        } catch (InternalError ie) {
            if (FAIL_ON_INIT) {
                throw new AssertionError(
                        "Got unexpected InternalError trying to get code cache",
                        ie);
            }
            // passed
            return;
        }
        Asserts.assertTrue(FAIL_ON_INIT,
                    "Haven't caught InternalError in negative case");
        Method testMethod;
        try {
            testMethod = SimpleClass.class.getDeclaredMethod(METHOD_NAME);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: Can't find " + METHOD_NAME, e);
        }
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(SimpleClass.class, testMethod);
        HotSpotCompiledCode compiledCode = new HotSpotCompiledCode(METHOD_NAME,
                new byte[0], 0, new Site[0], new Assumption[0],
                new ResolvedJavaMethod[]{method}, new Comment[0], new byte[0],
                16, new DataPatch[0], false, 0, null);
        codeCache.installCode(method, compiledCode, /* installedCode = */ null,
                /* speculationLog = */ null, /* isDefault = */ false);
        Asserts.assertEQ(gotInstallNotification, 1,
                "Got unexpected event count after 1st install attempt");
        // since "empty" compilation result is ok, a second attempt should be ok
        codeCache.installCode(method, compiledCode, /* installedCode = */ null,
                /* speculationLog = */ null, /* isDefault = */ false);
        Asserts.assertEQ(gotInstallNotification, 2,
                "Got unexpected event count after 2nd install attempt");
        // and an incorrect cases
        Utils.runAndCheckException(() -> {
            codeCache.installCode(method, null, null, null, true);
        }, NullPointerException.class);
        Asserts.assertEQ(gotInstallNotification, 2,
                "Got unexpected event count after 3rd install attempt");
        Utils.runAndCheckException(() -> {
            codeCache.installCode(null, null, null, null, true);
        }, NullPointerException.class);
        Asserts.assertEQ(gotInstallNotification, 2,
                "Got unexpected event count after 4th install attempt");
    }

    @Override
    public void notifyInstall(HotSpotCodeCacheProvider hotSpotCodeCacheProvider,
            InstalledCode installedCode, CompiledCode compiledCode) {
        gotInstallNotification++;
    }
}
