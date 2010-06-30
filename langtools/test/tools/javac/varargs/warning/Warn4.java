/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     6945418
 * @summary Project Coin: Simplified Varargs Method Invocation
 * @author  mcimadamore
 * @run main Warn4
 */
import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class Warn4 {

    final static Warning[] error = null;
    final static Warning[] none = new Warning[] {};
    final static Warning[] vararg = new Warning[] { Warning.VARARGS };
    final static Warning[] unchecked = new Warning[] { Warning.UNCHECKED };
    final static Warning[] both = new Warning[] { Warning.VARARGS, Warning.UNCHECKED };

    enum Warning {
        UNCHECKED("unchecked"),
        VARARGS("varargs");

        String category;

        Warning(String category) {
            this.category = category;
        }

        boolean isEnabled(XlintOption xlint, SuppressLevel suppressLevel) {
            return Arrays.asList(xlint.enabledWarnings).contains(this);
        }

        boolean isSuppressed(SuppressLevel suppressLevel) {
            return Arrays.asList(suppressLevel.suppressedWarnings).contains(VARARGS);
        }
    }

    enum XlintOption {
        NONE(),
        UNCHECKED(Warning.UNCHECKED),
        VARARGS(Warning.VARARGS),
        ALL(Warning.UNCHECKED, Warning.VARARGS);

        Warning[] enabledWarnings;

        XlintOption(Warning... enabledWarnings) {
            this.enabledWarnings = enabledWarnings;
        }

        String getXlintOption() {
            StringBuilder buf = new StringBuilder();
            String sep = "";
            for (Warning w : enabledWarnings) {
                buf.append(sep);
                buf.append(w.category);
                sep=",";
            }
            return "-Xlint:" +
                    (this == NONE ? "none" : buf.toString());
        }
    }

    enum SuppressLevel {
        NONE(),
        UNCHECKED(Warning.UNCHECKED),
        VARARGS(Warning.VARARGS),
        ALL(Warning.UNCHECKED, Warning.VARARGS);

        Warning[] suppressedWarnings;

        SuppressLevel(Warning... suppressedWarnings) {
            this.suppressedWarnings = suppressedWarnings;
        }

        String getSuppressAnnotation() {
            StringBuilder buf = new StringBuilder();
            String sep = "";
            for (Warning w : suppressedWarnings) {
                buf.append(sep);
                buf.append("\"");
                buf.append(w.category);
                buf.append("\"");
                sep=",";
            }
            return this == NONE ? "" :
                "@SuppressWarnings({" + buf.toString() + "})";
        }
    }

    enum Signature {

        EXTENDS_TVAR("<Z> void #name(List<? extends Z>#arity arg) { #body }",
            new Warning[][] {both, both, both, both, error, both, both, both, error}),
        SUPER_TVAR("<Z> void #name(List<? super Z>#arity arg) { #body }",
            new Warning[][] {error, both, error, both, error, error, both, both, error}),
        UNBOUND("void #name(List<?>#arity arg) { #body }",
            new Warning[][] {none, none, none, none, none, none, none, none, error}),
        INVARIANT_TVAR("<Z> void #name(List<Z>#arity arg) { #body }",
            new Warning[][] {both, both, both, both, error, both, both, both, error}),
        TVAR("<Z> void #name(Z#arity arg) { #body }",
            new Warning[][] {both, both, both, both, both, both, both, both, vararg}),
        EXTENDS("void #name(List<? extends String>#arity arg) { #body }",
            new Warning[][] {error, error, error, error, error, both, error, both, error}),
        SUPER("void #name(List<? super String>#arity arg) { #body }",
            new Warning[][] {error, error, error, error, error, error, both, both, error}),
        INVARIANT("void #name(List<String>#arity arg) { #body }",
            new Warning[][] {error, error, error, error, error, error, error, both, error}),
        UNPARAMETERIZED("void #name(String#arity arg) { #body }",
            new Warning[][] {error, error, error, error, error, error, error, error, none});

        String template;
        Warning[][] warnings;

        Signature(String template, Warning[][] warnings) {
            this.template = template;
            this.warnings = warnings;
        }

        boolean isApplicableTo(Signature other) {
            return warnings[other.ordinal()] != null;
        }

        boolean giveUnchecked(Signature other) {
            return warnings[other.ordinal()] == unchecked ||
                    warnings[other.ordinal()] == both;
        }

        boolean giveVarargs(Signature other) {
            return warnings[other.ordinal()] == vararg ||
                    warnings[other.ordinal()] == both;
        }
    }

    public static void main(String... args) throws Exception {
        for (XlintOption xlint : XlintOption.values()) {
            for (SuppressLevel suppressLevel : SuppressLevel.values()) {
                for (Signature vararg_meth : Signature.values()) {
                    for (Signature client_meth : Signature.values()) {
                        if (vararg_meth.isApplicableTo(client_meth)) {
                            test(xlint,
                                    suppressLevel,
                                    vararg_meth,
                                    client_meth);
                        }
                    }
                }
            }
        }
    }

    static void test(XlintOption xlint, SuppressLevel suppressLevel,
            Signature vararg_meth, Signature client_meth) throws Exception {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(suppressLevel, vararg_meth, client_meth);
        DiagnosticChecker dc = new DiagnosticChecker();
        JavacTask ct = (JavacTask)tool.getTask(null, null, dc,
                Arrays.asList(xlint.getXlintOption()), null, Arrays.asList(source));
        ct.generate(); //to get mandatory notes
        check(dc.warnings,
                dc.notes,
                new boolean[] {vararg_meth.giveUnchecked(client_meth),
                               vararg_meth.giveVarargs(client_meth)},
                source, xlint, suppressLevel);
    }

    static void check(Set<Warning> warnings, Set<Warning> notes, boolean[] warnArr, JavaSource source, XlintOption xlint, SuppressLevel suppressLevel) {
        boolean badOutput = false;
        for (Warning wkind : Warning.values()) {
            badOutput |= (warnArr[wkind.ordinal()] && !wkind.isSuppressed(suppressLevel)) !=
                    (wkind.isEnabled(xlint, suppressLevel) ?
                        warnings.contains(wkind) :
                        notes.contains(wkind));
        }
        if (badOutput) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nOptions: " + xlint.getXlintOption() +
                    "\nExpected unchecked warning: " + warnArr[0] +
                    "\nExpected unsafe vararg warning: " + warnArr[1] +
                    "\nWarnings: " + warnings +
                    "\nNotes: " + notes);
        }
    }

    static class JavaSource extends SimpleJavaFileObject {

        String source;

        public JavaSource(SuppressLevel suppressLevel, Signature vararg_meth, Signature client_meth) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            String meth1 = vararg_meth.template.replace("#arity", "...");
            meth1 = meth1.replace("#name", "m");
            meth1 = meth1.replace("#body", "");
            meth1 = suppressLevel.getSuppressAnnotation() + meth1;
            String meth2 = client_meth.template.replace("#arity", "");
            meth2 = meth2.replace("#name", "test");
            meth2 = meth2.replace("#body", "m(arg);");
            source = "import java.util.List;\n" +
               "class Test {\n" + meth1 +
               "\n" + meth2 + "\n}\n";
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        Set<Warning> warnings = new HashSet<>();
        Set<Warning> notes = new HashSet<>();

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING ||
                    diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                warnings.add(diagnostic.getCode().contains("varargs") ?
                    Warning.VARARGS :
                    Warning.UNCHECKED);
            }
            else if (diagnostic.getKind() == Diagnostic.Kind.NOTE) {
                notes.add(diagnostic.getCode().contains("varargs") ?
                    Warning.VARARGS :
                    Warning.UNCHECKED);
            }
        }
    }
}
