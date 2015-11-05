/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary null test
 * @build KullaTesting TestingInputStream
 * @run testng TypeNameTest
 */

import jdk.jshell.Snippet;
import jdk.jshell.VarSnippet;
import org.testng.annotations.Test;

import static jdk.jshell.Snippet.Status.VALID;
import static org.testng.Assert.assertEquals;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;

@Test
public class TypeNameTest extends KullaTesting {

    public void testReplClassName() {
        assertEval("class C {}");
        VarSnippet sn = (VarSnippet) varKey(assertEval("new C();"));
        assertEquals(sn.typeName(), "C");
    }

    public void testReplNestedClassName() {
        assertEval("class D { static class E {} }");
        VarSnippet sn = (VarSnippet) varKey(assertEval("new D.E();"));
        assertEquals(sn.typeName(), "D.E");
    }

    public void testAnonymousClassName() {
        assertEval("class C {}");
        VarSnippet sn = (VarSnippet) varKey(assertEval("new C() { int x; };"));
        assertEquals(sn.typeName(), "C");
    }

    public void testCapturedTypeName() {
        VarSnippet sn = (VarSnippet) varKey(assertEval("\"\".getClass();"));
        assertEquals(sn.typeName(), "Class<? extends String>");
    }

    public void testJavaLang() {
        VarSnippet sn = (VarSnippet) varKey(assertEval("\"\";"));
        assertEquals(sn.typeName(), "String");
    }

    public void testNotOverEagerPackageEating() {
        VarSnippet sn = (VarSnippet) varKey(assertEval("\"\".getClass().getDeclaredMethod(\"hashCode\");"));
        assertEquals(sn.typeName(), "java.lang.reflect.Method");
    }

    public void testBounds() {
        assertEval("java.util.List<? extends String> list1 = java.util.Arrays.asList(\"\");");
        VarSnippet sn1 = (VarSnippet) varKey(assertEval("list1.iterator().next()"));
        assertEquals(sn1.typeName(), "String");
        assertEval("java.util.List<? super String> list2 = java.util.Arrays.asList(\"\");");
        VarSnippet sn2 = (VarSnippet) varKey(assertEval("list2.iterator().next()"));
        assertEquals(sn2.typeName(), "Object");
        assertEval("java.util.List<?> list3 = java.util.Arrays.asList(\"\");");
        VarSnippet sn3 = (VarSnippet) varKey(assertEval("list3.iterator().next()"));
        assertEquals(sn3.typeName(), "Object");
        assertEval("class Test1<X extends CharSequence> { public X get() { return null; } }");
        Snippet x = varKey(assertEval("Test1<?> x = new Test1<>();"));
        VarSnippet sn4 = (VarSnippet) varKey(assertEval("x.get()"));
        assertEquals(sn4.typeName(), "CharSequence");
        assertEval("class Foo<X extends Number & CharSequence> { public X get() { return null; } }");
        assertEval("Foo<?> x = new Foo<>();",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        VarSnippet sn5 = (VarSnippet) varKey(assertEval("x.get()"));
        assertEquals(sn5.typeName(), "Object");
    }
}
