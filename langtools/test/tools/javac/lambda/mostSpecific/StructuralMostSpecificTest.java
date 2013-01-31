/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003280 8006694
 * @summary Add lambda tests
 *  Automatic test for checking correctness of structural most specific test routine
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm/timeout=600 StructuralMostSpecificTest
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.util.JCDiagnostic;

public class StructuralMostSpecificTest
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum RetTypeKind {
        SHORT("short"),
        INT("int"),
        OBJECT("Object"),
        INTEGER("Integer"),
        VOID("void"),
        J_L_VOID("Void");

        String retTypeStr;

        RetTypeKind(String retTypeStr) {
            this.retTypeStr = retTypeStr;
        }

        boolean moreSpecificThan(RetTypeKind rk) {
            return moreSpecificThan[this.ordinal()][rk.ordinal()];
        }

        static boolean[][] moreSpecificThan = {
                //              SHORT |  INT  | OBJECT | INTEGER | VOID  | J_L_VOID
                /* SHORT */   { true  , true  , true   , false   , false , false },
                /* INT */     { false , true  , true   , true    , false , false },
                /* OBJECT */  { false , false , true   , false   , false , false },
                /* INTEGER */ { false , false , true   , true    , false , false },
                /* VOID */    { false , false , false  , false   , true  , true  },
                /* J_L_VOID */{ false , false , true   , false   , false , true  } };
    }

    enum ArgTypeKind {
        SHORT("short"),
        INT("int"),
        BOOLEAN("boolean"),
        OBJECT("Object"),
        INTEGER("Integer"),
        DOUBLE("Double");

        String argTypeStr;

        ArgTypeKind(String typeStr) {
            this.argTypeStr = typeStr;
        }
    }

    enum ExceptionKind {
        NONE(""),
        EXCEPTION("throws Exception"),
        SQL_EXCEPTION("throws java.sql.SQLException"),
        IO_EXCEPTION("throws java.io.IOException");

        String exceptionStr;

        ExceptionKind(String exceptionStr) {
            this.exceptionStr = exceptionStr;
        }
    }

    enum LambdaReturnKind {
        VOID("return;"),
        SHORT("return (short)0;"),
        INT("return 0;"),
        INTEGER("return (Integer)null;"),
        NULL("return null;");

        String retStr;

        LambdaReturnKind(String retStr) {
            this.retStr = retStr;
        }

        boolean compatibleWith(RetTypeKind rk) {
            return compatibleWith[rk.ordinal()][ordinal()];
        }

        static boolean[][] compatibleWith = {
                //              VOID  | SHORT | INT     | INTEGER | NULL
                /* SHORT */   { false , true  , false   , false   , false },
                /* INT */     { false , true  , true    , true    , false },
                /* OBJECT */  { false , true  , true    , true    , true  },
                /* INTEGER */ { false , false , true    , true    , true  },
                /* VOID */    { true  , false , false   , false   , false },
                /* J_L_VOID */{ false , false , false   , false   , true  } };

        boolean needsConversion(RetTypeKind rk) {
            return needsConversion[rk.ordinal()][ordinal()];
        }

        static boolean[][] needsConversion = {
                //              VOID  | SHORT | INT     | INTEGER | NULL
                /* SHORT */   { false , false , false   , false   , false },
                /* INT */     { false , false , false   , true    , false },
                /* OBJECT */  { false , true  , true    , false   , false },
                /* INTEGER */ { false , false , true    , false   , false },
                /* VOID */    { false , false , false   , false   , false },
                /* J_L_VOID */{ true  , false , false   , false   , false } };
    }

    public static void main(String... args) throws Exception {
        for (LambdaReturnKind lrk : LambdaReturnKind.values()) {
            for (RetTypeKind rk1 : RetTypeKind.values()) {
                for (RetTypeKind rk2 : RetTypeKind.values()) {
                    for (ExceptionKind ek1 : ExceptionKind.values()) {
                        for (ExceptionKind ek2 : ExceptionKind.values()) {
                            for (ArgTypeKind ak11 : ArgTypeKind.values()) {
                                for (ArgTypeKind ak12 : ArgTypeKind.values()) {
                                    pool.execute(
                                        new StructuralMostSpecificTest(lrk, rk1,
                                            rk2, ek1, ek2, ak11, ak12));
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    LambdaReturnKind lrk;
    RetTypeKind rt1, rt2;
    ArgTypeKind ak1, ak2;
    ExceptionKind ek1, ek2;
    JavaSource source;
    DiagnosticChecker diagChecker;

    StructuralMostSpecificTest(LambdaReturnKind lrk, RetTypeKind rt1, RetTypeKind rt2,
            ExceptionKind ek1, ExceptionKind ek2, ArgTypeKind ak1, ArgTypeKind ak2) {
        this.lrk = lrk;
        this.rt1 = rt1;
        this.rt2 = rt2;
        this.ek1 = ek1;
        this.ek2 = ek2;
        this.ak1 = ak1;
        this.ak2 = ak2;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "interface SAM1 {\n" +
                          "   #R1 m(#A1 a1) #E1;\n" +
                          "}\n" +
                          "interface SAM2 {\n" +
                          "   #R2 m(#A2 a1) #E2;\n" +
                          "}\n" +
                          "class Test {\n" +
                          "   void m(SAM1 s) { }\n" +
                          "   void m(SAM2 s) { }\n" +
                          "   { m(x->{ #LR }); }\n" +
                          "}\n";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#LR", lrk.retStr)
                    .replaceAll("#R1", rt1.retTypeStr)
                    .replaceAll("#R2", rt2.retTypeStr)
                    .replaceAll("#A1", ak1.argTypeStr)
                    .replaceAll("#A2", ak2.argTypeStr)
                    .replaceAll("#E1", ek1.exceptionStr)
                    .replaceAll("#E2", ek2.exceptionStr);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    public void run() {
        JavacTask ct = (JavacTask)comp.getTask(null, fm.get(), diagChecker,
                Arrays.asList("-XDverboseResolution=all,-predef,-internal,-object-init"),
                null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            throw new
                AssertionError("Error thron when analyzing the following source:\n" +
                    source.getCharContent(true));
        }
        check();
    }

    void check() {
        checkCount.incrementAndGet();

        if (!lrk.compatibleWith(rt1) || !lrk.compatibleWith(rt2))
            return;

        if (lrk.needsConversion(rt1) != lrk.needsConversion(rt2))
            return;

        boolean m1MoreSpecific = moreSpecific(rt1, rt2, ek1, ek2, ak1, ak2);
        boolean m2MoreSpecific = moreSpecific(rt2, rt1, ek2, ek1, ak2, ak1);

        boolean ambiguous = (m1MoreSpecific == m2MoreSpecific);

        if (ambiguous != diagChecker.ambiguityFound) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nAmbiguity found: " + diagChecker.ambiguityFound +
                "\nm1 more specific: " + m1MoreSpecific +
                "\nm2 more specific: " + m2MoreSpecific +
                "\nexpected ambiguity: " + ambiguous);
        }

        if (!ambiguous) {
            String sigToCheck = m1MoreSpecific ? "m(SAM1)" : "m(SAM2)";
            if (!sigToCheck.equals(diagChecker.mostSpecificSig)) {
                throw new Error("invalid most specific method selected:\n" +
                source.getCharContent(true) +
                "\nMost specific found: " + diagChecker.mostSpecificSig +
                "\nm1 more specific: " + m1MoreSpecific +
                "\nm2 more specific: " + m2MoreSpecific);
            }
        }
    }

    boolean moreSpecific(RetTypeKind rk1, RetTypeKind rk2, ExceptionKind ek1,
            ExceptionKind ek2, ArgTypeKind ak1, ArgTypeKind ak2) {
        if (!rk1.moreSpecificThan(rk2))
            return false;

        if (ak1 != ak2)
            return false;

        return true;
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean ambiguityFound;
        String mostSpecificSig;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            try {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR &&
                        diagnostic.getCode().equals("compiler.err.ref.ambiguous")) {
                    ambiguityFound = true;
                } else if (diagnostic.getKind() == Diagnostic.Kind.NOTE &&
                        diagnostic.getCode()
                        .equals("compiler.note.verbose.resolve.multi")) {
                    ClientCodeWrapper.DiagnosticSourceUnwrapper dsu =
                        (ClientCodeWrapper.DiagnosticSourceUnwrapper)diagnostic;
                    JCDiagnostic.MultilineDiagnostic mdiag =
                        (JCDiagnostic.MultilineDiagnostic)dsu.d;
                    int mostSpecificIndex = (Integer)mdiag.getArgs()[2];
                    mostSpecificSig =
                        ((JCDiagnostic)mdiag.getSubdiagnostics()
                            .get(mostSpecificIndex)).getArgs()[1].toString();
                }
            } catch (RuntimeException t) {
                t.printStackTrace();
                throw t;
            }
        }
    }

}
