/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8143964
 * @summary test queries to the JShell that return Streams
 * @build KullaTesting
 * @run testng JShellQueryTest
 */
import java.util.Set;
import java.util.stream.Stream;
import jdk.jshell.Snippet;
import org.testng.annotations.Test;

import jdk.jshell.ImportSnippet;
import jdk.jshell.MethodSnippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertEquals;

@Test
public class JShellQueryTest extends KullaTesting {

    private <T> void checkStreamMatch(Stream<T> result, T... expected) {
        Set<T> sns = result.collect(toSet());
        Set<T> exp = Stream.of(expected).collect(toSet());
        assertEquals(sns, exp);
    }

    public void testSnippets() {
        checkStreamMatch(getState().snippets());
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        VarSnippet sfoo = varKey(assertEval("String foo;"));
        MethodSnippet smm = methodKey(assertEval("int mm() { return 6; }"));
        MethodSnippet svv = methodKey(assertEval("void vv() { }"));
        checkStreamMatch(getState().snippets(), sx, sfoo, smm, svv);
        TypeDeclSnippet sc = classKey(assertEval("class C { }"));
        TypeDeclSnippet si = classKey(assertEval("interface I { }"));
        ImportSnippet simp = importKey(assertEval("import java.lang.reflect.*;"));
        checkStreamMatch(getState().snippets(), sx, sfoo, smm, svv, sc, si, simp);
    }

    public void testVars() {
        checkStreamMatch(getState().variables());
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        VarSnippet sfoo = varKey(assertEval("String foo;"));
        MethodSnippet smm = methodKey(assertEval("int mm() { return 6; }"));
        MethodSnippet svv = methodKey(assertEval("void vv() { }"));
        checkStreamMatch(getState().variables(), sx, sfoo);
        TypeDeclSnippet sc = classKey(assertEval("class C { }"));
        TypeDeclSnippet si = classKey(assertEval("interface I { }"));
        ImportSnippet simp = importKey(assertEval("import java.lang.reflect.*;"));
        checkStreamMatch(getState().variables(), sx, sfoo);
    }

    public void testMethods() {
        checkStreamMatch(getState().methods());
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        VarSnippet sfoo = varKey(assertEval("String foo;"));
        MethodSnippet smm = methodKey(assertEval("int mm() { return 6; }"));
        MethodSnippet svv = methodKey(assertEval("void vv() { }"));
        TypeDeclSnippet sc = classKey(assertEval("class C { }"));
        TypeDeclSnippet si = classKey(assertEval("interface I { }"));
        ImportSnippet simp = importKey(assertEval("import java.lang.reflect.*;"));
        checkStreamMatch(getState().methods(), smm, svv);
    }

    public void testTypes() {
        checkStreamMatch(getState().types());
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        VarSnippet sfoo = varKey(assertEval("String foo;"));
        MethodSnippet smm = methodKey(assertEval("int mm() { return 6; }"));
        MethodSnippet svv = methodKey(assertEval("void vv() { }"));
        TypeDeclSnippet sc = classKey(assertEval("class C { }"));
        TypeDeclSnippet si = classKey(assertEval("interface I { }"));
        ImportSnippet simp = importKey(assertEval("import java.lang.reflect.*;"));
        checkStreamMatch(getState().types(), sc, si);
    }

    public void testImports() {
        checkStreamMatch(getState().imports());
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        VarSnippet sfoo = varKey(assertEval("String foo;"));
        MethodSnippet smm = methodKey(assertEval("int mm() { return 6; }"));
        MethodSnippet svv = methodKey(assertEval("void vv() { }"));
        TypeDeclSnippet sc = classKey(assertEval("class C { }"));
        TypeDeclSnippet si = classKey(assertEval("interface I { }"));
        ImportSnippet simp = importKey(assertEval("import java.lang.reflect.*;"));
        checkStreamMatch(getState().imports(), simp);
    }

    public void testDiagnostics() {
        Snippet sx = varKey(assertEval("int x = 5;"));
        checkStreamMatch(getState().diagnostics(sx));
        Snippet broken = methodKey(assertEvalFail("int m() { blah(); return \"hello\"; }"));
        String res = getState().diagnostics(broken)
                .map(d -> d.getCode())
                .collect(joining("+"));
        assertEquals(res, "compiler.err.cant.resolve.location.args+compiler.err.prob.found.req");
    }

    public void testUnresolvedDependencies() {
        VarSnippet sx = varKey(assertEval("int x = 5;"));
        checkStreamMatch(getState().unresolvedDependencies(sx));
        MethodSnippet unr = methodKey(getState().eval("void uu() { baz(); zips(); }"));
        checkStreamMatch(getState().unresolvedDependencies(unr), "method zips()", "method baz()");
    }
}
