/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.runtime;

import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8037086
 * @run testng/othervm jdk.nashorn.internal.runtime.OptimisticRecompilationTest
 */
public class OptimisticRecompilationTest {

   private ScriptEngine engine;
   private ByteArrayOutputStream stderr;
   private PrintStream prevStderr;

   public void runTest(String script, String expectedOutputPattern,
                       int expectedPatternOccurrence) {
      try {
         engine.eval(script);
      } catch (final Exception se) {
         se.printStackTrace();
         fail(se.getMessage());
      }
      Pattern deoptimizing = Pattern.compile(expectedOutputPattern);
      Matcher matcher = deoptimizing.matcher(stderr.toString());
      int matches = 0;
      while (matcher.find()) {
         matches++;
      }
      if (matches != expectedPatternOccurrence) {
         fail("Number of Deoptimizing recompilation is not correct, expected: "
                    + expectedPatternOccurrence + " and found: " + matches + "\n"
              + stderr);
      }
      stderr.reset();
   }

   private static String getRecompilationPattern(String type, String value) {
        return "\\[recompile\\]\\s*RewriteException\\s\\[programPoint\\=(\\d+)\\s"
           + "returnType=" + type + "\\s\\(" + value + "\\)";
   }

    @BeforeTest
    public void setupTest() {
       stderr = new ByteArrayOutputStream();
       prevStderr = System.err;
       System.setErr(new PrintStream(stderr));
       NashornScriptEngineFactory nashornFactory = null;
       ScriptEngineManager sm = new ScriptEngineManager();
       for (ScriptEngineFactory fac : sm.getEngineFactories()) {
          if (fac instanceof NashornScriptEngineFactory) {
             nashornFactory = (NashornScriptEngineFactory) fac;
             break;
          }
       }
       if (nashornFactory == null) {
          fail("Cannot find nashorn factory!");
       }
       String[] options = new String[]{"--log=recompile"};
       engine = nashornFactory.getScriptEngine(options);
    }

    @AfterTest
    public void setErrTest() {
       System.setErr(prevStderr);
    }

    @Test
    public void divisionByZeroTest() {
        //Check that one Deoptimizing recompilation and RewriteExceptions happened
        runTest("function f() {var x1 = { a: 2, b:1 }; x1.a = Number.POSITIVE_INFINITY;"
                + " x1.b = 0; print(x1.a/x1.b);} f()",
                getRecompilationPattern("double", "Infinity"), 1);
    }

    @Test
    public void divisionWithRemainderTest() {
       //Check that one Deoptimizing recompilation and RewriteException happened
        runTest("function f() {var x2 = { a: 7, b:2 }; print(x2.a/x2.b);} f()",
               getRecompilationPattern("double", "3.5"), 1);
    }

    @Test
    public void infinityMultiplicationTest() {
        //Check that one deoptimizing recompilation and RewriteExceptions happened
        runTest("function f() {var x3 = { a: Number.POSITIVE_INFINITY, "
                + "b: Number.POSITIVE_INFINITY}; print(x3.a*x3.b);} f()",
                getRecompilationPattern("double", "Infinity"), 1);
    }

    @Test
    public void maxValueMultiplicationTest() {
        runTest("function f() {var x4 = { a: Number.MAX_VALUE, b: Number.MAX_VALUE};"
                + " print(x4.a*x4.b);} f()",
                getRecompilationPattern("double", "1.7976931348623157E308"), 1);
    }

    @Test
    public void divisionByInfinityTest() {
        //Check that one Deoptimizing recompilation and RewriteExceptions happened
        runTest("function f() {var x5 = { a: -1, b: Number.POSITIVE_INFINITY};"
                + " print(x5.a/x5.b);} f()",
                getRecompilationPattern("double", "Infinity"), 1);
    }

    @Test
    public void divisionByStringTest() {
        //Check that one deoptimizing recompilations and RewriteExceptions happened
        runTest("function f() {var x6 = { a: Number.POSITIVE_INFINITY, b: 'Hello'};"
                + " print(x6.a/x6.b);} f()", getRecompilationPattern("double", "Infinity"), 1);
    }

    @Test
    public void nestedFunctionTest() {
       //Check that one Deoptimizing recompilations and RewriteExceptions happened
        runTest("var a=3,b,c; function f() {var x7 = 2, y =1; function g(){ "
                + "var y = x7; var z = a; z = x7*y; print(a*b); } g() } f()",
               getRecompilationPattern("object", "undefined"), 1);
    }

    @Test
    public void andTest() {
       //Check that one Deoptimizing recompilations and RewriteExceptions happened
       runTest("function f(a,b) { d = a && b; print(d);} f()",
               "\\[recompile\\]\\sDeoptimizing\\srecompilation\\sof\\s\'f\'", 1);
    }

    @Test
    public void functionTest() {
       //Check that one Deoptimizing recompilations and RewriteExceptions happened
        runTest("function f(a,b,c) { h = (a + b) * c; print(h);} f()",
               getRecompilationPattern("double", "NaN"), 1);
    }
}
