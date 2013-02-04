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
 * @bug 7115052
 * @bug 8003280 8006694
 * @summary Add lambda tests
 *  Add parser support for method references
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm MethodReferenceParserTest
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import com.sun.source.util.JavacTask;

public class MethodReferenceParserTest
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum ReferenceKind {
        METHOD_REF("#Q::#Gm"),
        CONSTRUCTOR_REF("#Q::#Gnew"),
        FALSE_REF("min < max"),
        ERR_SUPER("#Q::#Gsuper"),
        ERR_METH0("#Q::#Gm()"),
        ERR_METH1("#Q::#Gm(X)"),
        ERR_CONSTR0("#Q::#Gnew()"),
        ERR_CONSTR1("#Q::#Gnew(X)");

        String referenceTemplate;

        ReferenceKind(String referenceTemplate) {
            this.referenceTemplate = referenceTemplate;
        }

        String getReferenceString(QualifierKind qk, GenericKind gk) {
            return referenceTemplate
                    .replaceAll("#Q", qk.qualifier)
                    .replaceAll("#G", gk.typeParameters);
        }

        boolean erroneous() {
            switch (this) {
                case ERR_SUPER:
                case ERR_METH0:
                case ERR_METH1:
                case ERR_CONSTR0:
                case ERR_CONSTR1:
                    return true;
                default: return false;
            }
        }
    }

    enum ContextKind {
        ASSIGN("SAM s = #E;"),
        METHOD("m(#E, i);");

        String contextTemplate;

        ContextKind(String contextTemplate) {
            this.contextTemplate = contextTemplate;
        }

        String contextString(ExprKind ek, ReferenceKind rk, QualifierKind qk,
                GenericKind gk, SubExprKind sk) {
            return contextTemplate.replaceAll("#E", ek.expressionString(rk, qk, gk, sk));
        }
    }

    enum GenericKind {
        NONE(""),
        ONE("<X>"),
        TWO("<X,Y>");

        String typeParameters;

        GenericKind(String typeParameters) {
            this.typeParameters = typeParameters;
        }
    }

    enum QualifierKind {
        THIS("this"),
        SUPER("super"),
        NEW("new Foo()"),
        METHOD("m()"),
        FIELD("a.f"),
        UBOUND_SIMPLE("A"),
        UNBOUND_ARRAY1("int[]"),
        UNBOUND_ARRAY2("A<G>[][]"),
        UNBOUND_GENERIC1("A<X>"),
        UNBOUND_GENERIC2("A<X, Y>"),
        UNBOUND_GENERIC3("A<? extends X, ? super Y>"),
        UNBOUND_GENERIC4("A<int[], short[][]>"),
        NESTED_GENERIC1("A<A<X,Y>, A<X,Y>>"),
        NESTED_GENERIC2("A<A<A<X,Y>,A<X,Y>>, A<A<X,Y>,A<X,Y>>>");

        String qualifier;

        QualifierKind(String qualifier) {
            this.qualifier = qualifier;
        }
    }

    enum ExprKind {
        NONE("#R::S"),
        SINGLE_PAREN1("(#R#S)"),
        SINGLE_PAREN2("(#R)#S"),
        DOUBLE_PAREN1("((#R#S))"),
        DOUBLE_PAREN2("((#R)#S)"),
        DOUBLE_PAREN3("((#R))#S");

        String expressionTemplate;

        ExprKind(String expressionTemplate) {
            this.expressionTemplate = expressionTemplate;
        }

        String expressionString(ReferenceKind rk, QualifierKind qk, GenericKind gk, SubExprKind sk) {
            return expressionTemplate
                    .replaceAll("#R", rk.getReferenceString(qk, gk))
                    .replaceAll("#S", sk.subExpression);
        }
    }

    enum SubExprKind {
        NONE(""),
        SELECT_FIELD(".f"),
        SELECT_METHOD(".f()"),
        SELECT_NEW(".new Foo()"),
        POSTINC("++"),
        POSTDEC("--");

        String subExpression;

        SubExprKind(String subExpression) {
            this.subExpression = subExpression;
        }
    }

    public static void main(String... args) throws Exception {
        for (ReferenceKind rk : ReferenceKind.values()) {
            for (QualifierKind qk : QualifierKind.values()) {
                for (GenericKind gk : GenericKind.values()) {
                    for (SubExprKind sk : SubExprKind.values()) {
                        for (ExprKind ek : ExprKind.values()) {
                            for (ContextKind ck : ContextKind.values()) {
                                pool.execute(new MethodReferenceParserTest(rk, qk, gk, sk, ek, ck));
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    ReferenceKind rk;
    QualifierKind qk;
    GenericKind gk;
    SubExprKind sk;
    ExprKind ek;
    ContextKind ck;
    JavaSource source;
    DiagnosticChecker diagChecker;

    MethodReferenceParserTest(ReferenceKind rk, QualifierKind qk, GenericKind gk, SubExprKind sk, ExprKind ek, ContextKind ck) {
        this.rk = rk;
        this.qk = qk;
        this.gk = gk;
        this.sk = sk;
        this.ek = ek;
        this.ck = ck;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "class Test {\n" +
                          "   void test() {\n" +
                          "      #C\n" +
                          "   }" +
                          "}";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#C", ck.contextString(ek, rk, qk, gk, sk));
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
            ct.parse();
        } catch (Throwable ex) {
            processException(ex);
            return;
        }
        check();
    }

    void check() {
        checkCount.incrementAndGet();

        if (diagChecker.errorFound != rk.erroneous()) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nFound error: " + diagChecker.errorFound +
                "\nExpected error: " + rk.erroneous());
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }

}
