/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Asserts;
import jdk.test.lib.thread.ProcessThread;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8226919 8373867
 * @summary Test to make sure attach target process which is not dumpable.
 * @library /test/lib
 * @modules jdk.attach
 * @requires os.family == "linux"
 *
 * @run main/timeout=200 TestWithoutDumpableProcess
 */
public class TestWithoutDumpableProcess {

    private static final String EXPECTED_PROP_KEY = "attach.test";
    private static final String EXPECTED_PROP_VALUE = "true";

    public static class Debuggee {

        // Disable dumpable attribute via prctl(2)
        private static void disableDumpable() throws Throwable {
            var linker = Linker.nativeLinker();
            var prctl = linker.downcallHandle(linker.defaultLookup().findOrThrow("prctl"),
                                              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG),
                                              Linker.Option.firstVariadicArg(1), Linker.Option.captureCallState("errno"));
            var errnoSeg = Arena.global().allocate(Linker.Option.captureStateLayout());
            final int PR_SET_DUMPABLE = 4; // from linux/prctl.h

            int ret = (int)prctl.invoke(errnoSeg, PR_SET_DUMPABLE, 0L);
            if (ret == -1){
                var hndErrno = Linker.Option
                                     .captureStateLayout()
                                     .varHandle(MemoryLayout.PathElement.groupElement("errno"));
                int errno = (int)hndErrno.get(errnoSeg, 0L);
                throw new RuntimeException("prctl: errno=" + errno);
            }
        }

        public static void main(String[] args) throws Throwable {
            disableDumpable();
            IO.println(Application.READY_MSG);

            while (IO.readln().equals(Application.SHUTDOWN_MSG));
        }

        public static ProcessThread start() {
            var args = new String[]{
                "--enable-native-access=ALL-UNNAMED",
                String.format("-D%s=%s", EXPECTED_PROP_KEY, EXPECTED_PROP_VALUE), Debuggee.class.getName()
            };
            var pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
            var pt = new ProcessThread("runApplication", Application.READY_MSG::equals, pb);
            pt.start();
            return pt;
        }

    }

    public static void main(String[] args) throws Exception {
        var pt = Debuggee.start();
        var vm = VirtualMachine.attach(Long.toString(pt.getPid()));
        var val = vm.getSystemProperties().getProperty(EXPECTED_PROP_KEY);

        Asserts.assertNotNull(val, "Expected sysprop not found");
        Asserts.assertEquals(val, "true", "Unexpected sysprop value");
    }

}
