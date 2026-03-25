/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.PrintWriter;
import java.io.IOException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.Charset;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.PathFileObject;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

public class InitializationWarningTester {
    Context context;
    Options options;
    MyJavaCompiler javaCompiler;
    JavacFileManager javacFileManager;
    PrintWriter errOut;
    DiagnosticListener<JavaFileObject> diagnosticListener;

    public static void main(String... args) throws Throwable {
        String testSrc = System.getProperty("test.src");
        Path baseDir = Paths.get(testSrc);
        InitializationWarningTester tester = new InitializationWarningTester();
        Assert.check(args.length > 0, "no args, ending");
        Assert.check(args.length <= 2, "unexpected number of arguments");
        String className = args[0];
        String warningsGoldenFileName = args.length > 1 ? args[1] : null;
        tester.test(baseDir, className, warningsGoldenFileName);
    }

    java.util.List<String> compilationOutput = new ArrayList<>();

    public InitializationWarningTester() {
        context = new Context();
        diagnosticListener = new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> message) {
                JCDiagnostic diagnostic = (JCDiagnostic) message;
                String msgData = ((PathFileObject)diagnostic.getDiagnosticSource().getFile()).getShortName() +
                        ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() + ": " + diagnostic.getCode();
                if (diagnostic.getArgs() != null && diagnostic.getArgs().length > 0) {
                    msgData += ": " + Arrays.stream(diagnostic.getArgs()).map(o -> o.toString())
                            .collect(Collectors.joining(", "));
                }
                compilationOutput.add(msgData);
            }
        };
        context.put(DiagnosticListener.class, diagnosticListener);
        JavacFileManager.preRegister(context);
        MyAttr.preRegister(context, this);
        options = Options.instance(context);
        options.put("--enable-preview", "--enable-preview");
        options.put("--source", Integer.toString(Runtime.version().feature()));
        options.put("-Xlint:initialization", "-Xlint:initialization");
        javaCompiler = new MyJavaCompiler(context);
        javacFileManager = new JavacFileManager(context, false, Charset.defaultCharset());
    }

    void test(Path baseDir, String className, String warningsGoldenFileName) throws Throwable {
        Path javaFile = baseDir.resolve(className + ".java");
        Path goldenFile = warningsGoldenFileName != null ? baseDir.resolve(warningsGoldenFileName) : null;

        // compile
        javaCompiler.compile(com.sun.tools.javac.util.List.of(javacFileManager.getJavaFileObject(javaFile)));
        if (goldenFile != null) {
            java.util.List<String> goldenFileContent = Files.readAllLines(goldenFile);
            if (goldenFileContent.size() != compilationOutput.size()) {
                System.err.println("compilation output length mismatch");
                System.err.println("    golden file content:");
                for (String s : goldenFileContent) {
                    System.err.println("        " + s);
                }
                System.err.println("    warning compilation result:");
                for (String s : compilationOutput) {
                    System.err.println("        " + s);
                }
                throw new AssertionError("compilation output length mismatch");
            }
            for (int i = 0; i < goldenFileContent.size(); i++) {
                String goldenLine = goldenFileContent.get(i);
                String warningLine = compilationOutput.get(i);
                Assert.check(warningLine.equals(goldenLine), "error, found:\n" + warningLine + "\nexpected:\n" + goldenLine);
            }
        } else {
            if (compilationOutput.size() != 0) {
                System.err.println("    expecting empty compilation output, got:");
                for (String s : compilationOutput) {
                    System.err.println("        " + s);
                }
                throw new AssertionError("expected empty compilation output");
            }
        }
    }

    static class MyJavaCompiler extends JavaCompiler {
        MyJavaCompiler(Context context) {
            super(context);
            // do not generate code
            this.shouldStopPolicyIfNoError = CompileStates.CompileState.LOWER;
        }
    }

    static class MyAttr extends Attr {
        InitializationWarningTester tester;
        static void preRegister(Context context, InitializationWarningTester tester) {
            context.put(attrKey, (com.sun.tools.javac.util.Context.Factory<Attr>) c -> new MyAttr(c, tester));
        }

        MyAttr(Context context, InitializationWarningTester tester) {
            super(context);
            this.tester = tester;
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            if (TreeInfo.isConstructor(tree)) {
                /* remove the super constructor call if it has no arguments, that way the Attr super class
                 * will add a super() as the first statement in the constructor and will analyze the rest
                 * of the code in warnings only mode
                 */
                if (TreeInfo.hasAnyConstructorCall(tree)) {
                    ListBuffer<JCStatement> newStats = new ListBuffer<>();
                    for (JCStatement statement : tree.body.stats) {
                        if (statement instanceof JCExpressionStatement expressionStatement &&
                                expressionStatement.expr instanceof JCMethodInvocation methodInvocation) {
                            if (TreeInfo.isConstructorCall(methodInvocation) &&
                                methodInvocation.args.isEmpty()) {
                                continue;
                            }
                        }
                        newStats.add(statement);
                    }
                    tree.body.stats = newStats.toList();
                }
            }
            super.visitMethodDef(tree);
        }
    }
}
