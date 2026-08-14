/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8396147
 * @summary javac launcher sets -XX:TieredStopAtLevel=1 for small source sets
 * @library /test/lib
 * @run junit/othervm/timeout=600 JavacTieredStopHeuristicTest
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavacTieredStopHeuristicTest {
    private static final long THRESHOLD_BYTES = 2L * 1024 * 1024;
    private static final Pattern TIERED_STOP = Pattern.compile(
            "intx\\s+TieredStopAtLevel\\s*=\\s*(\\d+)");

    private static Path dir;
    private static Path src;

    @BeforeAll
    static void createHello() throws Exception {
        dir = Path.of(".").toAbsolutePath().normalize();
        src = dir.resolve("Hello.java");
        Files.writeString(src, "public class Hello {}\n", StandardCharsets.UTF_8);
    }

    @Test
    void smallSourcesUseLevel1() throws Exception {
        // Small explicit .java list => TieredStopAtLevel should be 1
        assertTieredStop(1, Map.of(), List.of(src.toString()));

        // @argfile with one source => also 1
        Path argfile = dir.resolve("sources.list");
        Files.writeString(argfile, argfileToken(src) + "\n", StandardCharsets.UTF_8);
        assertTieredStop(1, Map.of(), List.of("@" + argfile));
    }

    @Test
    void noSourcesDoesNotAddOption() throws Exception {
        // Nothing to size, so the heuristic does not add the option.
        assertNotLevel1(Map.of(),
                        List.of("-version"),
                        "when there are no .java sources");
    }

    @Test
    void thresholdComparedWithSourceSize() throws Exception {
        // Total size 1 byte below the 2 MiB threshold uses level 1.
        Path below = dir.resolve("BelowThreshold.java");
        writePaddedJava(below, "BelowThreshold", THRESHOLD_BYTES - 1);
        assertTieredStop(1, Map.of(), List.of(below.toString()));

        // Total size equal to the 2 MiB threshold does not use level 1.
        Path atLimit = dir.resolve("AtThreshold.java");
        writePaddedJava(atLimit, "AtThreshold", THRESHOLD_BYTES);
        assertNotLevel1(Map.of(),
                        List.of(atLimit.toString()),
                        "when source size equals 2 MiB threshold");

        // Multiple sources reaching the 2 MiB threshold together also decline.
        Path halfA = dir.resolve("HalfA.java");
        Path halfB = dir.resolve("HalfB.java");
        writePaddedJava(halfA, "HalfA", THRESHOLD_BYTES / 2);
        writePaddedJava(halfB, "HalfB", THRESHOLD_BYTES / 2);
        assertNotLevel1(Map.of(),
                        List.of(halfA.toString(), halfB.toString()),
                        "when multiple sources sum to 2 MiB threshold");
    }

    @Test
    void userJOptionOverridesHeuristic() throws Exception {
        assertTieredStop(3, Map.of(),
                         List.of("-J-XX:TieredStopAtLevel=3", src.toString()));
    }

    @Test
    void environmentVariablesWithTieredStopAtLevel() throws Exception {
        // The VM applies JAVA_TOOL_OPTIONS before the command line, so the
        // option added by the launcher wins.
        assertTieredStop(1,
                         Map.of("JAVA_TOOL_OPTIONS", "-XX:TieredStopAtLevel=3"),
                         List.of(src.toString()));

        // The javac launcher does not consult JDK_JAVA_OPTIONS.
        assertTieredStop(1,
                         Map.of("JDK_JAVA_OPTIONS", "-XX:TieredStopAtLevel=3"),
                         List.of(src.toString()));
    }

    // Any value of JDK_JAVAC_OPTIONS skips the heuristic, including empty,
    // whitespace, and flags that do not change the source set.
    @ParameterizedTest
    @ValueSource(strings = {"-sourcepath .", "-Xlint:all", "   ", ""})
    void jdkJavacOptionsSkipsHeuristic(String options) throws Exception {
        assertNotLevel1(
                Map.of("JDK_JAVAC_OPTIONS", options),
                List.of(src.toString()),
                "when JDK_JAVAC_OPTIONS=\"" + options + "\"");
    }

    // Decline options that may make javac compile sources not listed in argv
    // or use an AOT cache
    static Stream<List<String>> declineOptions() {
        return Stream.of(
                List.of("-sourcepath", "."),
                List.of("--source-path=."),
                List.of("--source-path", "."),
                List.of("--patch-module", "java.base=."),
                List.of("--patch-module=java.base=."),
                List.of("-processorpath", "."),
                List.of("--processor-path", "."),
                List.of("--processor-module-path", "."),
                List.of("--processor-module-path=."),
                List.of("-proc:full"),
                List.of("-proc:only"),
                List.of("-Xprint"),
                List.of("-Afoo=bar"),
                List.of("-Afoo"),
                List.of("-J-XX:AOTMode=off"));
    }

    @ParameterizedTest
    @MethodSource("declineOptions")
    void declineOptions(List<String> option) throws Exception {
        assertNotLevel1(Map.of(),
                        concat(option, src.toString()),
                        "with " + option);
    }

    @Test
    void aotCacheDeclines() throws Exception {
        // Explicit -J-XX:AOT* options decline the heuristic so C2 / AOT
        // tiered behavior (recording, assembly, or loading) is not altered.
        // Without a valid cache on exploded builds, these options abort VM
        // initialization before PrintFlagsFinal, so verify via
        // _JAVA_LAUNCHER_DEBUG=1 that the heuristic did not add the option.
        for (String opt : List.of("-J-XX:AOTCache=nosuch.aot",
                                  "-J-XX:AOTCacheOutput=out.aot",
                                  "-J-XX:AOTConfiguration=out.aotconfig")) {
            OutputAnalyzer oa = runJavac(
                    Map.of("_JAVA_LAUNCHER_DEBUG", "1"),
                    List.of(opt, src.toString()));
            oa.shouldNotContain("javac heuristic: added -XX:TieredStopAtLevel=1");
            oa.shouldContain("javac heuristic: sources=0 bytes=0 bytes_limit=2097152 rc=-1");
        }
    }

    // Decline options whose compile may fail with Hello.java; the VM flag is
    // still visible in the output.
    static Stream<List<String>> declineOptionsWithFailingCompile() {
        return Stream.of(
                List.of("-processor", "com.example.NoSuch"),
                List.of("-m", "no.such.module"),
                List.of("--module", "no.such.module"),
                List.of("--module-source-path=."),
                List.of("-Xplugin:NoSuch"));
    }

    @ParameterizedTest
    @MethodSource("declineOptionsWithFailingCompile")
    void declineOptionsWithFailingCompile(List<String> option) throws Exception {
        assertNotLevel1AnyExit(Map.of(),
                               concat(option, src.toString()),
                               "with " + option);
    }

    @Test
    void procNoneDoesNotDecline() throws Exception {
        // -proc:none disables annotation processing.
        assertTieredStop(1, Map.of(), List.of("-proc:none", src.toString()));
    }

    @Test
    void declineOptionInArgfile() throws Exception {
        Path declineList = dir.resolve("decline.list");
        Files.writeString(declineList,
                          "-sourcepath .\n" + argfileToken(src) + "\n",
                          StandardCharsets.UTF_8);
        assertNotLevel1(Map.of(),
                        List.of("@" + declineList),
                        "when an @argfile has -sourcepath");
    }

    @Test
    void argfileSyntax() throws Exception {
        // Comment lines, and a quoted path containing a space.
        Path spaceDir = dir.resolve("dir with space");
        Files.createDirectories(spaceDir);
        Path spaced = spaceDir.resolve("Spaced.java");
        Files.writeString(spaced, "class Spaced {}\n", StandardCharsets.UTF_8);
        Path syntaxList = dir.resolve("syntax.list");
        Files.writeString(syntaxList,
                          "# a comment line\n" + argfileToken(spaced) + "\n",
                          StandardCharsets.UTF_8);
        assertTieredStop(1, Map.of(), List.of("@" + syntaxList));
    }

    @Test
    void unreadableArgfile() throws Exception {
        // An argument longer than the argfile buffer declines instead of using a prefix.
        Path longArgs = dir.resolve("long.list");
        Files.writeString(longArgs, "a".repeat(5000) + ".java\n", StandardCharsets.UTF_8);
        assertNotLevel1AnyExit(Map.of(),
                               List.of("@" + longArgs),
                               "for an oversized @argfile argument");

        // A directory opened as an @argfile is a read error, not an empty file.
        Path argDir = dir.resolve("argdir");
        Files.createDirectories(argDir);
        assertNotLevel1AnyExit(Map.of(),
                               List.of("@" + argDir),
                               "when @argfile cannot be read");
    }

    // .java arguments that cannot be opened
    @Test
    void missingJavaFile() throws Exception {
        // Including an option operand. The trailing -d . from runJavac still compiles.
        assertNotLevel1(Map.of(),
                        List.of("-d", "missing.java", src.toString()),
                        "when a .java operand cannot be opened");
        assertNotLevel1(Map.of(),
                        List.of("-classpath", "no-such.java", src.toString()),
                        "when -classpath names a missing .java");

        Path missingList = dir.resolve("missing.list");
        Files.writeString(missingList,
                          "-d\nmissing.java\n" + argfileToken(src) + "\n",
                          StandardCharsets.UTF_8);
        assertNotLevel1(Map.of(),
                        List.of("@" + missingList),
                        "when an @argfile .java cannot be opened");
    }

    // Helpers

    private static OutputAnalyzer runJavac(Map<String, String> env,
                                           List<String> javacArgs) throws Exception {
        String javac = Path.of(System.getProperty("test.jdk"), "bin", "javac").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(javac);
        cmd.add("-J-XX:+PrintFlagsFinal");
        cmd.addAll(javacArgs);
        cmd.add("-d");
        cmd.add(".");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> pbEnv = pb.environment();
        pbEnv.remove("JDK_JAVAC_OPTIONS");
        pbEnv.remove("JDK_JAVA_OPTIONS");
        // These reach the VM. _JAVA_OPTIONS is applied after the launcher
        // options and would override the level this test asserts.
        pbEnv.remove("JAVA_TOOL_OPTIONS");
        pbEnv.remove("_JAVA_OPTIONS");
        pbEnv.putAll(env);
        return ProcessTools.executeProcess(pb);
    }

    /* Run javac, which must exit with 0, and check that TieredStopAtLevel is expected. */
    private static void assertTieredStop(int expected, Map<String, String> env,
                                         List<String> javacArgs) throws Exception {
        OutputAnalyzer oa = runJavac(env, javacArgs);
        oa.shouldHaveExitValue(0);
        int value = parseTieredStop(oa.getOutput());
        if (value != expected) {
            throw new AssertionError("TieredStopAtLevel=" + value +
                    " expected " + expected + "\n" + oa.getOutput());
        }
    }

    /*
     * Run javac, which must exit with 0, and check that TieredStopAtLevel is not 1.
     * 'when' describes the case, for the failure message.
     */
    private static void assertNotLevel1(Map<String, String> env,
                                        List<String> javacArgs,
                                        String when) throws Exception {
        OutputAnalyzer oa = runJavac(env, javacArgs);
        oa.shouldHaveExitValue(0);
        checkNotLevel1(oa, when);
    }

    /* Like assertNotLevel1, but the compile itself may fail. */
    private static void assertNotLevel1AnyExit(Map<String, String> env,
                                               List<String> javacArgs,
                                               String when) throws Exception {
        checkNotLevel1(runJavac(env, javacArgs), when);
    }

    private static void checkNotLevel1(OutputAnalyzer oa, String when) {
        if (parseTieredStop(oa.getOutput()) == 1) {
            throw new AssertionError(
                    "expected TieredStopAtLevel != 1 " + when + "\n" + oa.getOutput());
        }
    }

    private static void writePaddedJava(Path path, String className, long totalBytes)
            throws Exception {
        String prefix = "public class " + className + " { /* ";
        String suffix = " */ }\n";
        int pad = Math.toIntExact(totalBytes - prefix.length() - suffix.length());
        Files.writeString(path, prefix + "x".repeat(pad) + suffix, StandardCharsets.UTF_8);
        assertEquals(totalBytes, Files.size(path));
    }

    private static List<String> concat(List<String> head, String last) {
        List<String> all = new ArrayList<>(head);
        all.add(last);
        return all;
    }

    /* Quoted so spaces survive; javac decodes backslashes inside quotes. */
    private static String argfileToken(Path p) {
        return "\"" + p.toString().replace('\\', '/') + "\"";
    }

    private static int parseTieredStop(String output) {
        Matcher m = TIERED_STOP.matcher(output);
        if (!m.find()) {
            throw new AssertionError("TieredStopAtLevel not found in:\n" + output);
        }
        return Integer.parseInt(m.group(1));
    }
}
