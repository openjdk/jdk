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
 * @requires jdk.foreign.linker != "UNSUPPORTED"
 * @modules java.base/jdk.internal.foreign
 * @run testng TestLinker
 * @run testng/othervm/policy=security.policy
 *          -Djava.security.manager=default TestLinker
 */

import jdk.internal.foreign.CABI;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.foreign.MemoryLayout.*;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertNotSame;

public class TestLinker extends NativeTestHelper {

    static final boolean IS_FALLBACK_LINKER = CABI.current() == CABI.FALLBACK;

    record LinkRequest(FunctionDescriptor descriptor, Linker.Option... options) {}

    @Test(dataProvider = "notSameCases")
    public void testLinkerOptionsCache(LinkRequest l1, LinkRequest l2) {
        Linker linker = Linker.nativeLinker();
        MethodHandle mh1 = linker.downcallHandle(l1.descriptor(), l1.options());
        MethodHandle mh2 = linker.downcallHandle(l2.descriptor(), l2.options());
        // assert that these are 2 distinct link request. No caching allowed
        assertNotSame(mh1, mh2);
    }

    @DataProvider
    public static Object[][] notSameCases() {
        FunctionDescriptor fd_II_V = FunctionDescriptor.ofVoid(C_INT, C_INT);
        return new Object[][]{
            {new LinkRequest(fd_II_V), new LinkRequest(fd_II_V, Linker.Option.firstVariadicArg(1))},
            {new LinkRequest(FunctionDescriptor.ofVoid(JAVA_SHORT)), new LinkRequest(FunctionDescriptor.ofVoid(JAVA_CHAR))},
            {new LinkRequest(FunctionDescriptor.ofVoid(JAVA_SHORT)), new LinkRequest(FunctionDescriptor.ofVoid(JAVA_CHAR))},
        };
    }

    @Test(dataProvider = "namedDescriptors")
    public void testNamedLinkerCache(FunctionDescriptor f1, FunctionDescriptor f2) {
        Linker linker = Linker.nativeLinker();
        MethodHandle mh1 = linker.downcallHandle(f1);
        MethodHandle mh2 = linker.downcallHandle(f2);
        // assert that these are the same link request, even though layout names differ
        assertSame(mh1, mh2);
    }

    @DataProvider
    public static Object[][] namedDescriptors() {
        List<Object[]> cases = new ArrayList<>(Arrays.asList(new Object[][]{
            { FunctionDescriptor.ofVoid(C_INT),
                    FunctionDescriptor.ofVoid(C_INT.withName("x")) },
            { FunctionDescriptor.ofVoid(structLayout(C_INT)),
                    FunctionDescriptor.ofVoid(structLayout(C_INT).withName("x")) },
            { FunctionDescriptor.ofVoid(structLayout(C_INT)),
                    FunctionDescriptor.ofVoid(structLayout(C_INT.withName("x"))) },
            { FunctionDescriptor.ofVoid(structLayout(C_INT, paddingLayout(4), C_LONG_LONG)),
                    FunctionDescriptor.ofVoid(structLayout(C_INT, paddingLayout(4), C_LONG_LONG.withName("x"))) },
            { FunctionDescriptor.ofVoid(structLayout(C_INT, paddingLayout(4), C_LONG_LONG)),
                    FunctionDescriptor.ofVoid(structLayout(C_INT, paddingLayout(4).withName("x"), C_LONG_LONG)) },
            { FunctionDescriptor.ofVoid(structLayout(sequenceLayout(1, C_INT))),
                    FunctionDescriptor.ofVoid(structLayout(sequenceLayout(1, C_INT).withName("x"))) },
            { FunctionDescriptor.ofVoid(structLayout(sequenceLayout(1, C_INT))),
                    FunctionDescriptor.ofVoid(structLayout(sequenceLayout(1, C_INT.withName("x")))) },
            { FunctionDescriptor.ofVoid(C_POINTER),
                    FunctionDescriptor.ofVoid(C_POINTER.withName("x")) },
            { FunctionDescriptor.ofVoid(C_POINTER.withTargetLayout(C_INT)),
                    FunctionDescriptor.ofVoid(C_POINTER.withTargetLayout(C_INT.withName("x"))) },
            { FunctionDescriptor.ofVoid(C_POINTER.withTargetLayout(C_INT)),
                    FunctionDescriptor.ofVoid(C_POINTER.withName("x").withTargetLayout(C_INT.withName("x"))) },
        }));

        if (!IS_FALLBACK_LINKER) {
            cases.add(new Object[]{ FunctionDescriptor.ofVoid(unionLayout(C_INT)),
                    FunctionDescriptor.ofVoid(unionLayout(C_INT).withName("x")) });
            cases.add(new Object[]{ FunctionDescriptor.ofVoid(unionLayout(C_INT)),
                    FunctionDescriptor.ofVoid(unionLayout(C_INT.withName("x"))) });
        }

        return cases.toArray(Object[][]::new);
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
