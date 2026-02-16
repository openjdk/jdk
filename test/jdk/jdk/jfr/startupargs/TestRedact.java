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

package jdk.jfr.startupargs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.CommonHelper;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Test that redaction of sensitive data works
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @build jdk.jfr.startupargs.Application
 * @run main jdk.jfr.startupargs.TestRedact
 */
public class TestRedact {
    private static Path TEST_DIRECTORY = Path.of(System.getProperty("test.src", ".")).toAbsolutePath();
    private static Path FILE_REDACTED_ARGUMENTS = TEST_DIRECTORY.resolve("redacted-arguments.txt");
    private static Path FILE_REDACTED_KEYS = TEST_DIRECTORY.resolve("redacted-keys.txt");

    private record Execution(
       Path file,
       String jvmArgs,
       String jvmFlags,
       String javaArgs,
       Map<String, String> environment,
       Map<String, String> systemProperties,
       Map<String, String> stringFlags
    ) {

        void assertUnredacted(String text) throws Exception {
            if (jvmArgs != null && jvmArgs.contains(text)) {
                return;
            }
            if (javaArgs != null && javaArgs.contains(text)) {
                return;
            }
            if (systemProperties.containsValue(text) || environment.containsValue(text)) {
                return;
            }
            printSystemProperties();
            printEnvironment();
            printCommandLine();
            throw new Exception("Could not find text '" + text + "'. Likely it's been incorrectly redacted.");
        }

       private void printCommandLine() {
           System.out.println("JVM Args: " + jvmArgs);
           System.out.println("JVM Flags: " + jvmFlags);
           System.out.println("Java Args: " + javaArgs);
           System.out.println();
       }

       void assertRedactedKey(String key) throws Exception {
           String v = systemProperties.get(key);
           if (v != null) {
               printSystemProperties();
               if (!v.equals("[REDACTED]")) {
                   throw new Exception("Expected system property value of key '" + key + "' to be [REDACTED]");
               }
               return;
           }
           v = environment.get(key);
           if (v == null) {
               printEnvironment();
               printSystemProperties();
               throw new Exception("Expected key '" + key + "' in environment variable or system property");
           }
           if (!v.equals("[REDACTED]")) {
               printEnvironment();
               throw new Exception("Expected environment value of key '" + key + "' to be [REDACTED]");
           }
       }

       void printEnvironment() {
           printProperties("Environment Variables", environment);
       }

       void printStringFlags() {
           printProperties("String Flags", stringFlags);
       }

       void printSystemProperties() {
           printProperties("System Properties", systemProperties);
       }

       private void printProperties(String title, Map<String, String> map) {
           System.out.println(title);
           System.out.println("=".repeat(title.length()));
           for (var entry : map.entrySet()) {
               System.out.println(entry.getKey() + " = " + entry.getValue());
           }
           System.out.println();
       }

        void assertRedactedArgument(String argument) throws Exception {
            checkArgument("JVM arguments", jvmArgs, argument);
            checkArgument("Java arguments", javaArgs, argument);
            checkArgument("Flags", jvmFlags, argument);
            checkArgument("sun.java.command", systemProperties.get("sun.java.command"), argument);
        }

        private void checkArgument(String kind, String arguments, String argument) throws Exception {
            if (arguments != null && arguments.contains(argument)) {
                System.out.println("ARGS: " + arguments);
                throw new Exception("Found '" + argument + "' in " + kind + ". Should have been [REDACTED]");
            }
        }

        public void print() {
            printCommandLine();
            printEnvironment();
            printSystemProperties();
            printStringFlags();
        }
    }

    public static void main(String... args) throws Exception {
        testRedactionOfRedacted();
        testRedactKey();
        testRedactArgument();
        testRedactMultiple();
        testWildcards();
        testDefaults();
        testRedactFile();
        testAppendable();
        testComplex();
        testBinary();
    }

    private static void testRedactionOfRedacted() throws Exception {
        Execution e = run(
            "-XX:FlightRecorderOptions:" +
            "maxchunksize=1MB,redact-argument=zebra," +
            "old-object-queue-size=256," +
            "redact-key=tiger",
            "zebra",
            "tiger"
        );
        e.assertRedactedArgument("zebra");
        e.assertRedactedArgument("redact-argument=zebra");
        e.assertUnredacted("tiger");
    }

    private static void testAppendable() throws Exception {
        Execution e = run(
             Map.of(
                "secret", "thing",  // For default filter
                "cat", "bird"       // For user-defined filter
            ),
            Map.of(
                "secret", "stuff",  // For default filter
                "cat", "dog"        // For user-defined filter
            ),
            "-XX:FlightRecorderOptions:redact-argument=+foo;bar,redact-key=+cat",
            "foo",                  // For user-defined
            "bar",                  // For user-defined
            "secret");              // For default filter
        e.assertRedactedArgument("foo");    // Matched by user-defined filter
        e.assertRedactedArgument("bar");    // Matched by user-defined filter
        e.assertRedactedKey("cat");         // Matched by user-defined filter
        e.assertRedactedArgument("secret"); // Matched by default filter *secret*
        e.assertRedactedKey("secret");      // Matched by default filter *secret*
    }

    private static void testWildcards() throws Exception {
        Execution e = run(
           "-XX:FlightRecorderOptions:redact-argument=*pple;oran*;c*he*ry;?2?4?;a?c*?f",
           "apple", "orange", "cherry", "12345", "abcdef", "4711"
        );
        e.assertRedactedArgument("apple");
        e.assertRedactedArgument("orange");
        e.assertRedactedArgument("cherry");
        e.assertRedactedArgument("12345");
        e.assertRedactedArgument("abcdef");
        e.assertUnredacted("4711");
    }

    private static void testComplex() throws Exception {
        // ignore
    }

    private static void testBinary() throws Exception {
        Execution unredacted = run(
            Map.of("stuff1", "hammock"),
            Map.of("stuff2", "haberdashery"),
            "-XX:FlightRecorderOptions:stackdepth=64",
            "hammock"
        );
        if (!contains(unredacted.file, "hammock")) {
            throw new Exception("Expected 'hammock' to be in recording file");
        }
        if (!contains(unredacted.file, "haberdashery")) {
            throw new Exception("Expected 'haberdashery' to be in recording file");
        }

        Execution redacted = run(
            Map.of("what", "zippers"),
            Map.of("where", "haberdashery"),
            "-XX:FlightRecorderOptions:redact-argument=zippers,redact-key=what;where",
            "zippers"
        );
        if (contains(redacted.file, "zippers")) {
            redacted.print();
            throw new Exception("Expected all occurrences of 'zippers' to be redacted");
        }
        if (contains(redacted.file, "haberdashery")) {
            redacted.print();
            throw new Exception("Expected all occurrences of 'haberdashery' to be redacted");
        }
    }

    private static boolean contains(Path file, String text) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        byte[] chars = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - chars.length; i++) {
            if (matchesAt(bytes, chars, i)) {
                System.out.print("Found '" + text + "' at position " + i);
                System.out.println(" in file " + file.toAbsolutePath());
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(byte[] bytes, byte[] chars, int position) {
        for (int j = 0; j < chars.length; j++) {
            if (bytes[position + j] != chars[j]) {
                return false;
            }
        }
        return true;
    }

    private static void testRedactFile() throws Exception {
        Execution e1 = run(
            Map.of("stuff1", "snake"),
            Map.of("stuff2", "snake"),
            "-XX:FlightRecorderOptions:redact-argument=@" + FILE_REDACTED_ARGUMENTS,
            "https://john:smith@www.example.com/myresource",
            "-conf-key=chicken",
            "/foo/dog",
            "-header", "Authorization:Bearer", "banana",
            "apple");
        e1.assertRedactedArgument("https://john:smith@www.example.com/myresource");
        e1.assertRedactedArgument("conf-key=chicken");
        e1.assertRedactedArgument("/foo/dog");
        e1.assertRedactedArgument("-header");
        e1.assertRedactedArgument("Authorization:Bearer");
        e1.assertRedactedArgument("banana");
        e1.assertRedactedArgument("apple");
        e1.assertUnredacted("snake");

        Execution e2 = run(
            Map.of("confidential", "apple"),
            Map.of("very-sensitive", "banana"),
            "-XX:FlightRecorderOptions:redact-key=@" + FILE_REDACTED_KEYS,
            "snake");
        e2.assertRedactedKey("confidential");
        e2.assertRedactedKey("very-sensitive");
        e2.assertUnredacted("snake");
    }

    private static void testDefaults() throws Exception {
        Execution e = run(
           Map.of("apiKey","thing"),
           Map.of("apiKey", "stuff"),
           "-DapiKey=stuff",
           "secret"
        );
        e.assertRedactedArgument("secret");
        e.assertRedactedKey("apiKey");
    }

    private static void testRedactArgument() throws Exception {
        Execution e = run(
            "-XX:FlightRecorderOptions:redact-argument=" +
            // FILTERS
            "plain;" +
            "--option<DELIMITER>*;" +
            "--pass-phrase<DELIMITER>*;" +
            "--login<DELIMITER>*<DELIMITER>*;" +
            "--colon-based:*;" +
            "https://*:*@*",
            // SENSITIVE INFORMATION
            "plain",
            "--option", "option-value",
            "--pass-phrase", "cant-be-whitespace-outage",
            "--login", "john", "4711",
            "--colon-based:hello",
            "https://smith:abc123@example.com/path",
            // UNSENSITIVE INFORMATION
            "banana"
        );
        e.assertRedactedArgument("plain");
        e.assertRedactedArgument("option-value");
        e.assertRedactedArgument("cant-be-whitespace-outage");
        e.assertRedactedArgument("hello");
        e.assertRedactedArgument("john");
        e.assertRedactedArgument("4711");
        e.assertRedactedArgument("smith:abc123");
        e.assertUnredacted("banana");
    }

    private static void testRedactMultiple() throws Exception {
        Execution e = run(
                Map.of("foo", "bird", "bar, ", "orange"),
                Map.of("foo", "tiger", "bar", "banana"),
                "-XX:FlightRecorderOptions:redact-key=foo;bar,redact-argument=baz;quz", "baz", "quz");
        e.assertRedactedKey("foo");
        e.assertRedactedKey("bar");
        e.assertRedactedArgument("baz");
        e.assertRedactedArgument("quz");
    }

    private static void testRedactKey() throws Exception {
        Execution e = run(
            Map.of("cart", "wheel", "banana", "split", "rose", "bud"),
            Map.of("banana", "split", "cat", "milk", "carpet","magic", "rose", "bud"),
            "-XX:FlightRecorderOptions:redact-key=banana;ca*t",
            "rose"
        );
        e.assertRedactedKey("banana");
        e.assertRedactedKey("cat");
        e.assertRedactedKey("carpet");
        e.assertUnredacted("rose");
    }

    private static Execution run(String options, String... args) throws Exception {
        return run(Map.of(), Map.of(), options, args);
    }

    private static Execution run(Map<String, String> environment, Map<String, String> properties, String options, String... args) throws Exception {
        List<String> arguments = new ArrayList<>();
        Path file = Path.of("file.jfr");
        for (var entry : properties.entrySet()) {
            arguments.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        arguments.add("-XX:StartFlightRecording:filename=" + file.toAbsolutePath().toString());
        arguments.add(options);
        arguments.add("jdk.jfr.startupargs.Application");
        arguments.addAll(Arrays.asList(args));

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(arguments);
        pb.environment().put("env.secret", "confidential");
        // An environment variable redacted by default filters
        pb.environment().putAll(environment);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getOutput());
        output.shouldHaveExitValue(0);
        var environmentVariables = new HashMap<String, String>();
        var systemProperties = new HashMap<String, String>();
        var stringFlags = new HashMap<String, String>();
        var jvmArgs = new AtomicReference<String>();
        var jvmFlags= new AtomicReference<String>();
        var javaArgs = new AtomicReference<String>();
        try (var es = EventStream.openFile(file)) {
            es.onEvent("jdk.InitialSystemProperty", e -> {
                systemProperties.put(e.getString("key"), e.getString("value"));
            });
            es.onEvent("jdk.InitialEnvironmentVariable", e -> {
                environmentVariables.put(e.getString("key"), e.getString("value"));
            });
            es.onEvent("jdk.JVMInformation", e -> {
                jvmArgs.set(e.getString("jvmArguments"));
                javaArgs.set(e.getString("javaArguments"));
                jvmFlags.set(e.getString("jvmFlags"));
            });
            es.onEvent("jdk.StringFlag", e -> {
                stringFlags.put(e.getString("name"), e.getString("value"));
            });
            es.start();
        }
        return new Execution(file,
          jvmArgs.get(), jvmFlags.get(), javaArgs.get(),
          environmentVariables, systemProperties, stringFlags);
    }
}
