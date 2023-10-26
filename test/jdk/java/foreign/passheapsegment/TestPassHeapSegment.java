/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestPassHeapSegment
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;

public class TestPassHeapSegment extends UpcallTestHelper  {

    static {
        System.loadLibrary("PassHeapSegment");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
        expectedExceptionsMessageRegExp = ".*Heap segment not allowed.*")
    public void testNoHeapArgs() throws Throwable {
        MethodHandle handle = downcallHandle("test_args", FunctionDescriptor.ofVoid(ADDRESS));
        MemorySegment segment = MemorySegment.ofArray(new byte[]{ 0, 1, 2 });
        handle.invoke(segment); // should throw
    }

    @Test(dataProvider = "specs")
    public void testNoHeapReturns(boolean spec) throws IOException, InterruptedException {
        runInNewProcess(Runner.class, spec).assertFailed().assertStdErrContains("Heap segment not allowed");
    }

    public static class Runner {

        static {
            System.loadLibrary("PassHeapSegment");
        }

        public static void main(String[] args) throws Throwable {
            MethodHandle handle = downcallHandle("test_return", FunctionDescriptor.ofVoid(ADDRESS));
            MemorySegment upcallStub = upcallStub(Runner.class, "target", FunctionDescriptor.of(ADDRESS));
            handle.invoke(upcallStub);
        }

        public static MemorySegment target() {
            return MemorySegment.ofArray(new byte[]{ 0, 1, 2 }); // should throw
        }
    }

    @DataProvider
    public static Object[][] specs() {
        return new Object[][]{
            { true },
            { false }
        };
    }
}
