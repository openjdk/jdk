/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8008768 8287379
 * @summary Using {@inheritDoc} in simple tag defined via -tag fails
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main DocTest
 */

import javadoc.tester.JavadocTester;

public class DocTest extends JavadocTester {
    public static void main(String... args) throws Exception {
        DocTest tester = new DocTest();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-verbose",
                "-d", "DocTest",
                "-sourcepath", System.getProperty("test.src.path"),
                "-tag", "apiNote:a:API Note",
                "-tag", "implSpec:a:Implementation Requirements:",
                "-tag", "implNote:a:Implementation Note:",
                "-package",
                testSrc("DocTest.java")
        );
        checkExit(Exit.OK);

        // javadoc does not report an exit code for an internal exception (!)
        // so monitor stderr for stack dumps.
        checkOutput(Output.STDERR, false, "at com.sun");
    }

    /**
     * DocTest.testMethod() documentation.
     *
     * @apiNote DocTest.testMethod() API note.
     * @implSpec DocTest.testMethod() implementation spec.
     * @implNote DocTest.testMethod() implementation note.
     */
    public void testMethod() {
    }
}

class DocTestWithTags {

    /**
     * DocTest.testMethod() documentation.
     *
     * @apiNote DocTestWithTags.testMethod() API note.
     * <pre>
     *    DocTestWithTags.testMethod() API note code sample.
     * </pre>
     * @implSpec DocTestWithTags.testMethod() implementation spec.
     * <pre>
     *    DocTestWithTags.testMethod() implementation spec code sample.
     * </pre>
     * @implNote DocTest.testMethod() implementation note.
     * <pre>
     *    DocTest.testMethod() implementation note code sample.
     * </pre>
     */
    public void testMethod() {
    }
}

class MinimallyExtendsDocTest extends DocTest {
}

class SimpleExtendsDocTest extends DocTest {

    /**
     * SimpleExtendsDocTest.testMethod() documentation.
     */
    @Override
    public void testMethod() {
    }
}

class SimpleInheritDocDocTest extends DocTest {

    /**
     * {@inheritDoc}
     */
    @Override
    public void testMethod() {
    }
}

class FullInheritDocDocTest extends DocTest {

    /**
     * {@inheritDoc}
     *
     * @apiNote {@inheritDoc}
     * @implSpec {@inheritDoc}
     * @implNote {@inheritDoc}
     */
    @Override
    public void testMethod() {
    }
}

class FullInheritDocPlusDocTest extends DocTest {

    /**
     * {@inheritDoc} and FullInheritDocPlusDocTest.testMethod() documentation.
     *
     * @implSpec {@inheritDoc} and FullInheritDocPlusDocTest.testMethod() implementation specification.
     * @implNote {@inheritDoc} and FullInheritDocPlusDocTest.testMethod() implementation note.
     * @apiNote {@inheritDoc} and FullInheritDocPlusDocTest.testMethod() API note.
     */
    @Override
    public void testMethod() {
    }
}

