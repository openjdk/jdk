/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6945418 6993978
 * @summary Project Coin: Simplified Varargs Method Invocation
 * @author  mcimadamore
 * @run main Warn4
 */
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class Warn4 {

    final static Warning[] error = null;
    final static Warning[] none = new Warning[] {};
    final static Warning[] vararg = new Warning[] { Warning.VARARGS };
    final static Warning[] unchecked = new Warning[] { Warning.UNCHECKED };
    final static Warning[] both = new Warning[] { Warning.VARARGS, Warning.UNCHECKED };

    enum Warning {
        UNCHECKED("generic.array.creation"),
        VARARGS("varargs.non.reifiable.type");

        String key;

        Warning(String key) {
            this.key = key;
        }

        boolean isSuppressed(TrustMe trustMe, SourceLevel source, SuppressLevel suppressLevelClient,
                SuppressLevel suppressLevelDecl, ModifierKind modKind) {
            switch(this) {
                case VARARGS:
                    return source == SourceLevel.JDK_6 ||
                            suppressLevelDecl == SuppressLevel.UNCHECKED ||
                            trustMe == TrustMe.TRUST;
                case UNCHECKED:
                    return suppressLevelClient == SuppressLevel.UNCHECKED ||
                        (trustMe == TrustMe.TRUST && modKind != ModifierKind.NONE && source == SourceLevel.JDK_7);
            }

            SuppressLevel supLev = this == VARARGS ?
                suppressLevelDecl :
                suppressLevelClient;
            return supLev == SuppressLevel.UNCHECKED ||
                    (trustMe == TrustMe.TRUST && modKind != ModifierKind.NONE);
        }
    }

    enum SourceLevel {
        JDK_6("6"),
        JDK_7("7");

        String sourceKey;

        SourceLevel(String sourceKey) {
            this.sourceKey = sourceKey;
        }
    }

    enum TrustMe {
        DONT_TRUST(""),
        TRUST("@java.lang.SafeVarargs");

        String anno;

        TrustMe(String anno) {
            this.anno = anno;
        }
    }

    enum ModifierKind {
        NONE(" "),
        FINAL("final "),
        STATIC("static ");

        String mod;

        ModifierKind(String mod) {
            this.mod = mod;
        }
    }

    enum SuppressLevel {
        NONE(""),
        UNCHECKED("unchecked");

        String lint;

        SuppressLevel(String lint) {
            this.lint = lint;
        }

        String getSuppressAnno() {
            return "@SuppressWarnings(\"" + lint + "\")";
        }
    }

    enum Signature {
        UNBOUND("void #name(List<?>#arity arg) { #body }",
            new Warning[][] {none, none, none, none, error}),
        INVARIANT_TVAR("<Z> void #name(List<Z>#arity arg) { #body }",
            new Warning[][] {both, both, error, both, error}),
        TVAR("<Z> void #name(Z#arity arg) { #body }",
            new Warning[][] {both, both, both, both, vararg}),
        INVARIANT("void #name(List<String>#arity arg) { #body }",
            new Warning[][] {error, error, error, both, error}),
        UNPARAMETERIZED("void #name(String#arity arg) { #body }",
            new Warning[][] {error, error, error, error, none});

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
        for (SourceLevel sourceLevel : SourceLevel.values()) {
            for (TrustMe trustMe : TrustMe.values()) {
                for (SuppressLevel suppressLevelClient : SuppressLevel.values()) {
                    for (SuppressLevel suppressLevelDecl : SuppressLevel.values()) {
                        for (ModifierKind modKind : ModifierKind.values()) {
                            for (Signature vararg_meth : Signature.values()) {
                                for (Signature client_meth : Signature.values()) {
                                    if (vararg_meth.isApplicableTo(client_meth)) {
                                        test(sourceLevel,
                                                trustMe,
                                                suppressLevelClient,
                                                suppressLevelDecl,
                                                modKind,
                                                vararg_meth,
                                                client_meth);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create a single file manager and reuse it for each compile to save time.
    static StandardJavaFileManager fm = JavacTool.create().getStandardFileManager(null, null, null);

    static void test(SourceLevel sourceLevel, TrustMe trustMe, SuppressLevel suppressLevelClient,
            SuppressLevel suppressLevelDecl, ModifierKind modKind, Signature vararg_meth, Signature client_meth) throws Exception {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(trustMe, suppressLevelClient, suppressLevelDecl, modKind, vararg_meth, client_meth);
        DiagnosticChecker dc = new DiagnosticChecker();
        JavacTask ct = (JavacTask)tool.getTask(null, fm, dc,
                Arrays.asList("-Xlint:unchecked", "-source", sourceLevel.sourceKey),
                null, Arrays.asList(source));
        ct.generate(); //to get mandatory notes
        check(dc.warnings, sourceLevel,
                new boolean[] {vararg_meth.giveUnchecked(client_meth),
                               vararg_meth.giveVarargs(client_meth)},
                source, trustMe, suppressLevelClient, suppressLevelDecl, modKind);
    }

    static void check(Set<Warning> warnings, SourceLevel sourceLevel, boolean[] warnArr, JavaSource source,
            TrustMe trustMe, SuppressLevel suppressLevelClient, SuppressLevel suppressLevelDecl, ModifierKind modKind) {
        boolean badOutput = false;
        for (Warning wkind : Warning.values()) {
            boolean isSuppressed = wkind.isSuppressed(trustMe, sourceLevel,
                    suppressLevelClient, suppressLevelDecl, modKind);
            System.out.println("SUPPRESSED = " + isSuppressed);
            badOutput |= (warnArr[wkind.ordinal()] && !isSuppressed) != warnings.contains(wkind);
        }
        if (badOutput) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nExpected unchecked warning: " + warnArr[0] +
                    "\nExpected unsafe vararg warning: " + warnArr[1] +
                    "\nWarnings: " + warnings +
                    "\nSource level: " + sourceLevel);
        }
    }

    static class JavaSource extends SimpleJavaFileObject {

        String source;

        public JavaSource(TrustMe trustMe, SuppressLevel suppressLevelClient, SuppressLevel suppressLevelDecl,
                ModifierKind modKind, Signature vararg_meth, Signature client_meth) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            String meth1 = vararg_meth.template.replace("#arity", "...");
            meth1 = meth1.replace("#name", "m");
            meth1 = meth1.replace("#body", "");
            meth1 = trustMe.anno + "\n" + suppressLevelDecl.getSuppressAnno() + modKind.mod + meth1;
            String meth2 = client_meth.template.replace("#arity", "");
            meth2 = meth2.replace("#name", "test");
            meth2 = meth2.replace("#body", "m(arg);");
            meth2 = suppressLevelClient.getSuppressAnno() + meth2;
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

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING ||
                    diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                if (diagnostic.getCode().contains(Warning.VARARGS.key)) {
                    warnings.add(Warning.VARARGS);
                } else if(diagnostic.getCode().contains(Warning.UNCHECKED.key)) {
                    warnings.add(Warning.UNCHECKED);
                }
            }
        }
    }
}
