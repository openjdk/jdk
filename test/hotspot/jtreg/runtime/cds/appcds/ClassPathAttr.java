/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Class-Path: attribute in MANIFEST file
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @run driver/timeout=240 ClassPathAttr
 */

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;


public class ClassPathAttr {

  public static void main(String[] args) throws Exception {
    testNormalOps();
    testNonExistentJars();
    testClassPathAttrJarOnCP();
  }

  static void testNormalOps() throws Exception {
    buildCpAttr("cpattr1", "cpattr1.mf", "CpAttr1", "CpAttr1");
    buildCpAttr("cpattr1_long", "cpattr1_long.mf", "CpAttr1", "CpAttr1");
    buildCpAttr("cpattr2", "cpattr2.mf", "CpAttr2", "CpAttr2");
    buildCpAttr("cpattr3", "cpattr3.mf", "CpAttr3", "CpAttr2", "CpAttr3");
    buildCpAttr("cpattr4", "cpattr4.mf", "CpAttr4",
        "CpAttr2", "CpAttr3", "CpAttr4", "CpAttr5");
    buildCpAttr("cpattr5_123456789_223456789_323456789_423456789_523456789_623456789", "cpattr5_extra_long.mf", "CpAttr5", "CpAttr5");

    String[] classlist = { "CpAttr1", "CpAttr2", "CpAttr3", "CpAttr4", "CpAttr5"};
    String jar4 = TestCommon.getTestJar("cpattr4.jar");
    for (int i=1; i<=2; i++) {
      String jar1 = TestCommon.getTestJar("cpattr1.jar");
      if (i == 2) {
        // Test case #2 -- same as #1, except we use cpattr1_long.jar, which has a super-long
        // Class-Path: attribute.
        jar1 = TestCommon.getTestJar("cpattr1_long.jar");
      }
      String cp = jar1 + File.pathSeparator + jar4;

      TestCommon.testDump(cp, classlist);

      TestCommon.run(
          "-cp", cp,
          "CpAttr1")
        .assertNormalExit();

      // Logging test for class+path.
      TestCommon.run(
          "-Xlog:class+path",
          "-cp", cp,
          "CpAttr1")
        .assertNormalExit(output -> {
            output.shouldMatch("checking shared classpath entry: .*cpattr2.jar");
            output.shouldMatch("checking shared classpath entry: .*cpattr3.jar");
          });

      // Test handling of forward slash ('/') file separator when locating entries
      // in the classpath entry on Windows.
      // Skip the following test when CDS dynamic dump is enabled due to some
      // issue when converting a relative path to real path.
      if (Platform.isWindows() && !CDSTestUtils.DYNAMIC_DUMP) {
          // Test with relative path
          // Find the index to the dir before the jar file.
          int idx = jar1.lastIndexOf(File.separator);
          idx = jar1.substring(0, idx - 1).lastIndexOf(File.separator);
          // Setup jar directory and names.
          String jarDir = jar1.substring(0, idx);
          String jar1Name = jar1.substring(idx + 1);
          String jar4Name = jar4.substring(idx + 1);
          String newCp = jar1Name.replace("\\", "/") + File.pathSeparator + jar4Name.replace("\\", "/");

          OutputAnalyzer out = TestCommon.testDump(jarDir, newCp, classlist, "-Xlog:class+path=info");
          if (i == 1) {
              out.shouldMatch("opened:.*cpattr1.jar"); // first jar on -cp
          } else {
              // first jar on -cp with long Class-Path: attribute
              out.shouldMatch("opened:.*cpattr1_long.jar");
          }
          // one of the jar in the Class-Path: attribute of cpattr1.jar
          out.shouldMatch("opened:.*cpattr2.jar");

          TestCommon.runWithRelativePath(
              jarDir.replace("\\", "/"),
              "-Xlog:class+path,class+load",
              "-cp", newCp,
              "CpAttr1")
            .assertNormalExit(output -> {
                output.shouldMatch("checking shared classpath entry: .*cpattr2.jar");
                output.shouldMatch("checking shared classpath entry: .*cpattr3.jar");
              });

          // Go one directory up.
          int idx2 = jar1.substring(0, idx - 1).lastIndexOf(File.separator);
          if (idx2 != -1) {
              // Setup jar directory and names.
              jarDir = jar1.substring(0, idx2);
              // Set relative path to jar containing '\' and '/' file separators
              // e.g. d1\d2/A.jar
              jar1Name = jar1.substring(idx2 + 1).replace("\\", "/");
              jar4Name = jar4.substring(idx2 + 1).replace("\\", "/");
              jar1Name = jar1Name.replaceFirst("/", "\\\\");
              jar4Name = jar4Name.replaceFirst("/", "\\\\");

              newCp = jar1Name + File.pathSeparator + jar4Name;
              out = TestCommon.testDump(jarDir, newCp, classlist, "-Xlog:class+path=info");
              if (i == 1) {
                  out.shouldMatch("opened:.*cpattr1.jar"); // first jar on -cp
              } else {
                  // first jar on -cp with long Class-Path: attribute
                  out.shouldMatch("opened:.*cpattr1_long.jar");
              }
              // one of the jar in the Class-Path: attribute of cpattr1.jar
              out.shouldMatch("opened:.*cpattr2.jar");

              TestCommon.runWithRelativePath(
                  jarDir.replace("\\", "/"),
                  "-Xlog:class+path,class+load",
                  "-cp", newCp,
                  "CpAttr1")
                .assertNormalExit(output -> {
                    output.shouldMatch("checking shared classpath entry: .*cpattr2.jar");
                    output.shouldMatch("checking shared classpath entry: .*cpattr3.jar");
                  });
          }
      }
    }

    // test duplicate jars in the "Class-path" attribute in the jar manifest
    buildCpAttr("cpattr_dup", "cpattr_dup.mf", "CpAttr1", "CpAttr1");
    String cp = TestCommon.getTestJar("cpattr_dup.jar") + File.pathSeparator + jar4;
    TestCommon.testDump(cp, classlist);

    TestCommon.run(
        "-cp", cp,
        "CpAttr1")
      .assertNormalExit();
  }

  static void testNonExistentJars() throws Exception {
    buildCpAttr("cpattr6", "cpattr6.mf", "CpAttr6", "CpAttr6");

    String cp = TestCommon.getTestJar("cpattr6.jar");
    String nonExistPath = CDSTestUtils.getOutputDir() + File.separator + "cpattrX.jar";
    (new File(nonExistPath)).delete();

    TestCommon.testDump(cp, TestCommon.list("CpAttr6"),
        "-Xlog:class+path");

    TestCommon.run(
        "-Xlog:class+path",
        "-cp", cp,
        "CpAttr6")
      .assertNormalExit(output -> {
          output.shouldMatch("should be non-existent: .*cpattrX.jar");
        });

    // Now make nonExistPath exist. CDS still loads, but archived non-system classes will not be used.
    Files.copy(Paths.get(cp), Paths.get(nonExistPath),
               StandardCopyOption.REPLACE_EXISTING);

    CDSTestUtils.Result result = TestCommon.run(
        "-Xlog:class+path",
        "-cp", cp,
        "CpAttr6");
    if (CDSTestUtils.isAOTClassLinkingEnabled()) {
        result.assertAbnormalExit(output -> {
                output.shouldMatch("CDS archive has aot-linked classes. It cannot be used because the file .*cpattrX.jar exists");
            });

    } else {
        result.assertNormalExit(output -> {
                output.shouldMatch("Archived non-system classes are disabled because the file .*cpattrX.jar exists");
            });
    }
  }

  static void testClassPathAttrJarOnCP() throws Exception {
    String helloJar = JarBuilder.getOrCreateHelloJar();
    String jar1 = TestCommon.getTestJar("cpattr1.jar");
    String cp = jar1 + File.pathSeparator + helloJar;

    // The cpattr1.jar contains "Class-Path: cpattr2.jar".
    // The cpattr2.jar contains "Class-Path: cpattr3.jar cpattr5_123456789_223456789_323456789_42345678.jar".
    // With -cp cpattr1:hello.jar, the following shared paths should be stored in the CDS archive:
    // cpattr1.jar:cpattr2.jar:cpattr3.jar:cpattr5_123456789_223456789_323456789_42345678.jari:hello.jar
    TestCommon.testDump(cp, TestCommon.list("Hello"), "-Xlog:class+path");

    // Run with the same -cp apattr1.jar:hello.jar. The Hello class should be
    // loaded from the archive.
    TestCommon.run("-Xlog:class+path,class+load",
                   "-cp", cp,
                   "Hello")
              .assertNormalExit(output -> {
                  output.shouldContain("Hello source: shared objects file");
                });

    // Run with -cp apattr1.jar:cpattr2.jar:hello.jar. App classpath mismatch should be detected.
    String jar2 = TestCommon.getTestJar("cpattr2.jar");
    cp = jar1 + File.pathSeparator + jar2 + File.pathSeparator + helloJar;
    TestCommon.run("-Xlog:class+path,class+load",
                   "-cp", cp,
                   "Hello")
              .assertAbnormalExit(output -> {
                  output.shouldMatch(".*APP classpath mismatch, actual: -Djava.class.path=.*cpattr1.jar.*cpattr2.jar.*hello.jar")
              .shouldContain("Unable to use shared archive.");
                });

    // Run with different -cp cpattr2.jar:hello.jar. App classpath mismatch should be detected.
    cp = jar2 + File.pathSeparator + helloJar;
    TestCommon.run("-Xlog:class+path,class+load",
                   "-cp", cp,
                   "Hello")
              .assertAbnormalExit(output -> {
                  output.shouldMatch(".*APP classpath mismatch, actual: -Djava.class.path=.*cpattr2.jar.*hello.jar")
              .shouldContain("Unable to use shared archive.");
                });

    // Dumping with -cp cpattr1.jar:cpattr2.jar:hello.jar
    // The cpattr2.jar is from the Class-Path: attribute of cpattr1.jar.
    cp = jar1 + File.pathSeparator + jar2 + File.pathSeparator + helloJar;
    TestCommon.testDump(cp, TestCommon.list("Hello"), "-Xlog:class+path");

    // Run with the same -cp as dump time. The Hello class should be loaded from the archive.
    TestCommon.run("-Xlog:class+path,class+load",
                   "-cp", cp,
                   "Hello")
              .assertNormalExit(output -> {
                  output.shouldContain("Hello source: shared objects file");
                });

  }

  private static void buildCpAttr(String jarName, String manifest, String enclosingClassName, String ...testClassNames) throws Exception {
    String jarClassesDir = CDSTestUtils.getOutputDir() + File.separator + jarName + "_classes";
    try { Files.createDirectory(Paths.get(jarClassesDir)); } catch (FileAlreadyExistsException e) { }

    JarBuilder.compile(jarClassesDir, System.getProperty("test.src") + File.separator +
        "test-classes" + File.separator + enclosingClassName + ".java");
    JarBuilder.buildWithManifest(jarName, manifest, jarClassesDir, testClassNames);
  }
}
