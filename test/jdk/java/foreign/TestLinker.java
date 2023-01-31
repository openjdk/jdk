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
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64" | os.arch=="riscv64"
 * @run testng TestLinker
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static org.testng.Assert.assertNotSame;

public class TestLinker extends NativeTestHelper {

    @Test
    public void testLinkerOptionsCache() {
        Linker linker = Linker.nativeLinker();
        FunctionDescriptor descriptor = FunctionDescriptor.ofVoid(C_INT, C_INT);
        MethodHandle mh1 = linker.downcallHandle(descriptor);
        MethodHandle mh2 = linker.downcallHandle(descriptor, Linker.Option.firstVariadicArg(1));
        // assert that these are 2 distinct link request. No caching allowed
        assertNotSame(mh1, mh2);
    }

    @DataProvider
    public static Object[][] invalidIndexCases() {
        return new Object[][]{
                { -1, },
                { 42, },
        };
    }

    @Test(dataProvider = "invalidIndexCases",
          expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*not in bounds for descriptor.*")
    public void testInvalidOption(int invalidIndex) {
        Linker.Option option = Linker.Option.firstVariadicArg(invalidIndex);
        FunctionDescriptor desc = FunctionDescriptor.ofVoid();
        Linker.nativeLinker().downcallHandle(desc, option); // throws
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*Unknown name.*")
    public void testInvalidPreservedValueName() {
        Linker.Option.captureCallState("foo"); // throws
    }

}
