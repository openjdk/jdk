/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6945418 6993978 8006694
 * @summary Project Coin: Simplified Varargs Method Invocation
 *  temporarily workaround combo tests are causing time out in several platforms
 * @author  mcimadamore
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm Warn4
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import com.sun.source.util.JavacTask;

public class Warn4
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    final static Warning[] error = null;
    final static Warning[] none = new Warning[] {};
    final static Warning[] vararg = new Warning[] { Warning.VARARGS };
    final static Warning[] unchecked = new Warning[] { Warning.UNCHECKED };
    final static Warning[] both =
            new Warning[] { Warning.VARARGS, Warning.UNCHECKED };

    enum Warning {
        UNCHECKED("generic.array.creation"),
        VARARGS("varargs.non.reifiable.type");

        String key;

        Warning(String key) {
            this.key = key;
        }

        boolean isSuppressed(TrustMe trustMe, SourceLevel source,
                SuppressLevel suppressLevelClient,
                SuppressLevel suppressLevelDecl,
                ModifierKind modKind) {
            switch(this) {
                case VARARGS:
                    return source == SourceLevel.JDK_6 ||
                            suppressLevelDecl == SuppressLevel.UNCHECKED ||
                            trustMe == TrustMe.TRUST;
                case UNCHECKED:
                    return suppressLevelClient == SuppressLevel.UNCHECKED ||
                        (trustMe == TrustMe.TRUST && modKind !=
                            ModifierKind.NONE && source == SourceLevel.JDK_7);
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
                                        pool.execute(new Warn4(sourceLevel,
                                                trustMe,
                                                suppressLevelClient,
                                                suppressLevelDecl,
                                                modKind,
                                                vararg_meth,
                                                client_meth));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    SourceLevel sourceLevel;
    TrustMe trustMe;
    SuppressLevel suppressLevelClient;
    SuppressLevel suppressLevelDecl;
    ModifierKind modKind;
    Signature vararg_meth;
    Signature client_meth;
    DiagnosticChecker diagChecker;

    public Warn4(SourceLevel sourceLevel, TrustMe trustMe,
            SuppressLevel suppressLevelClient, SuppressLevel suppressLevelDecl,
            ModifierKind modKind, Signature vararg_meth, Signature client_meth) {
        this.sourceLevel = sourceLevel;
        this.trustMe = trustMe;
        this.suppressLevelClient = suppressLevelClient;
        this.suppressLevelDecl = suppressLevelDecl;
        this.modKind = modKind;
        this.vararg_meth = vararg_meth;
        this.client_meth = client_meth;
        this.diagChecker = new DiagnosticChecker();
    }

    @Override
    public void run() {
        int id = checkCount.incrementAndGet();
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(id);
        JavacTask ct = (JavacTask)tool.getTask(null, fm.get(), diagChecker,
                Arrays.asList("-Xlint:unchecked", "-source", sourceLevel.sourceKey),
                null, Arrays.asList(source));
        ct.call(); //to get mandatory notes
        check(source, new boolean[] {vararg_meth.giveUnchecked(client_meth),
                               vararg_meth.giveVarargs(client_meth)});
    }

    void check(JavaSource source, boolean[] warnArr) {
        boolean badOutput = false;
        for (Warning wkind : Warning.values()) {
            boolean isSuppressed = wkind.isSuppressed(trustMe, sourceLevel,
                    suppressLevelClient, suppressLevelDecl, modKind);
            badOutput |= (warnArr[wkind.ordinal()] && !isSuppressed) !=
                    diagChecker.warnings.contains(wkind);
        }
        if (badOutput) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nExpected unchecked warning: " + warnArr[0] +
                    "\nExpected unsafe vararg warning: " + warnArr[1] +
                    "\nWarnings: " + diagChecker.warnings +
                    "\nSource level: " + sourceLevel);
        }
    }

    class JavaSource extends SimpleJavaFileObject {

        String source;

        public JavaSource(int id) {
            super(URI.create(String.format("myfo:/Test%d.java", id)),
                    JavaFileObject.Kind.SOURCE);
            String meth1 = vararg_meth.template.replace("#arity", "...");
            meth1 = meth1.replace("#name", "m");
            meth1 = meth1.replace("#body", "");
            meth1 = trustMe.anno + "\n" + suppressLevelDecl.getSuppressAnno() +
                    modKind.mod + meth1;
            String meth2 = client_meth.template.replace("#arity", "");
            meth2 = meth2.replace("#name", "test");
            meth2 = meth2.replace("#body", "m(arg);");
            meth2 = suppressLevelClient.getSuppressAnno() + meth2;
            source = String.format("import java.util.List;\n" +
                     "class Test%s {\n %s \n %s \n } \n", id, meth1, meth2);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

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
