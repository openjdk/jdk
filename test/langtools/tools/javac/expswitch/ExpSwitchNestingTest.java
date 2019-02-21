/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.List;

import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import tools.javac.combo.JavacTemplateTestBase;

import static java.util.stream.Collectors.toList;

@Test
public class ExpSwitchNestingTest extends JavacTemplateTestBase {
    private static final String RUNNABLE = "Runnable r = () -> { # };";
    private static final String INT_FN = "java.util.function.IntSupplier r = () -> { # };";
    private static final String LABEL = "label: #";
    private static final String DEF_LABEL_VAR = "int label = 0; { # }";
    private static final String FOR = "for (int i=0; i<10; i++) { # }";
    private static final String FOR_EACH = "for (int i : new int[] {}) { # }";
    private static final String WHILE = "while (cond) { # }";
    private static final String DO = "do { # } while (cond);";
    private static final String SSWITCH = "switch (x) { case 0: # };";
    private static final String ESWITCH_Z = "int res = switch (x) { case 0 -> { # } default -> 0; };";
    private static final String ESWITCH_S = "String res_string = switch (x) { case 0 -> { # } default -> \"default\"; };";
    private static final String INT_FN_ESWITCH = "java.util.function.IntSupplier r = switch (x) { case 0 -> { # } default -> null; };";
    private static final String INT_ESWITCH_DEFAULT = "int res = switch (x) { default -> { # } };";
    private static final String IF = "if (cond) { # } else throw new RuntimeException();";
    private static final String BLOCK = "{ # }";
    private static final String BREAK_Z = "break 0;";
    private static final String BREAK_S = "break \"hello world\";";
    private static final String BREAK_INT_FN = "break () -> 0 ;";
    private static final String BREAK_N = "break;";
    private static final String BREAK_L = "break label;";
    private static final String RETURN_Z = "return 0;";
    private static final String RETURN_N = "return;";
    private static final String RETURN_S = "return \"Hello\";";
    private static final String CONTINUE_N = "continue;";
    private static final String CONTINUE_L = "continue label;";
    private static final String NOTHING = "System.out.println();";

    // containers that do not require exhaustiveness
    private static final List<String> CONTAINERS
            = List.of(RUNNABLE, FOR, WHILE, DO, SSWITCH, IF, BLOCK);
    // containers that do not require exhaustiveness that are statements
    private static final List<String> CONTAINER_STATEMENTS
            = List.of(FOR, WHILE, DO, SSWITCH, IF, BLOCK);

    @AfterMethod
    public void dumpTemplateIfError(ITestResult result) {
        // Make sure offending template ends up in log file on failure
        if (!result.isSuccess()) {
            System.err.printf("Diagnostics: %s%nTemplate: %s%n", diags.errorKeys(), sourceFiles.stream().map(p -> p.snd).collect(toList()));
        }
    }

    private static String[] PREVIEW_OPTIONS = {"--enable-preview", "-source",
                                               Integer.toString(Runtime.version().feature())};

    private void program(String... constructs) {
        String s = "class C { static boolean cond = false; static int x = 0; void m() { # } }";
        for (String c : constructs)
            s = s.replace("#", c);
        addSourceFile("C.java", new StringTemplate(s));
    }

    private void assertOK(String... constructs) {
        reset();
        addCompileOptions(PREVIEW_OPTIONS);
        program(constructs);
        try {
            compile();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertCompileSucceeded();
    }

    private void assertOKWithWarning(String warning, String... constructs) {
        reset();
        addCompileOptions(PREVIEW_OPTIONS);
        program(constructs);
        try {
            compile();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertCompileSucceededWithWarning(warning);
    }

    private void assertFail(String expectedDiag, String... constructs) {
        reset();
        addCompileOptions(PREVIEW_OPTIONS);
        program(constructs);
        try {
            compile();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertCompileFailed(expectedDiag);
    }

    public void testReallySimpleCases() {
        for (String s : CONTAINERS)
            assertOK(s, NOTHING);
        for (String s : CONTAINER_STATEMENTS)
            assertOK(LABEL, s, NOTHING);
    }

    public void testLambda() {
        assertOK(RUNNABLE, RETURN_N);
        assertOK(RUNNABLE, NOTHING);
        assertOK(INT_FN, RETURN_Z);
        assertFail("compiler.err.break.outside.switch.loop", RUNNABLE, BREAK_N);
        assertFail("compiler.err.break.complex.value.no.switch.expression", RUNNABLE, BREAK_Z);
        assertFail("compiler.err.break.complex.value.no.switch.expression", RUNNABLE, BREAK_S);
        assertFail("compiler.err.break.outside.switch.loop", INT_FN, BREAK_N);
        assertFail("compiler.err.break.complex.value.no.switch.expression", INT_FN, BREAK_Z);
        assertFail("compiler.err.break.complex.value.no.switch.expression", INT_FN, BREAK_S);
        assertFail("compiler.err.cont.outside.loop", RUNNABLE, CONTINUE_N);
        assertFail("compiler.err.undef.label", RUNNABLE, BREAK_L);
        assertFail("compiler.err.undef.label", RUNNABLE, CONTINUE_L);
        assertFail("compiler.err.cont.outside.loop", INT_FN, CONTINUE_N);
        assertFail("compiler.err.undef.label", INT_FN, BREAK_L);
        assertFail("compiler.err.undef.label", INT_FN, CONTINUE_L);
        assertFail("compiler.err.undef.label", LABEL, BLOCK, RUNNABLE, BREAK_L);
        assertFail("compiler.err.undef.label", LABEL, BLOCK, RUNNABLE, CONTINUE_L);
        assertFail("compiler.err.undef.label", LABEL, BLOCK, INT_FN, BREAK_L);
        assertFail("compiler.err.undef.label", LABEL, BLOCK, INT_FN, CONTINUE_L);
    }

    public void testEswitch() {
        //Int-valued switch expressions
        assertOK(ESWITCH_Z, BREAK_Z);
        assertOK(LABEL, BLOCK, ESWITCH_Z, BREAK_Z);
        assertFail("compiler.err.break.missing.value", ESWITCH_Z, BREAK_N);
        assertFail("compiler.err.prob.found.req", ESWITCH_Z, BREAK_S);
        assertFail("compiler.err.cant.resolve.location", ESWITCH_Z, BREAK_L);
        assertFail("compiler.err.break.outside.switch.expression", LABEL, BLOCK, ESWITCH_Z, BREAK_L);
        assertFail("compiler.err.undef.label", ESWITCH_Z, CONTINUE_L);
        assertFail("compiler.err.cont.outside.loop", ESWITCH_Z, CONTINUE_N);
        assertFail("compiler.err.return.outside.switch.expression", ESWITCH_Z, RETURN_N);
        assertFail("compiler.err.return.outside.switch.expression", ESWITCH_Z, RETURN_Z);

        assertOK(INT_ESWITCH_DEFAULT, BREAK_Z);
        assertFail("compiler.err.break.missing.value", INT_ESWITCH_DEFAULT, BREAK_N);
        assertFail("compiler.err.prob.found.req", INT_ESWITCH_DEFAULT, BREAK_S);
        assertFail("compiler.err.cant.resolve.location", INT_ESWITCH_DEFAULT, BREAK_L);


        // String-valued switch expressions
        assertOK(ESWITCH_S, BREAK_S);
        assertOK(LABEL, BLOCK, ESWITCH_S, BREAK_S);
        assertFail("compiler.err.break.missing.value", ESWITCH_S, BREAK_N);
        assertFail("compiler.err.prob.found.req", ESWITCH_S, BREAK_Z);
        assertFail("compiler.err.cant.resolve.location", ESWITCH_S, BREAK_L);
        assertFail("compiler.err.break.outside.switch.expression", LABEL, BLOCK, ESWITCH_S, BREAK_L);
        assertFail("compiler.err.undef.label", ESWITCH_S, CONTINUE_L);
        assertFail("compiler.err.cont.outside.loop", ESWITCH_S, CONTINUE_N);
        assertFail("compiler.err.return.outside.switch.expression", ESWITCH_S, RETURN_N);
        assertFail("compiler.err.return.outside.switch.expression", ESWITCH_S, RETURN_S);
        // Function-valued switch expression
        assertOK(INT_FN_ESWITCH, BREAK_INT_FN);
        assertFail("compiler.err.break.missing.value", INT_FN_ESWITCH, BREAK_N);
        assertFail("compiler.err.prob.found.req", INT_FN_ESWITCH, BREAK_Z);
        assertFail("compiler.err.prob.found.req", INT_FN_ESWITCH, BREAK_S);

        assertFail("compiler.err.cant.resolve.location", INT_FN_ESWITCH, BREAK_L);
        assertFail("compiler.err.break.outside.switch.expression", LABEL, BLOCK, INT_FN_ESWITCH, BREAK_L);
        assertFail("compiler.err.undef.label", INT_FN_ESWITCH, CONTINUE_L);
        assertFail("compiler.err.cont.outside.loop", INT_FN_ESWITCH, CONTINUE_N);
        assertFail("compiler.err.return.outside.switch.expression", INT_FN_ESWITCH, RETURN_N);
        assertFail("compiler.err.return.outside.switch.expression", INT_FN_ESWITCH, RETURN_S);

    }

    public void testNestedInExpSwitch() {
        assertOK(ESWITCH_Z, IF,     BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  BREAK_Z);
        //
        assertOK(ESWITCH_Z, IF,     IF,     BREAK_Z);
        assertOK(ESWITCH_Z, IF,     BLOCK,  BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  IF,     BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  BLOCK,  BREAK_Z);
        //
        assertOK(ESWITCH_Z, IF,     IF,     IF,     BREAK_Z);
        assertOK(ESWITCH_Z, IF,     IF,     BLOCK,  BREAK_Z);
        assertOK(ESWITCH_Z, IF,     BLOCK,  IF,     BREAK_Z);
        assertOK(ESWITCH_Z, IF,     BLOCK,  BLOCK,  BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  IF,     IF,     BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  IF,     BLOCK,  BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  BLOCK,  IF,     BREAK_Z);
        assertOK(ESWITCH_Z, BLOCK,  BLOCK,  BLOCK,  BREAK_Z);
        //
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, SSWITCH, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, FOR, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, WHILE, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, DO, BREAK_Z);
        assertFail("compiler.err.break.complex.value.no.switch.expression", ESWITCH_Z, INT_FN, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, SSWITCH, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, FOR, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, WHILE, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, DO, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, BLOCK, SSWITCH, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, BLOCK, FOR, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, BLOCK, WHILE, IF, BREAK_Z);
        assertFail("compiler.err.break.expr.not.immediate", ESWITCH_Z, BLOCK, DO, IF, BREAK_Z);
    }

    public void testBreakExpressionLabelDisambiguation() {
        assertOK(DEF_LABEL_VAR, ESWITCH_Z, BREAK_L);
        assertFail("compiler.err.break.ambiguous.target", LABEL, FOR, BLOCK, DEF_LABEL_VAR, ESWITCH_Z, BREAK_L);
        assertFail("compiler.err.break.ambiguous.target", DEF_LABEL_VAR, ESWITCH_Z, LABEL, FOR, BREAK_L); //label break
        assertFail("compiler.err.break.ambiguous.target", DEF_LABEL_VAR, LABEL, BLOCK, ESWITCH_Z, BREAK_L); //expression break
        //
    }

    public void testFunReturningSwitchExp() {
        assertOK(INT_FN_ESWITCH, BREAK_INT_FN);
    }

    public void testContinueLoops() {
        assertOK(LABEL, FOR, CONTINUE_L);
        assertOK(LABEL, FOR_EACH, CONTINUE_L);
        assertOK(LABEL, WHILE, CONTINUE_L);
        assertOK(LABEL, DO, CONTINUE_L);
        assertFail("compiler.err.not.loop.label", LABEL, CONTINUE_L);
    }
}
