/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8034854 8042251
 * @summary Testing inner classes attributes.
 * @library /tools/lib /tools/javac/lib ../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.classfile
 * @build InnerClassesTestBase TestBase TestResult InMemoryFileManager ToolBox
 * @run main InnerClassesTest
 */

import java.util.List;

public class InnerClassesTest extends InnerClassesTestBase {

    public static void main(String[] args) throws TestFailedException {
        new InnerClassesTest().test("InnerClassesSrc");
    }

    private List<TestCase> generateClasses() {
        setInnerClassType(ClassType.CLASS);
        setHasSyntheticClass(true);
        return super.generateTestCases();
    }

    private List<TestCase> generateEnums() {
        setInnerOtherModifiers(Modifier.EMPTY, Modifier.STATIC);
        setInnerClassType(ClassType.ENUM);
        setHasSyntheticClass(false);
        return super.generateTestCases();
    }

    private List<TestCase> generateInterfaces() {
        setInnerOtherModifiers(Modifier.EMPTY, Modifier.ABSTRACT, Modifier.STATIC);
        setInnerClassType(ClassType.INTERFACE);
        return super.generateTestCases();
    }

    private List<TestCase> generateAnnotations() {
        setInnerOtherModifiers(Modifier.EMPTY, Modifier.ABSTRACT, Modifier.STATIC);
        setInnerClassType(ClassType.ANNOTATION);
        return super.generateTestCases();
    }

    @Override
    public void setProperties() {
        setOuterAccessModifiers(Modifier.EMPTY);
        setOuterOtherModifiers(Modifier.EMPTY);
        setOuterClassType(ClassType.OTHER);
    }

    @Override
    public List<TestCase> generateTestCases() {
        List<TestCase> sources = generateClasses();
        sources.addAll(generateEnums());
        sources.addAll(generateInterfaces());
        sources.addAll(generateAnnotations());
        return sources;
    }
}
