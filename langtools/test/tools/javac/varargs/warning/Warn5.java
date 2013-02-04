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
 * @bug     6993978 7097436 8006694
 * @summary Project Coin: Annotation to reduce varargs warnings
 *  temporarily workaround combo tests are causing time out in several platforms
 * @author  mcimadamore
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm Warn5
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import com.sun.source.util.JavacTask;

public class Warn5
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum XlintOption {
        NONE("none"),
        ALL("all");

        String opt;

        XlintOption(String opt) {
            this.opt = opt;
        }

        String getXlintOption() {
            return "-Xlint:" + opt;
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

    enum SuppressLevel {
        NONE,
        VARARGS;

        String getSuppressAnno() {
            return this == VARARGS ?
                "@SuppressWarnings(\"varargs\")" :
                "";
        }
    }

    enum ModifierKind {
        NONE(""),
        FINAL("final"),
        STATIC("static");

        String mod;

        ModifierKind(String mod) {
            this.mod = mod;
        }
    }

    enum MethodKind {
        METHOD("void m"),
        CONSTRUCTOR("Test");

        String name;

        MethodKind(String name) {
            this.name = name;
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

    enum SignatureKind {
        VARARGS_X("#K <X>#N(X... x)", false, true),
        VARARGS_STRING("#K #N(String... x)", true, true),
        ARRAY_X("#K <X>#N(X[] x)", false, false),
        ARRAY_STRING("#K #N(String[] x)", true, false);

        String stub;
        boolean isReifiableArg;
        boolean isVarargs;

        SignatureKind(String stub, boolean isReifiableArg, boolean isVarargs) {
            this.stub = stub;
            this.isReifiableArg = isReifiableArg;
            this.isVarargs = isVarargs;
        }

        String getSignature(ModifierKind modKind, MethodKind methKind) {
            return methKind != MethodKind.CONSTRUCTOR ?
                stub.replace("#K", modKind.mod).replace("#N", methKind.name) :
                stub.replace("#K", "").replace("#N", methKind.name);
        }
    }

    enum BodyKind {
        ASSIGN("Object o = x;", true),
        CAST("Object o = (Object)x;", true),
        METH("test(x);", true),
        PRINT("System.out.println(x.toString());", false),
        ARRAY_ASSIGN("Object[] o = x;", true),
        ARRAY_CAST("Object[] o = (Object[])x;", true),
        ARRAY_METH("testArr(x);", true);

        String body;
        boolean hasAliasing;

        BodyKind(String body, boolean hasAliasing) {
            this.body = body;
            this.hasAliasing = hasAliasing;
        }
    }

    enum WarningKind {
        UNSAFE_BODY,
        UNSAFE_DECL,
        MALFORMED_SAFEVARARGS,
        REDUNDANT_SAFEVARARGS;
    }

    public static void main(String... args) throws Exception {
        for (SourceLevel sourceLevel : SourceLevel.values()) {
            for (XlintOption xlint : XlintOption.values()) {
                for (TrustMe trustMe : TrustMe.values()) {
                    for (SuppressLevel suppressLevel : SuppressLevel.values()) {
                        for (ModifierKind modKind : ModifierKind.values()) {
                            for (MethodKind methKind : MethodKind.values()) {
                                for (SignatureKind sig : SignatureKind.values()) {
                                    for (BodyKind body : BodyKind.values()) {
                                        pool.execute(new Warn5(sourceLevel,
                                                xlint, trustMe, suppressLevel,
                                                modKind, methKind, sig, body));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec(false);
    }

    final SourceLevel sourceLevel;
    final XlintOption xlint;
    final TrustMe trustMe;
    final SuppressLevel suppressLevel;
    final ModifierKind modKind;
    final MethodKind methKind;
    final SignatureKind sig;
    final BodyKind body;
    final JavaSource source;
    final DiagnosticChecker dc;

    public Warn5(SourceLevel sourceLevel, XlintOption xlint, TrustMe trustMe,
            SuppressLevel suppressLevel, ModifierKind modKind,
            MethodKind methKind, SignatureKind sig, BodyKind body) {
        this.sourceLevel = sourceLevel;
        this.xlint = xlint;
        this.trustMe = trustMe;
        this.suppressLevel = suppressLevel;
        this.modKind = modKind;
        this.methKind = methKind;
        this.sig = sig;
        this.body = body;
        this.source = new JavaSource();
        this.dc = new DiagnosticChecker();
    }

    @Override
    public void run() {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavacTask ct = (JavacTask)tool.getTask(null, fm.get(), dc,
                Arrays.asList(xlint.getXlintOption(),
                    "-source", sourceLevel.sourceKey),
                null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable t) {
            processException(t);
        }
        check();
    }

    void check() {

        EnumSet<WarningKind> expectedWarnings =
                EnumSet.noneOf(WarningKind.class);

        if (sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                suppressLevel != SuppressLevel.VARARGS &&
                xlint != XlintOption.NONE &&
                sig.isVarargs &&
                !sig.isReifiableArg &&
                body.hasAliasing &&
                (methKind == MethodKind.CONSTRUCTOR ||
                (methKind == MethodKind.METHOD &&
                modKind != ModifierKind.NONE))) {
            expectedWarnings.add(WarningKind.UNSAFE_BODY);
        }

        if (sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.DONT_TRUST &&
                sig.isVarargs &&
                !sig.isReifiableArg &&
                xlint == XlintOption.ALL) {
            expectedWarnings.add(WarningKind.UNSAFE_DECL);
        }

        if (sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                (!sig.isVarargs ||
                (modKind == ModifierKind.NONE &&
                methKind == MethodKind.METHOD))) {
            expectedWarnings.add(WarningKind.MALFORMED_SAFEVARARGS);
        }

        if (sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                xlint != XlintOption.NONE &&
                suppressLevel != SuppressLevel.VARARGS &&
                (modKind != ModifierKind.NONE ||
                methKind == MethodKind.CONSTRUCTOR) &&
                sig.isVarargs &&
                sig.isReifiableArg) {
            expectedWarnings.add(WarningKind.REDUNDANT_SAFEVARARGS);
        }

        if (!expectedWarnings.containsAll(dc.warnings) ||
                !dc.warnings.containsAll(expectedWarnings)) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nOptions: " + xlint.getXlintOption() +
                    "\nExpected warnings: " + expectedWarnings +
                    "\nFound warnings: " + dc.warnings);
        }
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "import com.sun.tools.javac.api.*;\n" +
                          "import java.util.List;\n" +
                          "class Test {\n" +
                          "   static void test(Object o) {}\n" +
                          "   static void testArr(Object[] o) {}\n" +
                          "   #T \n #S #M { #B }\n" +
                          "}\n";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replace("#T", trustMe.anno).
                    replace("#S", suppressLevel.getSuppressAnno()).
                    replace("#M", sig.getSignature(modKind, methKind)).
                    replace("#B", body.body);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        EnumSet<WarningKind> warnings = EnumSet.noneOf(WarningKind.class);

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                    if (diagnostic.getCode().
                            contains("unsafe.use.varargs.param")) {
                        setWarning(WarningKind.UNSAFE_BODY);
                    } else if (diagnostic.getCode().
                            contains("redundant.trustme")) {
                        setWarning(WarningKind.REDUNDANT_SAFEVARARGS);
                    }
            } else if (diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING &&
                    diagnostic.getCode().
                        contains("varargs.non.reifiable.type")) {
                setWarning(WarningKind.UNSAFE_DECL);
            } else if (diagnostic.getKind() == Diagnostic.Kind.ERROR &&
                    diagnostic.getCode().contains("invalid.trustme")) {
                setWarning(WarningKind.MALFORMED_SAFEVARARGS);
            }
        }

        void setWarning(WarningKind wk) {
            if (!warnings.add(wk)) {
                throw new AssertionError("Duplicate warning of kind " +
                        wk + " in source:\n" + source);
            }
        }

        boolean hasWarning(WarningKind wk) {
            return warnings.contains(wk);
        }
    }

}
