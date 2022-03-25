/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestIllegalLink
 */

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIllegalLink extends NativeTestHelper {

    private static final NativeSymbol DUMMY_TARGET = NativeSymbol.ofAddress("dummy", MemoryAddress.ofLong(1), ResourceScope.globalScope());
    private static final CLinker ABI = CLinker.systemCLinker();

    @Test(dataProvider = "types")
    public void testTypeMismatch(FunctionDescriptor desc, String expectedExceptionMessage) {
        try {
            ABI.downcallHandle(DUMMY_TARGET, desc);
            fail("Expected IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedExceptionMessage));
        }
    }

    @DataProvider
    public static Object[][] types() {
        return new Object[][]{
            {
                FunctionDescriptor.of(MemoryLayout.paddingLayout(64)),
                "Unsupported layout: x64"
            },
            {
                FunctionDescriptor.ofVoid(MemoryLayout.paddingLayout(64)),
                "Unsupported layout: x64"
            },
            {
                    FunctionDescriptor.of(MemoryLayout.sequenceLayout(C_INT)),
                    "Unsupported layout: [:b32]"
            },
            {
                    FunctionDescriptor.ofVoid(MemoryLayout.sequenceLayout(C_INT)),
                    "Unsupported layout: [:b32]"
            },
        };
    }

}
