/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7115050
 * @summary Add parser support for lambda expressions
 */

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class LambdaParserTest {

    static int checkCount = 0;

    enum LambdaKind {
        NILARY_EXPR("()->x"),
        NILARY_STMT("()->{ return x; }"),
        ONEARY_SHORT_EXPR("x->x"),
        ONEARY_SHORT_STMT("x->{ return x; }"),
        ONEARY_EXPR("(#M1 #T1 x)->x"),
        ONEARY_STMT("(#M1 #T1 x)->{ return x; }"),
        TWOARY_EXPR("(#M1 #T1 x, #M2 #T2 y)->x"),
        TWOARY_STMT("(#M1 #T1 x, #M2 #T2 y)->{ return x; }");

        String lambdaTemplate;

        LambdaKind(String lambdaTemplate) {
            this.lambdaTemplate = lambdaTemplate;
        }

        String getLambdaString(LambdaParameterKind pk1, LambdaParameterKind pk2,
                ModifierKind mk1, ModifierKind mk2) {
            return lambdaTemplate.replaceAll("#M1", mk1.modifier)
                    .replaceAll("#M2", mk2.modifier)
                    .replaceAll("#T1", pk1.parameterType)
                    .replaceAll("#T2", pk2.parameterType);
        }

        int arity() {
            switch (this) {
                case NILARY_EXPR:
                case NILARY_STMT: return 0;
                case ONEARY_SHORT_EXPR:
                case ONEARY_SHORT_STMT:
                case ONEARY_EXPR:
                case ONEARY_STMT: return 1;
                case TWOARY_EXPR:
                case TWOARY_STMT: return 2;
                default: throw new AssertionError("Invalid lambda kind " + this);
            }
        }

        boolean isShort() {
            return this == ONEARY_SHORT_EXPR ||
                    this == ONEARY_SHORT_STMT;
        }
    }

    enum LambdaParameterKind {
        IMPLICIT(""),
        EXPLIICT_SIMPLE("A"),
        EXPLICIT_VARARGS("A..."),
        EXPLICIT_GENERIC1("A<X>"),
        EXPLICIT_GENERIC3("A<? extends X, ? super Y>");

        String parameterType;

        LambdaParameterKind(String parameterType) {
            this.parameterType = parameterType;
        }

        boolean explicit() {
            return this != IMPLICIT;
        }
    }

    enum ModifierKind {
        NONE(""),
        FINAL("final"),
        PUBLIC("public");

        String modifier;

        ModifierKind(String modifier) {
            this.modifier = modifier;
        }

        boolean compatibleWith(LambdaParameterKind pk) {
            switch (this) {
                case PUBLIC: return false;
                case FINAL: return pk != LambdaParameterKind.IMPLICIT;
                case NONE: return true;
                default: throw new AssertionError("Invalid modifier kind " + this);
            }
        }
    }

    enum ExprKind {
        NONE("#L#S"),
        SINGLE_PAREN1("(#L#S)"),
        SINGLE_PAREN2("(#L)#S"),
        DOUBLE_PAREN1("((#L#S))"),
        DOUBLE_PAREN2("((#L)#S)"),
        DOUBLE_PAREN3("((#L))#S");

        String expressionTemplate;

        ExprKind(String expressionTemplate) {
            this.expressionTemplate = expressionTemplate;
        }

        String expressionString(LambdaParameterKind pk1, LambdaParameterKind pk2,
                ModifierKind mk1, ModifierKind mk2, LambdaKind lk, SubExprKind sk) {
            return expressionTemplate.replaceAll("#L", lk.getLambdaString(pk1, pk2, mk1, mk2))
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

        //create default shared JavaCompiler - reused across multiple compilations
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null);

        for (LambdaKind lk : LambdaKind.values()) {
            for (LambdaParameterKind pk1 : LambdaParameterKind.values()) {
                if (lk.arity() < 1 && pk1 != LambdaParameterKind.IMPLICIT) continue;
                for (LambdaParameterKind pk2 : LambdaParameterKind.values()) {
                    if (lk.arity() < 2 && pk2 != LambdaParameterKind.IMPLICIT) continue;
                    for (ModifierKind mk1 : ModifierKind.values()) {
                        if (mk1 != ModifierKind.NONE && lk.isShort()) continue;
                        if (lk.arity() < 1 && mk1 != ModifierKind.NONE) continue;
                        for (ModifierKind mk2 : ModifierKind.values()) {
                            if (lk.arity() < 2 && mk2 != ModifierKind.NONE) continue;
                            for (SubExprKind sk : SubExprKind.values()) {
                                for (ExprKind ek : ExprKind.values()) {
                                    new LambdaParserTest(pk1, pk2, mk1, mk2, lk, sk, ek)
                                            .run(comp, fm);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Total check executed: " + checkCount);
    }

    LambdaParameterKind pk1;
    LambdaParameterKind pk2;
    ModifierKind mk1;
    ModifierKind mk2;
    LambdaKind lk;
    SubExprKind sk;
    ExprKind ek;
    JavaSource source;
    DiagnosticChecker diagChecker;

    LambdaParserTest(LambdaParameterKind pk1, LambdaParameterKind pk2, ModifierKind mk1,
            ModifierKind mk2, LambdaKind lk, SubExprKind sk, ExprKind ek) {
        this.pk1 = pk1;
        this.pk2 = pk2;
        this.mk1 = mk1;
        this.mk2 = mk2;
        this.lk = lk;
        this.sk = sk;
        this.ek = ek;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "class Test {\n" +
                          "   SAM s = #E;\n" +
                          "}";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#E", ek.expressionString(pk1, pk2, mk1, mk2, lk, sk));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    void run(JavaCompiler tool, StandardJavaFileManager fm) throws Exception {
        JavacTask ct = (JavacTask)tool.getTask(null, fm, diagChecker,
                Arrays.asList("-XDallowLambda"), null, Arrays.asList(source));
        try {
            ct.parse();
        } catch (Throwable ex) {
            throw new AssertionError("Error thrown when parsing the following source:\n" + source.getCharContent(true));
        }
        check();
    }

    void check() {
        checkCount++;

        boolean errorExpected = (lk.arity() > 0 && !mk1.compatibleWith(pk1)) ||
                (lk.arity() > 1 && !mk2.compatibleWith(pk2));

        if (lk.arity() == 2 &&
                (pk1.explicit() != pk2.explicit() ||
                pk1 == LambdaParameterKind.EXPLICIT_VARARGS)) {
            errorExpected = true;
        }

        if (errorExpected != diagChecker.errorFound) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nFound error: " + diagChecker.errorFound +
                "\nExpected error: " + errorExpected);
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
