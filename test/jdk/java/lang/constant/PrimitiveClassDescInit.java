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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.lang.constant.ConstantDescs;

/*
 * @test
 * @bug 8396824
 * @summary PrimitiveClassDescImpl initialization order
 * @library /test/lib
 * @requires vm.flagless
 * @comment module adjustment flags etc. trigger bytecode generation and
 *          ConstantDescs initialization side effects
 * @run driver ${test.main.class}
 */
public class PrimitiveClassDescInit {
    public static void main(String... args) throws Throwable {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xlog:class+init",
                "PrimitiveClassDescInitCase");

        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        // Ensure correct initialization order in the test
        out.shouldMatch("(?s)Initializing 'PrimitiveClassDescInitCase'"
                + ".*Initializing 'jdk/internal/constant/PrimitiveClassDescImpl'"
                + ".*Initializing 'java/lang/constant/ConstantDescs'");
    }
}

class PrimitiveClassDescInitCase {
    public static void main(String... args) throws Throwable {
        // Ensure the initialization does not throw any exception
        Class.forName("jdk.internal.constant.PrimitiveClassDescImpl");
        // NPE if CD_void is not properly initialized
        ConstantDescs.CD_void.isPrimitive();
    }
}


