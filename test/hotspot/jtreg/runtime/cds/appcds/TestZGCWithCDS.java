/*
 * @test 8232069 for ZGC
 * @requires vm.cds
 * @requires (vm.gc=="null")
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. TestZGCWithCDS
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

import sun.hotspot.gc.GC;
import sun.hotspot.code.Compiler;

public class TestZGCWithCDS {
    public final static String HELLO = "Hello World";
    public final static String UNABLE_TO_USE_ARCHIVE = "Unable to use shared archive.";
    public final static String ERR_MSG = "The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime, CDS will be disabled.";
    public static void main(String... args) throws Exception {
         // The test is only for 64-bit
         if (!Platform.is64bit()) {
             throw new SkippedException("Platform is not 64 bit, skipped");
         }

         // Platform must support ZGC
         if (!GC.Z.isSupported()) {
             throw new SkippedException("Platform does not support ZGC, skipped");
         } else if (Compiler.isGraalEnabled()) {
             throw new SkippedException("Graal does not support ZGC, skipped");
         }

         String helloJar = JarBuilder.build("hello", "Hello");
         // 0. dump with ZGC
         System.out.println("0. Dump with ZGC");
         OutputAnalyzer out = TestCommon
                                  .dump(helloJar,
                                        new String[] {"Hello"},
                                        "-XX:+UseZGC",
                                        "-Xlog:cds");
         out.shouldContain("Dumping shared data to file:");
         out.shouldHaveExitValue(0);

         // 1. Run with same args of dump
         System.out.println("1. Run with same args of dump");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:+UseZGC",
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(HELLO);
         out.shouldHaveExitValue(0);

         // 2. Run with ZGC turned off
         System.out.println("2. Run with ZGC turned off");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:-UseZGC",
                         "-XX:+UseCompressedOops",           // in case turned off by vmoptions
                         "-XX:+UseCompressedClassPointers",  // by jtreg
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(UNABLE_TO_USE_ARCHIVE);
         out.shouldContain(ERR_MSG);
         out.shouldHaveExitValue(1);

         // 3. Run with -UseCompressedOops -UseCompressedClassPointers
         System.out.println("3. Run with -UseCompressedOops -UseCompressedClassPointers");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:-UseCompressedOops",
                         "-XX:-UseCompressedClassPointers",
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(HELLO);
         out.shouldHaveExitValue(0);

         // 4. Run with +UseCompressedOops -UseCompressedClassPointers
         System.out.println("4. Run with +UseCompressedOops -UseCompressedClassPointers");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:+UseCompressedOops",
                         "-XX:-UseCompressedClassPointers",
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(UNABLE_TO_USE_ARCHIVE);
         out.shouldContain(ERR_MSG);
         out.shouldHaveExitValue(1);

         // 5. Run with +UseCompressedOops +UseCompressedClassPointers
         System.out.println("5. Run with +UseCompressedOops +UseCompressedClassPointers");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:+UseCompressedOops",
                         "-XX:+UseCompressedClassPointers",
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(UNABLE_TO_USE_ARCHIVE);
         out.shouldContain(ERR_MSG);
         out.shouldHaveExitValue(1);

         // 6. dump with -UseCompressedOops -UseCompressedClassPointers
         System.out.println("6. Dump with -UseCompressedOops -UseCompressedClassPointers");
         out = TestCommon
                   .dump(helloJar,
                         new String[] {"Hello"},
                         "-XX:-UseCompressedOops",
                         "-XX:-UseCompressedClassPointers",
                         "-Xlog:cds");
         out.shouldContain("Dumping shared data to file:");
         out.shouldHaveExitValue(0);

         // 7. Run with ZGC
         System.out.println("7. Run with ZGC");
         out = TestCommon
                   .exec(helloJar,
                         "-XX:+UseZGC",
                         "-Xlog:cds",
                         "Hello");
         out.shouldContain(HELLO);
         out.shouldHaveExitValue(0);
    }
}
