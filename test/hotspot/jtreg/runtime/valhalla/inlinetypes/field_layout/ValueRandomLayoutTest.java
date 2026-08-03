/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java ValueClassGenerator.java ValueRandomLayoutTest.java
 * @run main/othervm/timeout=2000 runtime.valhalla.inlinetypes.field_layout.ValueRandomLayoutTest
 */

package runtime.valhalla.inlinetypes.field_layout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;


public class ValueRandomLayoutTest {

  static class TestRunner {
      public static void main(String[] args) throws Exception {
          for (String s : args) {
              Class.forName(s);
          }
      }
  }

  static ProcessBuilder exec(boolean useAtomicFlat, boolean useNullableAtomicFlat,
                             boolean useNullableNonAtomicFlat, Path tempWorkDir, String... args) throws Exception {
      List<String> argsList = new ArrayList<>();
      String classpath = System.getProperty("java.class.path") + System.getProperty("path.separator") +
                         tempWorkDir.toString();
      Collections.addAll(argsList, "-cp", classpath);
      Collections.addAll(argsList, "--enable-preview");
      Collections.addAll(argsList, "-XX:+UnlockDiagnosticVMOptions");
      Collections.addAll(argsList, "-XX:+UnlockExperimentalVMOptions");
      Collections.addAll(argsList, "-XX:+PrintFieldLayout");
      Collections.addAll(argsList, "-Xshare:off");
      Collections.addAll(argsList, "-Xmx1g");
      Collections.addAll(argsList, useAtomicFlat ? "-XX:+UseNullFreeAtomicValueFlattening" : "-XX:-UseNullFreeAtomicValueFlattening");
      Collections.addAll(argsList, useNullableAtomicFlat ?  "-XX:+UseNullableAtomicValueFlattening" : "-XX:-UseNullableAtomicValueFlattening");
      Collections.addAll(argsList, useNullableNonAtomicFlat ? "-XX:+UseNullableNonAtomicValueFlattening" : "-XX:-UseNullableNonAtomicValueFlattening");
      Collections.addAll(argsList, args);
      return ProcessTools.createTestJavaProcessBuilder(argsList);
  }


  public static void main(String[] args) throws Exception {
      // These tests consume a lot of resources, let run them sequentially instead of in parallel
      for (int i = 0; i <= 5; i++) {
          System.out.println("Running scenario " + i);
          runScenario(i);
      }
  }

  static void runScenario(int config) throws Exception {

      boolean useAtomicFlat;
      boolean useNullableAtomicFlat;
      boolean useNullableNonAtomicFlat;

      switch(config) {
          case 0: useAtomicFlat = false;
                  useNullableAtomicFlat = false;
                  useNullableNonAtomicFlat = false;
                  break;
          case 1: useAtomicFlat = true;
                  useNullableAtomicFlat = true;
                  useNullableNonAtomicFlat = true;
                  break;
          case 2: useAtomicFlat = false;
                  useNullableAtomicFlat = true;
                  useNullableNonAtomicFlat = false;
                  break;
          case 3: useAtomicFlat = true;
                  useNullableAtomicFlat = false;
                  useNullableNonAtomicFlat = false;
                  break;
          case 4: useAtomicFlat = true;
                  useNullableAtomicFlat = true;
                  useNullableNonAtomicFlat = false;
                  break;
          case 5: useAtomicFlat = false;
                  useNullableAtomicFlat = true;
                  useNullableNonAtomicFlat = true;
                  break;
          default: throw new RuntimeException("Unrecognized configuration");
      }

      Path tempWorkDir;
      // Generate test classes with the given configuration

      for (int i = 0; i < 10; i++) {
          try {
              tempWorkDir = Utils.createTempDirectory("generatedClasses_");
          } catch (Exception e) {
              System.err.println("Failed to create temporary directory: " + e.getMessage());
              e.printStackTrace();
              throw new RuntimeException(e);
          }

          System.out.println("Running scenario " + config + " in directory : " + tempWorkDir);
          var gen = new ValueClassGenerator(Utils.getRandomInstance(), 256);
          gen.generateAll(128, tempWorkDir);

          String[] classNames = gen.getValueClassesNames().toArray(new String[0]);
          String[] testArgs = new String[classNames.length+1];
          testArgs[0] = "runtime.valhalla.inlinetypes.field_layout.ValueRandomLayoutTest$TestRunner";
          System.arraycopy(classNames, 0, testArgs, 1, classNames.length);

          // Execute the test runner in charge of loading all test classes
          ProcessBuilder pb = exec(useAtomicFlat, useNullableAtomicFlat, useNullableNonAtomicFlat, tempWorkDir,testArgs);
          OutputAnalyzer out = new OutputAnalyzer(pb.start());

          // Checking the status of the process execution before trying to parse the output
          out.shouldHaveExitValue(0);

          // Get and parse the test output
          FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());
          FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);

          // Verify that all layouts are correct
          try {
              fla.check();
          } catch (Throwable t) {
              System.out.print(out.getOutput());
              throw t;
          }
      }
  }
 }
