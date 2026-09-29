/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8292892 8345555
 * @summary  Tests that members inherited from classes with package access are
 *           documented in the index as though they were declared in the
 *           inheriting class.
 * @library  ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestIndexInherited
 */
import java.nio.file.Path;
import javadoc.tester.JavadocTester;

/**
 * Tests the index for members inherited from a class with package access.
 */
public class TestIndexInherited extends JavadocTester {

    /**
     * Name of the HTML index file.
     */
    private static final String INDEX_FILE = "index-all.html";

    /**
     * Name of the JavaScript member search index file.
     */
    private static final String SEARCH_FILE = "member-search-index.js";

    /**
     * Index entries for members inherited by the subclasses.
     */
    private static final String[] INDEX_INHERITED = {"""
        <dt><a href="pkg1/ClassB.html#methodA()" class="member-name-link">methodA()</a> \
        - Method in class pkg1.<a href="pkg1/ClassB.html" title="class in pkg1">ClassB</a></dt>
        """, """
        <dt><a href="pkg2/ClassC.html#methodA()" class="member-name-link">methodA()</a> \
        - Method in class pkg2.<a href="pkg2/ClassC.html" title="class in pkg2">ClassC</a></dt>
        """, """
        <dt><a href="pkg1/ClassB.html#STRING_A" class="member-name-link">STRING_A</a> \
        - Static variable in class pkg1.<a href="pkg1/ClassB.html" title="class in pkg1">ClassB</a></dt>
        """, """
        <dt><a href="pkg2/ClassC.html#STRING_A" class="member-name-link">STRING_A</a> \
        - Static variable in class pkg2.<a href="pkg2/ClassC.html" title="class in pkg2">ClassC</a></dt>
        """};

    /**
     * Search entries for members inherited by the subclasses.
     */
    private static final String[] SEARCH_INHERITED = {"""
        {"p":"pkg1","c":"ClassB","l":"methodA()"}""", """
        {"p":"pkg2","c":"ClassC","l":"methodA()"}""", """
        {"p":"pkg1","c":"ClassB","l":"STRING_A","k":"2"}""", """
        {"p":"pkg2","c":"ClassC","l":"STRING_A","k":"2"}"""};

    /**
     * Index entries for members declared by the superclass.
     */
    private static final String[] INDEX_DECLARED = {"""
        <dt><a href="pkg1/ClassA.html#methodA()" class="member-name-link">methodA()</a> \
        - Method in interface pkg1.<a href="pkg1/ClassA.html" title="interface in pkg1">ClassA</a></dt>
        """, """
        <dt><a href="pkg1/ClassA.html#STRING_A" class="member-name-link">STRING_A</a> \
        - Static variable in interface pkg1.<a href="pkg1/ClassA.html" title="interface in pkg1">ClassA</a></dt>
        """};

    /**
     * Search entries for members declared by the superclass.
     */
    private static final String[] SEARCH_DECLARED = {"""
        {"p":"pkg1","c":"ClassA","l":"methodA()"}""", """
        {"p":"pkg1","c":"ClassA","l":"STRING_A","k":"2"}"""};

    /**
     * Sole constructor.
     */
    public TestIndexInherited() {
    }

    /**
     * Runs the test methods.
     *
     * @param args the command-line arguments
     * @throws Exception if an errors occurs while executing a test method
     */
    public static void main(String... args) throws Exception {
        var tester = new TestIndexInherited();
        tester.runTests();
    }

    /**
     * Checks that the index includes the inherited members of both public
     * subclasses, loaded in alphabetical order, and that there is absolutely no
     * mention of the non-public superclass.
     *
     * @param base the base directory for this method's output
     */
    @Test
    public void testForInherited1(Path base) {
        String dir = base.resolve("out").toString();
        javadoc("-d", dir, "-sourcepath", testSrc, "pkg1", "pkg2");
        checkExit(Exit.OK);
        checkOrder(INDEX_FILE, INDEX_INHERITED);
        checkOrder(SEARCH_FILE, SEARCH_INHERITED);
        checkOutput(INDEX_FILE, false, "ClassA");
        checkOutput(SEARCH_FILE, false, "ClassA");
    }

    /**
     * Checks that the index includes the inherited members of both public
     * subclasses, loaded in reverse alphabetical order, and that there is
     * absolutely no mention of the non-public superclass.
     *
     * @param base the base directory for this method's output
     */
    @Test
    public void testForInherited2(Path base) {
        String dir = base.resolve("out").toString();
        javadoc("-d", dir, "-sourcepath", testSrc, "pkg2", "pkg1");
        checkExit(Exit.OK);
        checkOrder(INDEX_FILE, INDEX_INHERITED);
        checkOrder(SEARCH_FILE, SEARCH_INHERITED);
        checkOutput(INDEX_FILE, false, "ClassA");
        checkOutput(SEARCH_FILE, false, "ClassA");
    }

    /**
     * Checks that the index includes the declared members of the non-public
     * superclass when the Javadoc <i>private</i> option is specified, and that
     * it no longer includes the inherited members of either public subclass.
     *
     * @param base the base directory for this method's output
     */
    @Test
    public void testForDeclared(Path base) {
        String dir = base.resolve("out").toString();
        javadoc("-d", dir, "-sourcepath", testSrc, "-private", "pkg1", "pkg2");
        checkExit(Exit.OK);
        checkOrder(INDEX_FILE, INDEX_DECLARED);
        checkOrder(SEARCH_FILE, SEARCH_DECLARED);
        checkOutput(INDEX_FILE, false, INDEX_INHERITED);
        checkOutput(SEARCH_FILE, false, SEARCH_INHERITED);
    }
}
