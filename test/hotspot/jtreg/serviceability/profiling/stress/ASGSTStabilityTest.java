/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Google and/or its affiliates. All rights reserved.
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

package profiling.stress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ClassLoader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.lang.reflect.Method;

import jdk.test.lib.process.*;
import jdk.test.whitebox.WhiteBox;

/**
 * @test
 * @summary Verifies that AsyncGetStackTrace usage is stable in a high-frequency signal sampler
 * @library /test/jdk/lib/testlibrary /test/lib
 * @compile ASGSTStabilityTest.java
 * @key stress
 * @requires os.family == "linux"
 * @requires os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64" | os.arch=="arm" | os.arch=="aarch64" | os.arch=="ppc64" | os.arch=="s390" | os.arch=="riscv64"
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm/native/timeout=216000 profiling.stress.ASGSTStabilityTest akka-uct 10
 * @run main/othervm/native/timeout=216000 profiling.stress.ASGSTStabilityTest finagle-chirper 120
 * @run main/othervm/native/timeout=216000 profiling.stress.ASGSTStabilityTest finagle-http 120
 */

public class ASGSTStabilityTest {
  private static final String RENAISSANCE_URL = "https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.14.1/renaissance-gpl-0.14.1.jar";

  public static final class Runner {
    private static final String MAIN_CLASS = "org.renaissance.core.Launcher";
    public static void main(String[] args) throws Exception {
      WhiteBox wb = WhiteBox.getWhiteBox();

      // start a thread which will randomly deoptimize frames in order
      // to increase the chance of hitting problems when walking the stack
      // in the middle of opt/deopt - this used to be a source of various
      // crashes in the original ASGCT so it sounds useful to have a regtest
      // covering it
      Thread t = new Thread(() -> {
        Random rnd = new Random(845123525117L);
        while (!Thread.currentThread().isInterrupted()) {
          try {
            Thread.sleep(100 + rnd.nextInt(300));
            wb.deoptimizeFrames(true);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            break;
          } 
        }
      });
      t.setDaemon(true);
      t.start();

      Class<?> clz = Runner.class.getClassLoader().loadClass(MAIN_CLASS);
      clz.getMethod("main", String[].class).invoke(null, (Object)args);

    }
  }

  private static void downloadRenaissance(Path target) throws IOException {
    System.out.println("Downloading " + RENAISSANCE_URL + " to " + target);
    URL url = new URL(RENAISSANCE_URL);
    ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
    
    try (FileOutputStream os = new FileOutputStream(target.toFile())) {
      os.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    }
    System.out.println("Downloaded renaissance.jar to " + target);
  }

  public static void main(String[] args) throws Exception {
    String tmpPath = System.getProperty("java.io.tmpdir");
    Path jarPath = Paths.get(tmpPath, "renaissance.jar");
    if (!Files.exists(jarPath)) {
      downloadRenaissance(jarPath);
    }

    if (!Files.exists(jarPath)) {
      throw new Error("Renaissance library not found.");
    }
    
    Path out = Paths.get("renaissance.out").toAbsolutePath();
    String[] benchmarks = new String[] {
      "akka-uct", "finagle-chirper", "finagle-http", "akka-uct"
    };
    String benchmark = args[0];
    int duration = Integer.parseInt(args[1]);
    System.out.println("=== Going to run '" + benchmark + "' benchmark for " + duration + " seconds ...");
    String testCp = System.getProperty("test.class.path") + 
                      File.pathSeparator + jarPath.toString();
    System.out.println("===> Classpath: " + testCp);
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      List.of(
        "-Xbootclasspath/a:./WhiteBox.jar",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+WhiteBoxAPI", 
        "-agentlib:AsyncGetStackTraceSampler",
        "-Djava.library.path=" + System.getProperty("test.nativepath"),
        "-cp", testCp, 
        ASGSTStabilityTest.Runner.class.getName(),
        benchmark, "-t", Integer.toString(duration)))
      .redirectErrorStream(true);
    ProcessTools.executeProcess(pb)
      .shouldHaveExitValue(0)
      .shouldContain("=== asgst sampler initialized ===");

    System.out.println("=== ... done");
  }
}
