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

package compiler.valhalla.inlinetypes;

/*
 * @test
 * @bug 8380927
 * @summary Polluted callsite profiling must not break the idempotence of InlineTypeNode::Value
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -Xbatch
 *                   -XX:-TieredCompilation -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:CompileCommand=dontinline,${test.main.class}::call
 *                   -XX:+AlwaysIncrementalInline -XX:-InlineTypeReturnedAsFields
 *                   -XX:TypeProfileLevel=222 ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestPollutedCallsiteProfileInlineType {
    static value class MyValue {}

    static MyValue call(boolean throwRE) {
        if (throwRE) {
            // 1) At the outer callsite (in test) we want ProfileNeverNull:
            // ==> we should never observe a null return value
            // 2) At the inner callsite (in callWrapper) we want ProfileAlwaysNull:
            // ==> we should never observe a non-null return value
            // By conditionally throwing an exception we can guarantee that 1) and 2) hold
            throw new RuntimeException();
        } else {
            return null;
        }
    }

    static MyValue callWrapper(boolean throwRE) {
        return call(throwRE); // profile: ProfileAlwaysNull
    }

    static MyValue test() {
        return callWrapper(true); // profile: ProfileNeverNull
    }

    public static void main(String[] args) {
        // pollute the profile in callWrapper
        for (int i = 0; i < 2000; i++) {
            callWrapper(false);
        }

        // get a compilation based on the polluted profile
        for (int i = 0; i < 7000; i++) {
            try {
                test();
            } catch (RuntimeException t) {}
        }
    }
}
