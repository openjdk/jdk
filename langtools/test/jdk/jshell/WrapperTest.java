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
 * @bug 8159111
 * @summary test wrappers and dependencies
 * @modules jdk.jshell/jdk.jshell
 * @build KullaTesting
 * @run testng WrapperTest
 */

import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import jdk.jshell.ErroneousSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.Snippet.Kind;
import jdk.jshell.SourceCodeAnalysis.SnippetWrapper;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static jdk.jshell.Snippet.Status.RECOVERABLE_DEFINED;
import static jdk.jshell.Snippet.Status.VALID;

@Test
public class WrapperTest extends KullaTesting {

    public void testMethod() {
        String src = "void glib() { System.out.println(\"hello\"); }";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.METHOD, "void", "glib", "println");
        assertPosition(swl.get(0), src, 0, 4);
        assertPosition(swl.get(0), src, 5, 4);
        assertPosition(swl.get(0), src, 15, 6);

        Snippet g = methodKey(assertEval(src, added(VALID)));
        SnippetWrapper swg = getState().sourceCodeAnalysis().wrapper(g);
        assertWrapperHas(swg, src, Kind.METHOD, "void", "glib", "println");
        assertPosition(swg, src, 0, 4);
        assertPosition(swg, src, 5, 4);
        assertPosition(swg, src, 15, 6);
    }

    @Test(enabled = false) // TODO 8159740
    public void testMethodCorralled() {
        String src = "void glib() { f(); }";
        Snippet g = methodKey(assertEval(src, added(RECOVERABLE_DEFINED)));
        SnippetWrapper swg = getState().sourceCodeAnalysis().wrapper(g);
        assertWrapperHas(swg, src, Kind.METHOD, "void", "glib");
        assertPosition(swg, src, 5, 4);
    }

    public void testMethodBad() {
        String src = "void flob() { ?????; }";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.METHOD, "void", "flob", "?????");
        assertPosition(swl.get(0), src, 9, 2);

        Snippet f = key(assertEvalFail(src));
        assertEquals(f.kind(), Kind.ERRONEOUS);
        assertEquals(((ErroneousSnippet)f).probableKind(), Kind.METHOD);
        SnippetWrapper sw = getState().sourceCodeAnalysis().wrapper(f);
        assertWrapperHas(sw, src, Kind.METHOD, "void", "flob", "?????");
        assertPosition(swl.get(0), src, 14, 5);
    }

    public void testVar() {
        String src = "int gx = 1234;";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.VAR, "int", "gx", "1234");
        assertPosition(swl.get(0), src, 4, 2);

        Snippet g = varKey(assertEval(src, added(VALID)));
        SnippetWrapper swg = getState().sourceCodeAnalysis().wrapper(g);
        assertWrapperHas(swg, src, Kind.VAR, "int", "gx", "1234");
        assertPosition(swg, src, 0, 3);
    }

    public void testVarBad() {
        String src = "double dd = ?????;";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.VAR, "double", "dd", "?????");
        assertPosition(swl.get(0), src, 9, 2);

        Snippet f = key(assertEvalFail(src));
        assertEquals(f.kind(), Kind.ERRONEOUS);
        assertEquals(((ErroneousSnippet)f).probableKind(), Kind.VAR);
        SnippetWrapper sw = getState().sourceCodeAnalysis().wrapper(f);
        assertWrapperHas(sw, src, Kind.VAR, "double", "dd", "?????");
        assertPosition(swl.get(0), src, 12, 5);
    }

    public void testImport() {
        String src = "import java.lang.*;";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.IMPORT, "import", "java.lang");
        assertPosition(swl.get(0), src, 7, 4);

        Snippet g = key(assertEval(src, added(VALID)));
        SnippetWrapper swg = getState().sourceCodeAnalysis().wrapper(g);
        assertWrapperHas(swg, src, Kind.IMPORT, "import", "java.lang");
        assertPosition(swg, src, 0, 6);
    }

    public void testImportBad() {
        String src = "import java.?????;";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.IMPORT, "import", "?????");
        assertPosition(swl.get(0), src, 7, 4);

        Snippet f = key(assertEvalFail(src));
        assertEquals(f.kind(), Kind.ERRONEOUS);
        assertEquals(((ErroneousSnippet)f).probableKind(), Kind.IMPORT);
        SnippetWrapper sw = getState().sourceCodeAnalysis().wrapper(f);
        assertWrapperHas(sw, src, Kind.IMPORT, "import", "?????");
        assertPosition(swl.get(0), src, 0, 6);
    }

    public void testErroneous() {
        String src = "@@@@@@@@@@";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 1, "unexpected list length");
        assertWrapperHas(swl.get(0), src, Kind.ERRONEOUS, "@@@@@@@@@@");
        assertPosition(swl.get(0), src, 0, 10);

        Snippet f = key(assertEvalFail(src));
        assertEquals(f.kind(), Kind.ERRONEOUS);
        assertEquals(((ErroneousSnippet)f).probableKind(), Kind.ERRONEOUS);
        SnippetWrapper sw = getState().sourceCodeAnalysis().wrapper(f);
        assertWrapperHas(sw, src, Kind.ERRONEOUS, "@@@@@@@@@@");
        assertPosition(swl.get(0), src, 0, 10);
    }

    public void testEmpty() {
        String src = "";
        List<SnippetWrapper> swl = getState().sourceCodeAnalysis().wrappers(src);
        assertEquals(swl.size(), 0, "expected empty list");
    }

    public void testDependencies() {
        Snippet a = key(assertEval("int aaa = 6;", added(VALID)));
        Snippet b = key(assertEval("class B { B(int x) { aaa = x; } }", added(VALID)));
        Snippet c = key(assertEval("B ccc() { return new B(aaa); }", added(VALID)));
        Collection<Snippet> dep;
        dep = getState().sourceCodeAnalysis().dependents(c);
        assertEquals(dep.size(), 0);
        dep = getState().sourceCodeAnalysis().dependents(b);
        assertEquals(dep.size(), 1);
        assertTrue(dep.contains(c));
        dep = getState().sourceCodeAnalysis().dependents(a);
        assertEquals(dep.size(), 2);
        assertTrue(dep.contains(c));
        assertTrue(dep.contains(b));
    }

    private void assertWrapperHas(SnippetWrapper sw, String source, Kind kind, String... has) {
        assertEquals(sw.source(), source);
        assertEquals(sw.kind(), kind);
        if (kind == Kind.IMPORT) {
            assertTrue(sw.wrapped().contains("import"));
        } else {
            String cn = sw.fullClassName();
            int idx = cn.lastIndexOf(".");
            assertTrue(sw.wrapped().contains(cn.substring(idx+1)));
            assertTrue(sw.wrapped().contains("class"));
        }
        for (String s : has) {
            assertTrue(sw.wrapped().contains(s));
        }
    }

    private void assertPosition(SnippetWrapper sw, String source, int start, int length) {
        int wpg = sw.sourceToWrappedPosition(start);
        assertEquals(sw.wrapped().substring(wpg, wpg+length),
                source.substring(start, start+length),
                "position " + wpg + " in " + sw.wrapped());
        assertEquals(sw.wrappedToSourcePosition(wpg), start);
    }
}
