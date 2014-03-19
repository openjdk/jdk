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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

/**
 * @test
 * @bug 8037378
 * @summary Sanity tests for no persistence caching
 * @run testng/othervm jdk.nashorn.internal.runtime.NoPersistenceCachingTest
 */
public class NoPersistenceCachingTest {

   private ScriptEngine engine;
   private ScriptContext context1, context2, context3;
   private ByteArrayOutputStream stderr;
   private PrintStream prevStderr;

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
      String[] options = new String[]{"--log=compiler:finest"};
      engine = nashornFactory.getScriptEngine(options);
      context1 = engine.getContext();
      context2 = new SimpleScriptContext();
      context2.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
      context3 = new SimpleScriptContext();
      context3.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
   }

   @AfterTest
   public void setErrTest() {
      System.setErr(prevStderr);
   }

   public void runTest(int numberOfContext, String expectedOutputPattern,
                       int expectedPatternOccurrence) {

      try {
         switch (numberOfContext) {
         case 2:
            String scriptTwoContexts = "print('HelloTwoContexts')";
            engine.eval(scriptTwoContexts, context1);
            engine.eval(scriptTwoContexts, context2);
            break;
         case 3:
            String scriptThreeContexts = "print('HelloThreeContexts')";
            engine.eval(scriptThreeContexts, context1);
            engine.eval(scriptThreeContexts, context2);
            engine.eval(scriptThreeContexts, context3);
            break;
         }
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
         fail("Number of cache hit is not correct, expected: "
                    + expectedPatternOccurrence + " and found: " + matches + "\n"
              + stderr);
      }
      stderr.reset();
   }

   private static String getCodeCachePattern() {
      return ("\\[compiler\\]\\sCode\\scache\\shit\\sfor\\s<eval>\\savoiding\\srecompile.");
   }

    @Test
    public void twoContextTest() {
       runTest(2, getCodeCachePattern(), 1);

    }

    @Test
    public void threeContextTest() {
       runTest(3, getCodeCachePattern(), 2);
    }
}
