/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @modules java.base/jdk.internal.ref java.base/jdk.internal.foreign
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED TestDetachAfterExit
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.fail;

public class TestDetachAfterExit extends NativeTestHelper  {

    @Test
    public void testDetachAtExit() throws IOException, InterruptedException {
        try {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    "--enable-native-access=ALL-UNNAMED",
                    "-Djava.library.path=" + System.getProperty("java.library.path"),
                    Runner.class.getName());
            // note that it's important to use ProcessTools.startProcess here since this makes sure output streams of the
            // fork don't fill up, which could make the process stall while writing to stdout/stderr
            Process process = ProcessTools.startProcess(Runner.class.getName(), pb, null, null, 1L, TimeUnit.MINUTES);
            OutputAnalyzer output = new OutputAnalyzer(process);
            output.outputTo(System.out);
            output.errorTo(System.err);

            output.shouldHaveExitValue(0)
                    .stdoutShouldContain("[await_join] done joining");
        } catch (TimeoutException e) {
            fail("Timeout while waiting for forked process");
        }
    }

    public static class Runner {
        static {
            System.loadLibrary("DetachAfterExit");
        }

        public static void main(String[] args) throws Throwable {
            MethodHandle mhCreate = Linker.nativeLinker().downcallHandle(
                    SymbolLookup.loaderLookup().findOrThrow("create_thread_and_register_atexit"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            MethodHandle mhCB = MethodHandles.lookup().findStatic(Runner.class, "cb",
                    MethodType.methodType(void.class, AtomicBoolean.class));
            FunctionDescriptor fdCB = FunctionDescriptor.ofVoid();

            AtomicBoolean flag = new AtomicBoolean();
            try (Arena arena = Arena.ofShared()) {
                MemorySegment cb = Linker.nativeLinker().upcallStub(mhCB.bindTo(flag), fdCB, arena);
                mhCreate.invokeExact(cb);

                System.out.println("[main] Waiting for callback...");
                while (!flag.get()) {
                    Thread.onSpinWait();
                }
                System.out.println("[main] done waiting for callback");
            }

            System.out.println("[main] VM shutting down");
            System.exit(0);
        }

        private static void cb(AtomicBoolean flag) {
            System.out.println("[cb] Inside cb");
            flag.set(true);
        }
    }
}
