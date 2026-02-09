/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8366926
 * @summary Verify the instrumenation class hierarchy resolution works properly in local execution mode
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build KullaTesting
 * @run junit/othervm LocalExecutionInstrumentationCHRTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalExecutionInstrumentationCHRTest extends ReplToolTesting {

    @Test
    public void verifyMyClassFoundOnClassPath() {
        test(new String[] { "--execution", "local" },
            a -> assertCommand(a, "public interface TestInterface {}", "|  created interface TestInterface"),
            a -> assertCommand(a,
                      "public class TestClass {"
                        + "public TestInterface foo(boolean b) {"
                            + "TestInterface test; "
                            + "if (b) {"
                                + "test = new TestInterfaceImpl1();"
                            + "} else {"
                                + "test = new TestInterfaceImpl2();"
                            + "}"
                            + "return test;"
                        + "}"
                        + "private class TestInterfaceImpl1 implements TestInterface {}"
                        + "private class TestInterfaceImpl2 implements TestInterface {}"
                    + "}", "|  created class TestClass"),
            a -> assertCommand(a, "new TestClass().foo(true).getClass();", "$3 ==> class TestClass$TestInterfaceImpl1"),
            a -> assertCommand(a, "new TestClass().foo(false).getClass();", "$4 ==> class TestClass$TestInterfaceImpl2")
        );
    }
}
