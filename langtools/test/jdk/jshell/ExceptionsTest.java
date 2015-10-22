/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests for exceptions
 * @build KullaTesting TestingInputStream
 * @run testng ExceptionsTest
 */

import jdk.jshell.SnippetEvent;
import jdk.jshell.EvalException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jdk.jshell.Snippet;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
public class ExceptionsTest extends KullaTesting {

    public void throwUncheckedException() {
        String message = "error_message";
        SnippetEvent cr = assertEvalException("throw new RuntimeException(\"" + message + "\");");
        assertExceptionMatch(cr,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "", cr.snippet(), 1)));
    }

    public void throwCheckedException() {
        String message = "error_message";
        SnippetEvent cr = assertEvalException("throw new Exception(\"" + message + "\");");
        assertExceptionMatch(cr,
                new ExceptionInfo(Exception.class, message,
                        newStackTraceElement("", "", cr.snippet(), 1)));
    }

    public void throwFromStaticMethodOfClass() {
        String message = "error_message";
        Snippet s1 = methodKey(assertEval("void f() { throw new RuntimeException(\"" + message + "\"); }"));
        Snippet s2 = classKey(assertEval("class A { static void g() { f(); } }"));
        SnippetEvent cr3 = assertEvalException("A.g();");
        assertExceptionMatch(cr3,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "f", s1, 1),
                        newStackTraceElement("A", "g", s2, 1),
                        newStackTraceElement("", "", cr3.snippet(), 1)));
    }

    public void throwFromStaticMethodOfInterface() {
        String message = "error_message";
        Snippet s1 = methodKey(assertEval("void f() { throw new RuntimeException(\"" + message + "\"); }"));
        Snippet s2 = classKey(assertEval("interface A { static void g() { f(); } }"));
        SnippetEvent cr3 = assertEvalException("A.g();");
        assertExceptionMatch(cr3,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "f", s1, 1),
                        newStackTraceElement("A", "g", s2, 1),
                        newStackTraceElement("", "", cr3.snippet(), 1)));
    }

    public void throwFromConstructor() {
        String message = "error_message";
        Snippet s1 = methodKey(assertEval("void f() { throw new RuntimeException(\"" + message + "\"); }"));
        Snippet s2 = classKey(assertEval("class A { A() { f(); } }"));
        SnippetEvent cr3 = assertEvalException("new A();");
        assertExceptionMatch(cr3,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "f", s1, 1),
                        newStackTraceElement("A", "<init>", s2, 1),
                        newStackTraceElement("", "", cr3.snippet(), 1)));
    }

    public void throwFromDefaultMethodOfInterface() {
        String message = "error_message";
        Snippet s1 = methodKey(assertEval("void f() { throw new RuntimeException(\"" + message + "\"); }"));
        Snippet s2 = classKey(assertEval("interface A { default void g() { f(); } }"));
        SnippetEvent cr3 = assertEvalException("new A() { }.g();");
        assertExceptionMatch(cr3,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "f", s1, 1),
                        newStackTraceElement("A", "g", s2, 1),
                        newStackTraceElement("", "", cr3.snippet(), 1)));
    }

    public void throwFromLambda() {
        String message = "lambda";
        Snippet s1 = varKey(assertEval(
                "Runnable run = () -> {\n" +
                "   throw new RuntimeException(\"" + message + "\");\n" +
                "};"
        ));
        SnippetEvent cr2 = assertEvalException("run.run();");
        assertExceptionMatch(cr2,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("", "lambda$", s1, 2),
                        newStackTraceElement("", "", cr2.snippet(), 1)));
    }

    public void throwFromAnonymousClass() {
        String message = "anonymous";
        Snippet s1 = varKey(assertEval(
                "Runnable run = new Runnable() {\n" +
                "   public void run() {\n"+
                "       throw new RuntimeException(\"" + message + "\");\n" +
                "   }\n" +
                "};"
        ));
        SnippetEvent cr2 = assertEvalException("run.run();");
        assertExceptionMatch(cr2,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("1", "run", s1, 3),
                        newStackTraceElement("", "", cr2.snippet(), 1)));
    }

    public void throwFromLocalClass() {
        String message = "local";
        Snippet s1 = methodKey(assertEval(
                "void f() {\n" +
                "   class A {\n" +
                "       void f() {\n"+
                "           throw new RuntimeException(\"" + message + "\");\n" +
                "       }\n" +
                "   }\n" +
                "   new A().f();\n" +
                "}"
        ));
        SnippetEvent cr2 = assertEvalException("f();");
        assertExceptionMatch(cr2,
                new ExceptionInfo(RuntimeException.class, message,
                        newStackTraceElement("1A", "f", s1, 4),
                        newStackTraceElement("", "f", s1, 7),
                        newStackTraceElement("", "", cr2.snippet(), 1)));
    }

    @Test(enabled = false) // TODO 8129427
    public void outOfMemory() {
        assertEval("import java.util.*;");
        assertEval("List<byte[]> list = new ArrayList<>();");
        assertExecuteException("while (true) { list.add(new byte[10000]); }", OutOfMemoryError.class);
    }

    public void stackOverflow() {
        assertEval("void f() { f(); }");
        assertExecuteException("f();", StackOverflowError.class);
    }

    private StackTraceElement newStackTraceElement(String className, String methodName, Snippet key, int lineNumber) {
        return new StackTraceElement(className, methodName, "#" + key.id(), lineNumber);
    }

    private static class ExceptionInfo {
        public final Class<? extends Throwable> exception;
        public final String message;
        public final StackTraceElement[] stackTraceElements;

        public ExceptionInfo(Class<? extends Throwable> exception, String message, StackTraceElement...stackTraceElements) {
            this.exception = exception;
            this.message = message;
            this.stackTraceElements = stackTraceElements.length == 0 ? null : stackTraceElements;
        }
    }

    private void assertExecuteException(String input, Class<? extends Throwable> exception) {
        assertExceptionMatch(assertEvalException(input), new ExceptionInfo(exception, null));
    }

    private void assertExceptionMatch(SnippetEvent cr, ExceptionInfo exceptionInfo) {
        assertNotNull(cr.exception(), "Expected exception was not thrown: " + exceptionInfo.exception);
        if (cr.exception() instanceof EvalException) {
            EvalException ex = (EvalException) cr.exception();
            String actualException = ex.getExceptionClassName();
            String expectedException = exceptionInfo.exception.getCanonicalName();
            String stackTrace = getStackTrace(ex);
            String source = cr.snippet().source();
            assertEquals(actualException, expectedException,
                    String.format("Given \"%s\" expected exception: %s, got: %s%nStack trace:%n%s",
                            source, expectedException, actualException, stackTrace));
            if (exceptionInfo.message != null) {
                assertEquals(ex.getMessage(), exceptionInfo.message,
                        String.format("Given \"%s\" expected message: %s, got: %s",
                                source, exceptionInfo.message, ex.getMessage()));
            }
            if (exceptionInfo.stackTraceElements != null) {
                assertStackTrace(ex.getStackTrace(), exceptionInfo.stackTraceElements,
                        String.format("Given \"%s\"%nStack trace:%n%s%n",
                                source, stackTrace));
            }
        } else {
            fail("Unexpected execution exceptionInfo: " + cr.exception());
        }
    }

    private void assertStackTrace(StackTraceElement[] actual, StackTraceElement[] expected, String message) {
        if (actual != expected) {
            if (actual == null || expected == null) {
                fail(message);
            } else {
                assertEquals(actual.length, expected.length, message + " : arrays do not have the same size");
                for (int i = 0; i < actual.length; ++i) {
                    StackTraceElement actualElement = actual[i];
                    StackTraceElement expectedElement = expected[i];
                    assertEquals(actualElement.getClassName(), expectedElement.getClassName(), message + " : class names");
                    String expectedMethodName = expectedElement.getMethodName();
                    if (expectedMethodName.startsWith("lambda$")) {
                        assertTrue(actualElement.getMethodName().startsWith("lambda$"), message + " : method names");
                    } else {
                        assertEquals(actualElement.getMethodName(), expectedElement.getMethodName(), message + " : method names");
                    }
                    assertEquals(actualElement.getFileName(), expectedElement.getFileName(), message + " : file names");
                    assertEquals(actualElement.getLineNumber(), expectedElement.getLineNumber(), message + " : line numbers");
                }
            }
        }
    }

    private String getStackTrace(EvalException ex) {
        StringWriter st = new StringWriter();
        ex.printStackTrace(new PrintWriter(st));
        return st.toString();
    }
}
