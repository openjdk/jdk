/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8292302
 * @summary Test persistence of native last error value under jdb (Windows)
 * @requires (os.family == "windows") & (vm.compMode != "Xcomp") & (vm.compMode != "Xint")
 * @library /test/lib
 * @run compile --release 20 --enable-preview JdbLastErrorTest.java
 * @run main/othervm --enable-preview JdbLastErrorTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import lib.jdb.JdbCommand;
import lib.jdb.JdbTest;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

class TestNativeLastError {

    static final int VALUE = 42;

    public static void main(String[] args) throws Throwable {
        testWindows();
    }

    private static void testWindows() throws Throwable {
        Linker linker = Linker.nativeLinker();
        System.loadLibrary("Kernel32");
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        MethodHandle getLastError = linker.downcallHandle(
            lookup.lookup("GetLastError").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        MethodHandle setLastError = linker.downcallHandle(
            lookup.lookup("SetLastError").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));

        for (int i = 0; i < 10; i++) {
            setLastError.invoke(VALUE);
            int lastError = (int) getLastError.invoke();
            System.out.println(lastError);
            if (lastError != VALUE) {
                throw new RuntimeException("failed, lastError = " + lastError);
            }
        }
    }
}

public class JdbLastErrorTest extends JdbTest {

    public static void main(String argv[]) {
        LaunchOptions lo = new LaunchOptions(DEBUGGEE_CLASS);
        lo.addVMOptions("--enable-preview");
        new JdbLastErrorTest(lo).run();
    }

    private JdbLastErrorTest(LaunchOptions launchOptions) {
        super(launchOptions);
    }

    private static final String DEBUGGEE_CLASS = TestNativeLastError.class.getName();

    @Override
    protected void runCases() {
        // Simply run app within jdb.
        // App should finish and exit, or report last error mismatch on failure.
        JdbCommand runCommand = JdbCommand.run();
        runCommand.allowExit();
        jdb.command(runCommand);
        new OutputAnalyzer(jdb.getJdbOutput()).shouldMatch("The application exited");
        new OutputAnalyzer(jdb.getJdbOutput()).shouldNotMatch("failed, lastError = ");
    }
}
