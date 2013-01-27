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
 * @bug 7062745 8006694
 * @summary  Regression: difference in overload resolution when two methods
 *  are maximally specific
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm GenericOverrideTest
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import com.sun.source.util.JavacTask;

public class GenericOverrideTest
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum SignatureKind {
        NON_GENERIC(""),
        GENERIC("<X>");

        String paramStr;

        private SignatureKind(String paramStr) {
            this.paramStr = paramStr;
        }
    }

    enum ReturnTypeKind {
        LIST("List"),
        ARRAYLIST("ArrayList");

        String retStr;

        private ReturnTypeKind(String retStr) {
            this.retStr = retStr;
        }

        boolean moreSpecificThan(ReturnTypeKind that) {
            switch (this) {
                case LIST:
                    return that == this;
                case ARRAYLIST:
                    return that == LIST || that == ARRAYLIST;
                default: throw new AssertionError("Unexpected ret kind: " + this);
            }
        }
    }

    enum TypeArgumentKind {
        NONE(""),
        UNBOUND("<?>"),
        INTEGER("<Number>"),
        NUMBER("<Integer>"),
        TYPEVAR("<X>");

        String typeargStr;

        private TypeArgumentKind(String typeargStr) {
            this.typeargStr = typeargStr;
        }

        boolean compatibleWith(SignatureKind sig) {
            switch (this) {
                case TYPEVAR: return sig != SignatureKind.NON_GENERIC;
                default: return true;
            }
        }

        boolean moreSpecificThan(TypeArgumentKind that, boolean strict) {
            switch (this) {
                case NONE:
                    return that == this || !strict;
                case UNBOUND:
                    return that == this || that == NONE;
                case INTEGER:
                case NUMBER:
                case TYPEVAR:
                    return that == this || that == NONE || that == UNBOUND;
                default: throw new AssertionError("Unexpected typearg kind: " + this);
            }
        }

        boolean assignableTo(TypeArgumentKind that, SignatureKind sig) {
            switch (this) {
                case NONE:
                    //this case needs to workaround to javac's impl of 15.12.2.8 being too strict
                    //ideally should be just 'return true' (see 7067746)
                    return sig == SignatureKind.NON_GENERIC || that == NONE;
                case UNBOUND:
                    return that == this || that == NONE;
                case INTEGER:
                case NUMBER:
                    return that == this || that == NONE || that == UNBOUND;
                case TYPEVAR:
                    return true;
                default: throw new AssertionError("Unexpected typearg kind: " + this);
            }
        }
    }

    public static void main(String... args) throws Exception {
        for (SignatureKind sig1 : SignatureKind.values()) {
            for (ReturnTypeKind rt1 : ReturnTypeKind.values()) {
                for (TypeArgumentKind ta1 : TypeArgumentKind.values()) {
                    if (!ta1.compatibleWith(sig1)) continue;
                    for (SignatureKind sig2 : SignatureKind.values()) {
                        for (ReturnTypeKind rt2 : ReturnTypeKind.values()) {
                            for (TypeArgumentKind ta2 : TypeArgumentKind.values()) {
                                if (!ta2.compatibleWith(sig2)) continue;
                                for (ReturnTypeKind rt3 : ReturnTypeKind.values()) {
                                    for (TypeArgumentKind ta3 : TypeArgumentKind.values()) {
                                        if (!ta3.compatibleWith(SignatureKind.NON_GENERIC))
                                            continue;
                                        pool.execute(
                                                new GenericOverrideTest(sig1,
                                                rt1, ta1, sig2, rt2,
                                                ta2, rt3, ta3));
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

    SignatureKind sig1, sig2;
    ReturnTypeKind rt1, rt2, rt3;
    TypeArgumentKind ta1, ta2, ta3;
    JavaSource source;
    DiagnosticChecker diagChecker;

    GenericOverrideTest(SignatureKind sig1, ReturnTypeKind rt1, TypeArgumentKind ta1,
            SignatureKind sig2, ReturnTypeKind rt2, TypeArgumentKind ta2,
            ReturnTypeKind rt3, TypeArgumentKind ta3) {
        this.sig1 = sig1;
        this.sig2 = sig2;
        this.rt1 = rt1;
        this.rt2 = rt2;
        this.rt3 = rt3;
        this.ta1 = ta1;
        this.ta2 = ta2;
        this.ta3 = ta3;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "import java.util.*;\n" +
                          "interface A { #S1 #R1#TA1 m(); }\n" +
                          "interface B { #S2 #R2#TA2 m(); }\n" +
                          "interface AB extends A, B {}\n" +
                          "class Test {\n" +
                          "  void test(AB ab) { #R3#TA3 n = ab.m(); }\n" +
                          "}";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replace("#S1", sig1.paramStr).
                    replace("#S2", sig2.paramStr).
                    replace("#R1", rt1.retStr).
                    replace("#R2", rt2.retStr).
                    replace("#R3", rt3.retStr).
                    replace("#TA1", ta1.typeargStr).
                    replace("#TA2", ta2.typeargStr).
                    replace("#TA3", ta3.typeargStr);
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
        int mostSpecific = 0;

        //first check that either |R1| <: |R2| or |R2| <: |R1|
        if (rt1 != rt2) {
            if (!rt1.moreSpecificThan(rt2) &&
                    !rt2.moreSpecificThan(rt1)) {
                errorExpected = true;
            } else {
                mostSpecific = rt1.moreSpecificThan(rt2) ? 1 : 2;
            }
        }

        //check that either TA1 <= TA2 or TA2 <= TA1 (unless most specific return found above is raw)
        if (!errorExpected) {
            if (ta1 != ta2) {
                boolean useStrictCheck = ta1.moreSpecificThan(ta2, true) ||
                        ta2.moreSpecificThan(ta1, true);
                if (!ta1.moreSpecificThan(ta2, useStrictCheck) &&
                        !ta2.moreSpecificThan(ta1, useStrictCheck)) {
                    errorExpected = true;
                } else {
                    int mostSpecific2 = ta1.moreSpecificThan(ta2, useStrictCheck) ? 1 : 2;
                    if (mostSpecific != 0 && mostSpecific2 != mostSpecific) {
                        errorExpected = mostSpecific == 1 ?
                                ta1 != TypeArgumentKind.NONE :
                                ta2 != TypeArgumentKind.NONE;
                    } else {
                        mostSpecific = mostSpecific2;
                    }
                }
            } else if (mostSpecific == 0) {
                //when no signature is better than the other, an arbitrary choice
                //must be made - javac always picks the second signature
                mostSpecific = 2;
            }
        }

        //finally, check that most specific return type is compatible with expected type
        if (!errorExpected) {
            ReturnTypeKind msrt = mostSpecific == 1 ? rt1 : rt2;
            TypeArgumentKind msta = mostSpecific == 1 ? ta1 : ta2;
            SignatureKind mssig = mostSpecific == 1 ? sig1 : sig2;

            if (!msrt.moreSpecificThan(rt3) ||
                    !msta.assignableTo(ta3, mssig)) {
                errorExpected = true;
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
