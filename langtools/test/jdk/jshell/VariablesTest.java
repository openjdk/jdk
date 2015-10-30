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
 * @summary Tests for EvaluationState.variables
 * @build KullaTesting TestingInputStream ExpectedDiagnostic
 * @run testng VariablesTest
 */

import java.util.List;
import javax.tools.Diagnostic;

import jdk.jshell.Snippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.SnippetEvent;
import org.testng.annotations.Test;

import static jdk.jshell.Snippet.Status.*;
import static jdk.jshell.Snippet.SubKind.VAR_DECLARATION_SUBKIND;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public class VariablesTest extends KullaTesting {

    public void noVariables() {
        assertNumberOfActiveVariables(0);
    }

    private void badVarValue(VarSnippet key) {
        try {
            getState().varValue(key);
            fail("Expected exception for: " + key.source());
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testVarValue1() {
        VarSnippet v1 = varKey(assertEval("und1 a;", added(RECOVERABLE_NOT_DEFINED)));
        badVarValue(v1);
        VarSnippet v2 = varKey(assertEval("und2 a;",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(v1, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        badVarValue(v2);
        TypeDeclSnippet und = classKey(assertEval("class und2 {}",
                added(VALID),
                ste(v2, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET)));
        assertVarValue(v2, "null");
        assertDrop(und,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(und, VALID, DROPPED, true, null),
                ste(v2, VALID, RECOVERABLE_NOT_DEFINED, true, und));
        badVarValue(v1);
        badVarValue(v2);
    }

    public void testVarValue2() {
        VarSnippet v1 = (VarSnippet) assertDeclareFail("int a = 0.0;", "compiler.err.prob.found.req");
        badVarValue(v1);
        VarSnippet v2 = varKey(assertEval("int a = 0;", ste(v1, REJECTED, VALID, true, null)));
        assertDrop(v2, ste(MAIN_SNIPPET, VALID, DROPPED, true, null));
        badVarValue(v2);
    }

    public void testSignature1() {
        VarSnippet v1 = varKey(assertEval("und1 a;", added(RECOVERABLE_NOT_DEFINED)));
        assertVariableDeclSnippet(v1, "a", "und1", RECOVERABLE_NOT_DEFINED, VAR_DECLARATION_SUBKIND, 1, 0);
        VarSnippet v2 = varKey(assertEval("und2 a;",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(v1, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertVariableDeclSnippet(v2, "a", "und2", RECOVERABLE_NOT_DEFINED, VAR_DECLARATION_SUBKIND, 1, 0);
        TypeDeclSnippet und = classKey(assertEval("class und2 {}",
                added(VALID),
                ste(v2, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET)));
        assertVariableDeclSnippet(v2, "a", "und2", VALID, VAR_DECLARATION_SUBKIND, 0, 0);
        assertDrop(und,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(und, VALID, DROPPED, true, null),
                ste(v2, VALID, RECOVERABLE_NOT_DEFINED, true, und));
        assertVariableDeclSnippet(v2, "a", "und2", RECOVERABLE_NOT_DEFINED, VAR_DECLARATION_SUBKIND, 1, 0);
    }

    public void testSignature2() {
        VarSnippet v1 = (VarSnippet) assertDeclareFail("int a = 0.0;", "compiler.err.prob.found.req");
        assertVariableDeclSnippet(v1, "a", "int", REJECTED, SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, 0, 1);
        VarSnippet v2 = varKey(assertEval("int a = 0;",
                ste(v1, REJECTED, VALID, true, null)));
        assertVariableDeclSnippet(v2, "a", "int", VALID, SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, 0, 0);
        assertDrop(v2, ste(MAIN_SNIPPET, VALID, DROPPED, true, null));
        assertVariableDeclSnippet(v2, "a", "int", DROPPED, SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, 0, 0);
    }

    public void variables() {
        VarSnippet snx = varKey(assertEval("int x = 10;"));
        VarSnippet sny = varKey(assertEval("String y = \"hi\";"));
        VarSnippet snz = varKey(assertEval("long z;"));
        assertVariables(variable("int", "x"), variable("String", "y"), variable("long", "z"));
        assertVarValue(snx, "10");
        assertVarValue(sny, "\"hi\"");
        assertVarValue(snz, "0");
        assertActiveKeys();
    }

    public void variablesArray() {
        VarSnippet sn = varKey(assertEval("int[] a = new int[12];"));
        assertEquals(sn.typeName(), "int[]");
        assertEval("int len = a.length;", "12");
        assertVariables(variable("int[]", "a"), variable("int", "len"));
        assertActiveKeys();
    }

    public void variablesArrayOld() {
        VarSnippet sn = varKey(assertEval("int a[] = new int[12];"));
        assertEquals(sn.typeName(), "int[]");
        assertEval("int len = a.length;", "12");
        assertVariables(variable("int[]", "a"), variable("int", "len"));
        assertActiveKeys();
    }

    public void variablesRedefinition() {
        Snippet x = varKey(assertEval("int x = 10;"));
        Snippet y = varKey(assertEval("String y = \"\";", added(VALID)));
        assertVariables(variable("int", "x"), variable("String", "y"));
        assertActiveKeys();
        assertEval("long x;",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertVariables(variable("long", "x"), variable("String", "y"));
        assertActiveKeys();
        assertEval("String y;",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(y, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertVariables(variable("long", "x"), variable("String", "y"));
        assertActiveKeys();
    }

    public void variablesTemporary() {
        assertEval("int $1 = 10;", added(VALID));
        assertEval("2 * $1;", added(VALID));
        assertVariables(variable("int", "$1"), variable("int", "$2"));
        assertActiveKeys();
        assertEval("String y;", added(VALID));
        assertVariables(variable("int", "$1"), variable("int", "$2"), variable("String", "y"));
        assertActiveKeys();
    }

    public void variablesTemporaryNull() {
        assertEval("null;", added(VALID));
        assertVariables(variable("Object", "$1"));
        assertEval("(String) null;", added(VALID));
        assertVariables(variable("Object", "$1"), variable("String", "$2"));
        assertActiveKeys();
        assertEval("\"\";", added(VALID));
        assertVariables(
                variable("Object", "$1"),
                variable("String", "$2"),
                variable("String", "$3"));
        assertActiveKeys();
    }

    public void variablesClassReplace() {
        assertEval("import java.util.*;", added(VALID));
        Snippet var = varKey(assertEval("List<Integer> list = new ArrayList<>();", "[]",
                added(VALID)));
        assertVariables(variable("List<Integer>", "list"));
        assertEval("class List {}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(var, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
        assertVariables();
        assertEval("List list = new List();",
                DiagCheck.DIAG_OK, DiagCheck.DIAG_IGNORE,
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, VALID, true, null),
                ste(var, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET));
        assertVariables(variable("List", "list"));
        assertActiveKeys();
    }

    public void variablesErrors() {
        assertDeclareFail("String;", new ExpectedDiagnostic("compiler.err.cant.resolve.location", 0, 6, 0, -1, -1, Diagnostic.Kind.ERROR));
        assertNumberOfActiveVariables(0);
        assertActiveKeys();
    }

    public void variablesUnresolvedActiveFailed() {
        VarSnippet key = varKey(assertEval("und x;", added(RECOVERABLE_NOT_DEFINED)));
        assertVariableDeclSnippet(key, "x", "und", RECOVERABLE_NOT_DEFINED, VAR_DECLARATION_SUBKIND, 1, 0);
        assertUnresolvedDependencies1(key, RECOVERABLE_NOT_DEFINED, "class und");
        assertNumberOfActiveVariables(1);
        assertActiveKeys();
    }

    public void variablesUnresolvedError() {
        assertDeclareFail("und y = null;", new ExpectedDiagnostic("compiler.err.cant.resolve.location", 0, 3, 0, -1, -1, Diagnostic.Kind.ERROR));
        assertNumberOfActiveVariables(0);
        assertActiveKeys();
    }

    public void variablesMultiByteCharacterType() {
        assertEval("class \u3042 {}");
        assertEval("\u3042 \u3042 = null;", added(VALID));
        assertVariables(variable("\u3042", "\u3042"));
        assertEval("new \u3042()", added(VALID));
        assertVariables(variable("\u3042", "\u3042"), variable("\u3042", "$1"));

        assertEval("class \u3042\u3044\u3046\u3048\u304a {}");
        assertEval("\u3042\u3044\u3046\u3048\u304a \u3042\u3044\u3046\u3048\u304a = null;", added(VALID));
        assertVariables(variable("\u3042", "\u3042"), variable("\u3042", "$1"),
                variable("\u3042\u3044\u3046\u3048\u304a", "\u3042\u3044\u3046\u3048\u304a"));
        assertEval("new \u3042\u3044\u3046\u3048\u304a();");
        assertVariables(variable("\u3042", "\u3042"), variable("\u3042", "$1"),
                variable("\u3042\u3044\u3046\u3048\u304a", "\u3042\u3044\u3046\u3048\u304a"),
                variable("\u3042\u3044\u3046\u3048\u304a", "$2"));
        assertActiveKeys();
    }

    @Test(enabled = false) // TODO 8081689
    public void methodVariablesAreNotVisible() {
        Snippet foo = varKey(assertEval("int foo() {" +
                        "int x = 10;" +
                        "int y = 2 * x;" +
                        "return x * y;" +
                        "}", added(VALID)));
        assertNumberOfActiveVariables(0);
        assertActiveKeys();
        assertEval("int x = 10;", "10");
        assertEval("int foo() {" +
                        "int y = 2 * x;" +
                        "return x * y;" +
                        "}",
                ste(foo, VALID, VALID, false, null));
        assertVariables(variable("int", "x"));
        assertActiveKeys();
        assertEval("foo();", "200");
        assertVariables(variable("int", "x"), variable("int", "$1"));
        assertActiveKeys();
    }

    @Test(enabled = false) // TODO 8081689
    public void classFieldsAreNotVisible() {
        Snippet key = classKey(assertEval("class clazz {" +
                        "int x = 10;" +
                        "int y = 2 * x;" +
                        "}"));
        assertNumberOfActiveVariables(0);
        assertEval("int x = 10;", "10");
        assertActiveKeys();
        assertEval(
                "class clazz {" +
                        "int y = 2 * x;" +
                        "}",
                ste(key, VALID, VALID, true, null));
        assertVariables(variable("int", "x"));
        assertEval("new clazz().y;", "20");
        assertVariables(variable("int", "x"), variable("int", "$1"));
        assertActiveKeys();
    }

    public void multiVariables() {
        List<SnippetEvent> abc = assertEval("int a, b, c = 10;",
                DiagCheck.DIAG_OK, DiagCheck.DIAG_OK,
                chain(added(VALID)),
                chain(added(VALID)),
                chain(added(VALID)));
        Snippet a = abc.get(0).snippet();
        Snippet b = abc.get(1).snippet();
        Snippet c = abc.get(2).snippet();
        assertVariables(variable("int", "a"), variable("int", "b"), variable("int", "c"));
        assertEval("double a = 1.4, b = 8.8;", DiagCheck.DIAG_OK, DiagCheck.DIAG_OK,
                chain(ste(MAIN_SNIPPET, VALID, VALID, true, null), ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET)),
                chain(ste(MAIN_SNIPPET, VALID, VALID, true, null), ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertVariables(variable("double", "a"), variable("double", "b"), variable("int", "c"));
        assertEval("double c = a + b;",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(c, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertVariables(variable("double", "a"), variable("double", "b"), variable("double", "c"));
        assertActiveKeys();
    }

    public void syntheticVariables() {
        assertEval("assert false;");
        assertNumberOfActiveVariables(0);
        assertActiveKeys();
    }

    public void undefinedReplaceVariable() {
        Snippet key = varKey(assertEval("int d = 234;", "234"));
        assertVariables(variable("int", "d"));
        String src = "undefined d;";
        Snippet undefKey = varKey(assertEval(src,
                ste(MAIN_SNIPPET, VALID, RECOVERABLE_NOT_DEFINED, true, null),
                ste(key, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        //assertEquals(getState().source(snippet), src);
        //assertEquals(snippet, undefKey);
        assertEquals(getState().status(undefKey), RECOVERABLE_NOT_DEFINED);
        List<String> unr = getState().unresolvedDependencies((VarSnippet) undefKey);
        assertEquals(unr.size(), 1);
        assertEquals(unr.get(0), "class undefined");
        assertVariables(variable("undefined", "d"));
    }

    public void variableTypeName() {
        assertEquals(varKey(assertEval("\"x\"")).typeName(), "String");

        assertEquals(varKey(assertEval("java.util.regex.Pattern.compile(\"x\")")).typeName(), "java.util.regex.Pattern");
        assertEval("import java.util.regex.*;", added(VALID));
        assertEquals(varKey(assertEval("java.util.regex.Pattern.compile(\"x\")")).typeName(), "Pattern");

        assertEquals(varKey(assertEval("new java.util.ArrayList()")).typeName(), "java.util.ArrayList");
        assertEval("import java.util.ArrayList;", added(VALID));
        assertEquals(varKey(assertEval("new java.util.ArrayList()")).typeName(), "ArrayList");

        assertEquals(varKey(assertEval("java.util.Locale.Category.FORMAT")).typeName(), "java.util.Locale.Category");
        assertEval("import static java.util.Locale.Category;", added(VALID));
        assertEquals(varKey(assertEval("java.util.Locale.Category.FORMAT")).typeName(), "Category");
    }
}
