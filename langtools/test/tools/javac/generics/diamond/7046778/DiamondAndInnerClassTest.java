/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7046778 8006694
 * @summary Project Coin: problem with diamond and member inner classes
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm DiamondAndInnerClassTest
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

public class DiamondAndInnerClassTest
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum TypeArgumentKind {
        NONE(""),
        STRING("<String>"),
        INTEGER("<Integer>"),
        DIAMOND("<>");

        String typeargStr;

        private TypeArgumentKind(String typeargStr) {
            this.typeargStr = typeargStr;
        }

        boolean compatible(TypeArgumentKind that) {
            switch (this) {
                case NONE: return true;
                case STRING: return that != INTEGER;
                case INTEGER: return that != STRING;
                default: throw new AssertionError("Unexpected decl kind: " + this);
            }
        }

        boolean compatible(ArgumentKind that) {
            switch (this) {
                case NONE: return true;
                case STRING: return that == ArgumentKind.STRING;
                case INTEGER: return that == ArgumentKind.INTEGER;
                default: throw new AssertionError("Unexpected decl kind: " + this);
            }
        }
    }

    enum ArgumentKind {
        OBJECT("(Object)null"),
        STRING("(String)null"),
        INTEGER("(Integer)null");

        String argStr;

        private ArgumentKind(String argStr) {
            this.argStr = argStr;
        }
    }

    enum TypeQualifierArity {
        ONE(1, "A1#TA1"),
        TWO(2, "A1#TA1.A2#TA2"),
        THREE(3, "A1#TA1.A2#TA2.A3#TA3");

        int n;
        String qualifierStr;

        private TypeQualifierArity(int n, String qualifierStr) {
            this.n = n;
            this.qualifierStr = qualifierStr;
        }

        String getType(TypeArgumentKind... typeArgumentKinds) {
            String res = qualifierStr;
            for (int i = 1 ; i <= typeArgumentKinds.length ; i++) {
                res = res.replace("#TA" + i, typeArgumentKinds[i-1].typeargStr);
            }
            return res;
        }

        boolean matches(InnerClassDeclArity innerClassDeclArity) {
            return n ==innerClassDeclArity.n;
        }
    }

    enum InnerClassDeclArity {
        ONE(1, "class A1<X> { A1(X x1) { } #B }"),
        TWO(2, "class A1<X1> { class A2<X2> { A2(X1 x1, X2 x2) { }  #B } }"),
        THREE(3, "class A1<X1> { class A2<X2> { class A3<X3> { A3(X1 x1, X2 x2, X3 x3) { } #B } } }");

        int n;
        String classDeclStr;

        private InnerClassDeclArity(int n, String classDeclStr) {
            this.n = n;
            this.classDeclStr = classDeclStr;
        }
    }

    enum ArgumentListArity {
        ONE(1, "(#A1)"),
        TWO(2, "(#A1,#A2)"),
        THREE(3, "(#A1,#A2,#A3)");

        int n;
        String argListStr;

        private ArgumentListArity(int n, String argListStr) {
            this.n = n;
            this.argListStr = argListStr;
        }

        String getArgs(ArgumentKind... argumentKinds) {
            String res = argListStr;
            for (int i = 1 ; i <= argumentKinds.length ; i++) {
                res = res.replace("#A" + i, argumentKinds[i-1].argStr);
            }
            return res;
        }

        boolean matches(InnerClassDeclArity innerClassDeclArity) {
            return n ==innerClassDeclArity.n;
        }
    }

    public static void main(String... args) throws Exception {
        for (InnerClassDeclArity innerClassDeclArity : InnerClassDeclArity.values()) {
            for (TypeQualifierArity declType : TypeQualifierArity.values()) {
                if (!declType.matches(innerClassDeclArity)) continue;
                for (TypeQualifierArity newClassType : TypeQualifierArity.values()) {
                    if (!newClassType.matches(innerClassDeclArity)) continue;
                    for (ArgumentListArity argList : ArgumentListArity.values()) {
                        if (!argList.matches(innerClassDeclArity)) continue;
                        for (TypeArgumentKind taDecl1 : TypeArgumentKind.values()) {
                            boolean isDeclRaw = taDecl1 == TypeArgumentKind.NONE;
                            //no diamond on decl site
                            if (taDecl1 == TypeArgumentKind.DIAMOND) continue;
                            for (TypeArgumentKind taSite1 : TypeArgumentKind.values()) {
                                boolean isSiteRaw =
                                        taSite1 == TypeArgumentKind.NONE;
                                //diamond only allowed on the last type qualifier
                                if (taSite1 == TypeArgumentKind.DIAMOND &&
                                        innerClassDeclArity !=
                                        InnerClassDeclArity.ONE)
                                    continue;
                                for (ArgumentKind arg1 : ArgumentKind.values()) {
                                    if (innerClassDeclArity == innerClassDeclArity.ONE) {
                                        pool.execute(
                                                new DiamondAndInnerClassTest(
                                                innerClassDeclArity, declType,
                                                newClassType, argList,
                                                new TypeArgumentKind[] {taDecl1},
                                                new TypeArgumentKind[] {taSite1},
                                                new ArgumentKind[] {arg1}));
                                        continue;
                                    }
                                    for (TypeArgumentKind taDecl2 : TypeArgumentKind.values()) {
                                        //no rare types
                                        if (isDeclRaw != (taDecl2 == TypeArgumentKind.NONE))
                                            continue;
                                        //no diamond on decl site
                                        if (taDecl2 == TypeArgumentKind.DIAMOND)
                                            continue;
                                        for (TypeArgumentKind taSite2 : TypeArgumentKind.values()) {
                                            //no rare types
                                            if (isSiteRaw != (taSite2 == TypeArgumentKind.NONE))
                                                continue;
                                            //diamond only allowed on the last type qualifier
                                            if (taSite2 == TypeArgumentKind.DIAMOND &&
                                                    innerClassDeclArity != InnerClassDeclArity.TWO)
                                                continue;
                                            for (ArgumentKind arg2 : ArgumentKind.values()) {
                                                if (innerClassDeclArity == innerClassDeclArity.TWO) {
                                                    pool.execute(
                                                            new DiamondAndInnerClassTest(
                                                            innerClassDeclArity,
                                                            declType,
                                                            newClassType,
                                                            argList,
                                                            new TypeArgumentKind[] {taDecl1, taDecl2},
                                                            new TypeArgumentKind[] {taSite1, taSite2},
                                                            new ArgumentKind[] {arg1, arg2}));
                                                    continue;
                                                }
                                                for (TypeArgumentKind taDecl3 : TypeArgumentKind.values()) {
                                                    //no rare types
                                                    if (isDeclRaw != (taDecl3 == TypeArgumentKind.NONE))
                                                        continue;
                                                    //no diamond on decl site
                                                    if (taDecl3 == TypeArgumentKind.DIAMOND)
                                                        continue;
                                                    for (TypeArgumentKind taSite3 : TypeArgumentKind.values()) {
                                                        //no rare types
                                                        if (isSiteRaw != (taSite3 == TypeArgumentKind.NONE))
                                                            continue;
                                                        //diamond only allowed on the last type qualifier
                                                        if (taSite3 == TypeArgumentKind.DIAMOND &&
                                                            innerClassDeclArity != InnerClassDeclArity.THREE)
                                                            continue;
                                                        for (ArgumentKind arg3 : ArgumentKind.values()) {
                                                            if (innerClassDeclArity ==
                                                                    innerClassDeclArity.THREE) {
                                                                pool.execute(
                                                                        new DiamondAndInnerClassTest(
                                                                        innerClassDeclArity,
                                                                        declType,
                                                                        newClassType,
                                                                        argList,
                                                                        new TypeArgumentKind[] {taDecl1, taDecl2, taDecl3},
                                                                        new TypeArgumentKind[] {taSite1, taSite2, taSite3},
                                                                        new ArgumentKind[] {arg1, arg2, arg3}));
                                                                continue;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
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

    InnerClassDeclArity innerClassDeclArity;
    TypeQualifierArity declType;
    TypeQualifierArity siteType;
    ArgumentListArity argList;
    TypeArgumentKind[] declTypeArgumentKinds;
    TypeArgumentKind[] siteTypeArgumentKinds;
    ArgumentKind[] argumentKinds;
    JavaSource source;
    DiagnosticChecker diagChecker;

    DiamondAndInnerClassTest(InnerClassDeclArity innerClassDeclArity,
            TypeQualifierArity declType, TypeQualifierArity siteType,
            ArgumentListArity argList, TypeArgumentKind[] declTypeArgumentKinds,
            TypeArgumentKind[] siteTypeArgumentKinds, ArgumentKind[] argumentKinds) {
        this.innerClassDeclArity = innerClassDeclArity;
        this.declType = declType;
        this.siteType = siteType;
        this.argList = argList;
        this.declTypeArgumentKinds = declTypeArgumentKinds;
        this.siteTypeArgumentKinds = siteTypeArgumentKinds;
        this.argumentKinds = argumentKinds;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String bodyTemplate = "#D res = new #S#AL;";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = innerClassDeclArity.classDeclStr.replace("#B", bodyTemplate)
                    .replace("#D", declType.getType(declTypeArgumentKinds))
                    .replace("#S", siteType.getType(siteTypeArgumentKinds))
                    .replace("#AL", argList.getArgs(argumentKinds));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    @Override
    public void run() {
        JavacTask ct = (JavacTask)comp.getTask(null, fm.get(), diagChecker,
                null, null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            throw new AssertionError("Error thrown when compiling the following code:\n" +
                    source.getCharContent(true));
        }
        check();
    }

    void check() {
        checkCount.incrementAndGet();

        boolean errorExpected = false;

        TypeArgumentKind[] expectedArgKinds =
                new TypeArgumentKind[innerClassDeclArity.n];

        for (int i = 0 ; i < innerClassDeclArity.n ; i++) {
            if (!declTypeArgumentKinds[i].compatible(siteTypeArgumentKinds[i])) {
                errorExpected = true;
                break;
            }
            expectedArgKinds[i] = siteTypeArgumentKinds[i] ==
                    TypeArgumentKind.DIAMOND ?
                declTypeArgumentKinds[i] : siteTypeArgumentKinds[i];
        }

        if (!errorExpected) {
            for (int i = 0 ; i < innerClassDeclArity.n ; i++) {
                if (!expectedArgKinds[i].compatible(argumentKinds[i])) {
                    errorExpected = true;
                    break;
                }
            }
        }

        if (errorExpected != diagChecker.errorFound) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nFound error: " + diagChecker.errorFound +
                "\nExpected error: " + errorExpected);
        }
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }

}
