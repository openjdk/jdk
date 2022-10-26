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
 * @enablePreview
 * @library ../ /test/lib
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestCaptureCallState
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.assertEquals;

public class TestCaptureCallState extends NativeTestHelper {

    static {
        System.loadLibrary("CaptureCallState");
        if (IS_WINDOWS) {
            String system32 = System.getenv("SystemRoot") + "\\system32";
            System.load(system32 + "\\Kernel32.dll");
            System.load(system32 + "\\Ws2_32.dll");
        }
    }

    private record SaveValuesCase(String nativeTarget, String threadLocalName) {}

    @Test(dataProvider = "cases")
    public void testSavedThreadLocal(SaveValuesCase testCase) throws Throwable {
        Linker.Option.CaptureCallState stl = Linker.Option.captureCallState(testCase.threadLocalName());
        MethodHandle handle = downcallHandle(testCase.nativeTarget(), FunctionDescriptor.ofVoid(JAVA_INT), stl);

        VarHandle errnoHandle = stl.layout().varHandle(groupElement(testCase.threadLocalName()));

        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment saveSeg = session.allocate(stl.layout());
            int testValue = 42;
            handle.invoke(saveSeg, testValue);
            int savedErrno = (int) errnoHandle.get(saveSeg);
            assertEquals(savedErrno, testValue);
        }
    }

    @DataProvider
    public static Object[][] cases() {
        List<SaveValuesCase> cases = new ArrayList<>();

        cases.add(new SaveValuesCase("set_errno", "errno"));
        if (IS_WINDOWS) {
            cases.add(new SaveValuesCase("SetLastError", "GetLastError"));
            cases.add(new SaveValuesCase("WSASetLastError", "WSAGetLastError"));
        }

        return cases.stream().map(tc -> new Object[] {tc}).toArray(Object[][]::new);
    }

}

