/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=32
 * @requires vm.bits == 32
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 32
 */

/*
 * @test id=64_COOP_CCP_NCOH
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 64_COOP_CCP_NCOH
 */

/*
 * @test id=64_NCOOP_CCP_NCOH
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 64_NCOOP_CCP_NCOH
 */

/*
 * @test id=64_NCOOP_NCCP_NCOH
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 64_NCOOP_NCCP_NCOH
 */

/*
 * @test id=64_COOP_CCP_COH
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 64_COOP_CCP_COH
 */

/*
 * @test id=64_NCOOP_CCP_COH
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java FieldAlignmentTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.FieldAlignmentTest 64_NCOOP_CCP_COH
 */

 package runtime.valhalla.inlinetypes.field_layout;

 import java.util.ArrayList;
 import java.util.Collections;
 import java.util.List;

 import jdk.internal.vm.annotation.LooselyConsistentValue;
 import jdk.internal.vm.annotation.NullRestricted;


 import jdk.test.lib.Asserts;
 import jdk.test.lib.ByteCodeLoader;
 import jdk.test.lib.helpers.ClassFileInstaller;
 import jdk.test.lib.compiler.InMemoryJavaCompiler;
 import jdk.test.lib.process.OutputAnalyzer;
 import jdk.test.lib.process.ProcessTools;

 public class FieldAlignmentTest {
  public static class ZeroByte { }
  public static class OneByte { byte b; }
  public static class TwoByte { byte b0; byte b1; }
  public static class ThreeByte { byte b0; byte b1; byte b2; }
  public static class FourByte { byte b0; byte b1; byte b2; byte b3; }
  public static class FiveByte { byte b0; byte b1; byte b2; byte b3; byte b4; }
  public static class SixByte { byte b0; byte b1; byte b2; byte b3; byte b4; byte b5; }
  public static class SevenByte { byte b0; byte b1; byte b2; byte b3; byte b4; byte b5; byte b6; }
  public static final String[] superNames = { ZeroByte.class.getCanonicalName(),
                                              OneByte.class.getCanonicalName(),
                                              TwoByte.class.getCanonicalName(),
                                              ThreeByte.class.getCanonicalName(),
                                              FourByte.class.getCanonicalName(),
                                              FiveByte.class.getCanonicalName(),
                                              SixByte.class.getCanonicalName(),
                                              SevenByte.class.getCanonicalName() };
  public static final String[] valueNames = { ValueOneByte.class.getCanonicalName(),
                                              ValueOneChar.class.getCanonicalName(),
                                              ValueOneShort.class.getCanonicalName(),
                                              ValueOneInt.class.getCanonicalName(),
                                              ValueOneLong.class.getCanonicalName(),
                                              ValueOneFloat.class.getCanonicalName(),
                                              ValueOneDouble.class.getCanonicalName(),
                                              ValueByteLong.class.getCanonicalName(),
                                              ValueByteInt.class.getCanonicalName() };

  List<String> testNames = new ArrayList<String>();

  @LooselyConsistentValue public static value class ValueOneByte { byte val = 0; }
  @LooselyConsistentValue public static value class ValueOneChar { char val = 0; }
  @LooselyConsistentValue public static value class ValueOneShort { short val = 0; }
  @LooselyConsistentValue public static value class ValueOneInt { int val = 0; }
  @LooselyConsistentValue public static value class ValueOneLong { long val = 0; }
  @LooselyConsistentValue public static value class ValueOneFloat { float val = 0f; }
  @LooselyConsistentValue public static value class ValueOneDouble { double val = 0d; }

  @LooselyConsistentValue public static value class ValueByteLong { byte b = 0; long l = 0; }
  @LooselyConsistentValue public static value class ValueByteInt { byte b = 0; int i = 0; }

  void generateTests() throws Exception {
    for (String vName : valueNames) {
      for (String sName : superNames) {
        String vNameShort = vName.substring(vName.lastIndexOf('.') + 1);
        String sNameShort = sName.substring(sName.lastIndexOf('.') + 1);
        String className = "Test" + vNameShort + "With" + sNameShort;
        String sourceCode = "import jdk.internal.vm.annotation.NullRestricted;" +
                            "public class " + className + " extends " + sName + " { " +
                            "    @NullRestricted" +
                            "    " + vName + " v1;" +
                            "    public " + className + "() {" +
                            "      v1 = new " + vName + "();" +
                            "      super();" +
                            "    }" +
                            "}";
        String java_version = System.getProperty("java.specification.version");
        byte[] byteCode = InMemoryJavaCompiler.compile(className, sourceCode,
                                                      "-source", java_version, "--enable-preview",
                                                      "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED");
        jdk.test.lib.helpers.ClassFileInstaller.writeClassToDisk(className, byteCode);
        testNames.add(className);
      }
    }
  }

  void generateTestRunner() throws Exception {
    String className = "TestRunner";
    StringBuilder sb = new StringBuilder();
    sb.append("public class ").append(className).append(" {");
    sb.append("    public void main(String[] args) {");
    for (String name : testNames) {
      sb.append("        ").append(name).append(" var").append(name).append(" = new ").append(name).append("();");
    }
    sb.append("    }");
    sb.append("}");
    String java_version = System.getProperty("java.specification.version");
    byte[] byteCode = InMemoryJavaCompiler.compile(className, sb.toString(),
                                                   "-source", java_version, "--enable-preview",
                                                   "-cp", ".");
    jdk.test.lib.helpers.ClassFileInstaller.writeClassToDisk(className, byteCode);
  }

  static ProcessBuilder exec(String compressedOopsArg,
                             String compressedKlassPointersArg,
                             String compactObjectHeader,
                             String... args) throws Exception {
    List<String> argsList = new ArrayList<>();
    Collections.addAll(argsList, "--enable-preview");
    Collections.addAll(argsList, "-XX:+UnlockDiagnosticVMOptions");
    Collections.addAll(argsList, "-XX:+PrintFieldLayout");
    Collections.addAll(argsList, "-Xshare:off");
    if (compressedOopsArg != null) {
      Collections.addAll(argsList, compressedOopsArg);
    }
    if (compressedKlassPointersArg != null) {
      Collections.addAll(argsList, compressedKlassPointersArg);
    }
    if (compactObjectHeader != null) {
      Collections.addAll(argsList, compactObjectHeader);
    }
    Collections.addAll(argsList, "-Xmx256m");
    Collections.addAll(argsList, "-cp", System.getProperty("java.class.path") + System.getProperty("path.separator") +".");
    Collections.addAll(argsList, args);
    return ProcessTools.createTestJavaProcessBuilder(argsList);
  }

  public static void main(String[] args) throws Exception {
    String compressedOopsArg;
    String compressedKlassPointersArg;
    String compactObjectHeaderArg;

    switch(args[0]) {
      case "32":
        compressedOopsArg = null;
        compressedKlassPointersArg = null;
        compactObjectHeaderArg = null;
        break;
      case "64_COOP_CCP_NCOH":
        compressedOopsArg = "-XX:+UseCompressedOops";
        compressedKlassPointersArg =  "-XX:+UseCompressedClassPointers";
        compactObjectHeaderArg = "-XX:-UseCompactObjectHeaders";
        break;
      case "64_NCOOP_CCP_NCOH":
        compressedOopsArg = "-XX:-UseCompressedOops";
        compressedKlassPointersArg = "-XX:+UseCompressedClassPointers";
        compactObjectHeaderArg = "-XX:-UseCompactObjectHeaders";
        break;
      case "64_NCOOP_NCCP_NCOH":
        compressedOopsArg = "-XX:-UseCompressedOops";
        compressedKlassPointersArg = "-XX:-UseCompressedClassPointers";
        compactObjectHeaderArg = "-XX:-UseCompactObjectHeaders";
        break;
      case "64_COOP_CCP_COH":
        compressedOopsArg = "-XX:+UseCompressedOops";
        compressedKlassPointersArg = "-XX:+UseCompressedClassPointers";
        compactObjectHeaderArg = "-XX:+UseCompactObjectHeaders";
        break;
      case "64_NCOOP_CCP_COH":
        compressedOopsArg = "-XX:-UseCompressedOops";
        compressedKlassPointersArg = "-XX:+UseCompressedClassPointers";
        compactObjectHeaderArg = "-XX:+UseCompactObjectHeaders";
        break;
      default: throw new RuntimeException("Unrecognized configuration");
    }

    // Generate test classes
    FieldAlignmentTest fat = new FieldAlignmentTest();
    fat.generateTests();
    fat.generateTestRunner();

    // Execute the test runner in charge of loading all test classes
    ProcessBuilder pb = exec(compressedOopsArg, compressedKlassPointersArg, compactObjectHeaderArg, "TestRunner");
    OutputAnalyzer out = new OutputAnalyzer(pb.start());

    if (out.getExitValue() != 0) {
      out.outputTo(System.out);
    }
    Asserts.assertEquals(out.getExitValue(), 0, "Something went wrong while running the tests");

    // Analyze the test runner output
    FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());

    FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);
    try {
      fla.check();
    } catch (Throwable t) {
      out.outputTo(System.out);
      throw t;
    }
  }
 }
