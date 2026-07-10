/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.tracing;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.jfr.Recording;
import java.util.spi.ToolProvider;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests applying filters to methods in both exported and unexported packages
 *          of a named module.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.tracing.TestWithModules
 **/
public class TestWithModules {

    /** Directory structure:
         |-src
           |-application
           | |-Main.java
           |-module
             |-module-info.java
             |-test
               |-exported
               | |- Exported.java
               |-unexported
                 |- UnExported.java
    **/
    private static String MODULE_INFO =
    """
    module test.exported {
       exports test.exported;
    }
    """;

    private static String EXPORTED_CLASS =
    """
    package test.exported;

    import test.unexported.Unexported;

    public class Exported {
       public static void run() {
          System.out.println("Exported::run executed!");
          Unexported.run();
       }
    }
    """;

    private static String UNEXPORTED_CLASS =
    """
    package test.unexported;

    public class Unexported {
       public static void run() {
          System.out.println("Unexported::run executed!");
       }
    }
    """;

    private static String MAIN_CLASS =
    """
    import test.exported.Exported;
    import jdk.jfr.Recording;
    import java.nio.file.Path;

    public class Main {
       public static void main(String... args) throws Exception {
          Path file = Path.of(args[0]);
          boolean before = args[1].equals("run-before");
          System.out.println("Main before=" + before);
          try(Recording r = new Recording()) {
            if (before) {
              // Load class before JFR starts
              Exported.run();
            }
            r.enable("jdk.MethodTrace").with("filter", "test.unexported.Unexported::run");
            r.enable("jdk.MethodTiming").with("filter", "test.unexported.Unexported::run").with("period", "endChunk");
            r.start();
            System.out.println("About to run with instrumented");
            Exported.run();
            r.stop();
            r.dump(file);
            System.out.println("Dump written " + file);
          }
       }
    }
    """;

    public static void main(String... args) throws Exception {
        Path src = Path.of("src").toAbsolutePath();
        Path modulePath = materializeModule(src);
        Path mainFile = materializeMain(src);
        Path output = Files.createDirectory(Path.of("output").toAbsolutePath());
        List<Path> srcFiles = Files.walk(modulePath).filter(Files::isRegularFile).toList();
        List<String> arguments = new ArrayList<>();
        arguments.add("-d");
        arguments.add(output.toString());
        for (Path p : srcFiles) {
            arguments.add(p.toAbsolutePath().toString());
        }
        if (!compile(arguments)) {
            throw new Exception("Could not compile classes");
        }
        testClassloadBefore(mainFile, output);
        testClassloadDuring(mainFile, output);
    }

    private static Path materializeMain(Path src) throws IOException {
        Path srcApplication = Files.createDirectories(src.resolve("application"));
        Path mainFile = srcApplication.resolve("Main.java");
        Files.writeString(mainFile, MAIN_CLASS);
        return mainFile;
    }

    private static void testClassloadBefore(Path mainFile, Path modulePath) throws Exception {
        Path file = Path.of("before.jfr").toAbsolutePath();
        execute(file, mainFile, modulePath, true);
        verifyRecording("already loaded class", file);
    }

    private static void testClassloadDuring(Path mainFile, Path modulePath) throws Exception {
        Path file = Path.of("during.jfr").toAbsolutePath();
        execute(file, mainFile, modulePath, false);
        verifyRecording("loading of class", file);
    }

    private static void verifyRecording(String title, Path file) throws Exception {
        List<RecordedEvent> traceEvents = new ArrayList<>();
        List<RecordedEvent> timingEvents = new ArrayList<>();
        System.out.println("********* Verifying " + title + " ********");
        try (EventStream s = EventStream.openFile(file)) {
            s.setReuse(false);
            s.onEvent("jdk.MethodTrace", traceEvents::add);
            s.onEvent("jdk.MethodTiming", timingEvents::add);
            s.onEvent(System.out::println);
            s.start();
        }
        assertMethod(traceEvents, "test.unexported.Unexported", "run");
        assertMethod(timingEvents, "test.unexported.Unexported", "run");
        assertMethodTimingCount(timingEvents.get(0), 1);
    }

    private static void assertMethodTimingCount(RecordedEvent event, int expected) throws Exception {
        long invocations = event.getLong("invocations");
        if (invocations != expected) {
            throw new Exception("Expected invocations to be " + expected + ", but was " + invocations);
        }
    }

    private static void assertMethod(List<RecordedEvent> events, String className, String methodName) throws Exception {
        for (RecordedEvent event : events) {
            RecordedMethod method = event.getValue("method");
            if (method.getName().equals(methodName) && method.getType().getName().equals(className)) {
                return;
            }
        }
        throw new Exception("Expected method named " + className + "::" + methodName);
    }

    private static void execute(Path jfrFile, Path mainFile, Path modulePath, boolean before) throws Exception {
        String[] c = new String[7];
        c[0] = "--module-path";
        c[1] = modulePath.toString();
        c[2] = "--add-modules";
        c[3] = "test.exported";
        c[4] = mainFile.toString();
        c[5] = jfrFile.toString();
        c[6] = before ? "run-before" : "not-run-before";
        OutputAnalyzer oa = ProcessTools.executeTestJava(c);
        oa.waitFor();
        oa.shouldHaveExitValue(0);
    }

    private static Path materializeModule(Path src) throws IOException {
        Path srcModule = Files.createDirectories(src.resolve("module"));
        Path moduleFile = srcModule.resolve("module-info.java");
        Files.writeString(moduleFile, MODULE_INFO);

        Path exported = Files.createDirectories(srcModule.resolve("test").resolve("exported"));
        Path exportedJava = exported.resolve("Exported.java");
        Files.writeString(exportedJava, EXPORTED_CLASS);

        Path unexported = Files.createDirectories(srcModule.resolve("test").resolve("unexported"));
        Path unexportedJava = unexported.resolve("Unexported.java");
        Files.writeString(unexportedJava, UNEXPORTED_CLASS);

        return srcModule;
    }

    private static boolean compile(List<String> arguments) {
        Optional<ToolProvider> tp = ToolProvider.findFirst("javac");
        if (tp.isEmpty()) {
            return false;
        }
        var tool = tp.get();
        String[] options = arguments.toArray(String[]::new);
        int ret = tool.run(System.out, System.err, options);
        return ret == 0;
    }
}
