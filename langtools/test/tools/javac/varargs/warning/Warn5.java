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
 * @bug     6993978
 * @summary Project Coin: Annotation to reduce varargs warnings
 * @author  mcimadamore
 * @run main Warn5
 */
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class Warn5 {

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

    static class JavaSource extends SimpleJavaFileObject {

        String template = "import com.sun.tools.javac.api.*;\n" +
                          "import java.util.List;\n" +
                          "class Test {\n" +
                          "   static void test(Object o) {}\n" +
                          "   static void testArr(Object[] o) {}\n" +
                          "   #T \n #S #M { #B }\n" +
                          "}\n";

        String source;

        public JavaSource(TrustMe trustMe, SuppressLevel suppressLevel, ModifierKind modKind,
                MethodKind methKind, SignatureKind meth, BodyKind body) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replace("#T", trustMe.anno).
                    replace("#S", suppressLevel.getSuppressAnno()).
                    replace("#M", meth.getSignature(modKind, methKind)).
                    replace("#B", body.body);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
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
                                        test(sourceLevel,
                                                xlint,
                                                trustMe,
                                                suppressLevel,
                                                modKind,
                                                methKind,
                                                sig,
                                                body);
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

    static void test(SourceLevel sourceLevel, XlintOption xlint, TrustMe trustMe, SuppressLevel suppressLevel,
            ModifierKind modKind, MethodKind methKind, SignatureKind sig, BodyKind body) throws Exception {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(trustMe, suppressLevel, modKind, methKind, sig, body);
        DiagnosticChecker dc = new DiagnosticChecker();
        JavacTask ct = (JavacTask)tool.getTask(null, fm, dc,
                Arrays.asList(xlint.getXlintOption(), "-source", sourceLevel.sourceKey), null, Arrays.asList(source));
        ct.analyze();
        check(sourceLevel, dc, source, xlint, trustMe,
                suppressLevel, modKind, methKind, sig, body);
    }

    static void check(SourceLevel sourceLevel, DiagnosticChecker dc, JavaSource source,
            XlintOption xlint, TrustMe trustMe, SuppressLevel suppressLevel, ModifierKind modKind,
            MethodKind methKind, SignatureKind meth, BodyKind body) {

        boolean hasPotentiallyUnsafeBody = sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                suppressLevel != SuppressLevel.VARARGS &&
                xlint != XlintOption.NONE &&
                meth.isVarargs && !meth.isReifiableArg && body.hasAliasing &&
                (methKind == MethodKind.CONSTRUCTOR || (methKind == MethodKind.METHOD && modKind != ModifierKind.NONE));

        boolean hasPotentiallyPollutingDecl = sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.DONT_TRUST &&
                meth.isVarargs &&
                !meth.isReifiableArg &&
                xlint == XlintOption.ALL;

        boolean hasMalformedAnnoInDecl = sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                (!meth.isVarargs ||
                (modKind == ModifierKind.NONE && methKind == MethodKind.METHOD));

        boolean hasRedundantAnnoInDecl = sourceLevel == SourceLevel.JDK_7 &&
                trustMe == TrustMe.TRUST &&
                xlint != XlintOption.NONE &&
                suppressLevel != SuppressLevel.VARARGS &&
                (modKind != ModifierKind.NONE || methKind == MethodKind.CONSTRUCTOR) &&
                meth.isVarargs &&
                meth.isReifiableArg;

        if (hasPotentiallyUnsafeBody != dc.hasPotentiallyUnsafeBody ||
                hasPotentiallyPollutingDecl != dc.hasPotentiallyPollutingDecl ||
                hasMalformedAnnoInDecl != dc.hasMalformedAnnoInDecl ||
                hasRedundantAnnoInDecl != dc.hasRedundantAnnoInDecl) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nOptions: " + xlint.getXlintOption() +
                    "\nExpected potentially unsafe body warning: " + hasPotentiallyUnsafeBody +
                    "\nExpected potentially polluting decl warning: " + hasPotentiallyPollutingDecl +
                    "\nExpected malformed anno error: " + hasMalformedAnnoInDecl +
                    "\nExpected redundant anno warning: " + hasRedundantAnnoInDecl +
                    "\nFound potentially unsafe body warning: " + dc.hasPotentiallyUnsafeBody +
                    "\nFound potentially polluting decl warning: " + dc.hasPotentiallyPollutingDecl +
                    "\nFound malformed anno error: " + dc.hasMalformedAnnoInDecl +
                    "\nFound redundant anno warning: " + dc.hasRedundantAnnoInDecl);
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean hasPotentiallyUnsafeBody = false;
        boolean hasPotentiallyPollutingDecl = false;
        boolean hasMalformedAnnoInDecl = false;
        boolean hasRedundantAnnoInDecl = false;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                    if (diagnostic.getCode().contains("unsafe.use.varargs.param")) {
                        hasPotentiallyUnsafeBody = true;
                    } else if (diagnostic.getCode().contains("redundant.trustme")) {
                        hasRedundantAnnoInDecl = true;
                    }
            } else if (diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING &&
                    diagnostic.getCode().contains("varargs.non.reifiable.type")) {
                hasPotentiallyPollutingDecl = true;
            } else if (diagnostic.getKind() == Diagnostic.Kind.ERROR &&
                    diagnostic.getCode().contains("invalid.trustme")) {
                hasMalformedAnnoInDecl = true;
            }
        }
    }
}
