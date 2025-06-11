/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import static java.util.stream.Collectors.*;

// This program is executed by make/RunTests.gmk to support running HotSpot tests
// in the "AOT mode", for example:
//
//     make test JTREG=AOT_JDK=onestep TEST=open/test/hotspot/jtreg/runtime/invokedynamic
//     make test JTREG=AOT_JDK=twostep TEST=open/test/hotspot/jtreg/runtime/invokedynamic
//
// The onestep and twostep options specify whether the AOT cache is created with
// a single JVM command (java -XX:AOTMode=record -XX:AOTCacheOutput=jdk.aotcache ...) or
// two JVM commands (java -XX:AOTMode=record ...; java -XX:AOTMode=create -XX:AOTCache=jdk.aotcache ...)
//
// All JDK classes touched by this program will be stored into a customized AOT cache.
// This is a larger set of classes than those stored in the JDK's default CDS archive.
// This customized cache can also have additional optimizations that are not
// enabled in the default CDS archive. For example, AOT-linked classes and lambda
// expressions. In the future, it can also contain AOT profiles and AOT compiled methods.
//
// We can use this customized AOT cache to run various HotSpot tests to improve
// coverage on AOT.
//
// Note that make/RunTests.gmk loads this class using an implicit classpath of ".", so
// this class will be excluded from the customized AOT cache. As a result,
// the customized AOT cache contains *only* classes from the JDK itself.

public class TestSetupAOT {
    private static final Logger LOGGER = Logger.getLogger("Hello");

    public static void main(String[] args) throws Throwable {
        runJDKTools(args);
        invokedynamicTests(args);
        LOGGER.log(Level.FINE, "Done");
    }

    static class ToolOutput {
        ByteArrayOutputStream baos;
        PrintStream ps;
        String output;

        ToolOutput() throws Exception {
            baos = new ByteArrayOutputStream();
            ps = new PrintStream(baos, true, StandardCharsets.UTF_8.name());
        }
        void finish() throws Exception {
            output = baos.toString(StandardCharsets.UTF_8.name());
            System.out.println(output);
        }

        ToolOutput shouldContain(String... substrings) {
            for (String s : substrings) {
                if (!output.contains(s)) {
                    throw new RuntimeException("\"" + s + "\" missing from tool output");
                }
            }

            return this;
        }

        ToolOutput shouldMatch(String... regexps) {
            for (String regexp : regexps) {
                Pattern pattern = Pattern.compile(regexp, Pattern.MULTILINE);
                if (!pattern.matcher(output).find()) {
                    throw new RuntimeException("Pattern \"" + regexp + "\" missing from tool output");
                }
            }

            return this;
        }
    }

    static void runJDKTools(String[] args) throws Throwable {
        String tmpDir = args[0];
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        System.out.println("Temp output dir = " + tmpDir);

        // ------------------------------
        // javac

        execTool("javac", "--help")
            .shouldContain("Usage: javac <options> <source files>");

        JavacBenchApp.main(new String[] {"5"});

        // ------------------------------
        // javap

        execTool("javap", "--help")
            .shouldContain("Show package/protected/public classes");
        execTool("javap", "-c", "-private", "-v", "-verify",
                 "java.lang.System",
                 "java/util/stream/IntStream",
                 "jdk.internal.module.ModuleBootstrap")
            .shouldContain("Compiled from \"System.java\"",
                           "public static java.io.Console console()");

        // ------------------------------
        // jlink

        String jlinkOutput = tmpDir + File.separator + "jlinkOutput";

        execTool("jlink", "--help")
            .shouldContain("Compression to use in compressing resources");
        execTool("jlink", "--list-plugins")
            .shouldContain("List of available plugins",
                           "--generate-cds-archive ");

        deleteAll(jlinkOutput);
        execTool("jlink", "--add-modules", "java.base", "--strip-debug", "--output", jlinkOutput);
        deleteAll(jlinkOutput);

        // ------------------------------
        // jar

        String jarOutput = tmpDir + File.separator + "tmp.jar";

        execTool("jar", "--help")
            .shouldContain("--main-class=CLASSNAME");

        deleteAll(jarOutput);
        execTool("jar", "cvf", jarOutput, "TestSetupAOT.class")
            .shouldContain("adding: TestSetupAOT.class");
        execTool("jar", "uvf", jarOutput, "TestSetupAOT.class")
            .shouldContain("adding: TestSetupAOT.class");
        execTool("jar", "tvf", jarOutput)
            .shouldContain("META-INF/MANIFEST.MF");
        execTool("jar", "--describe-module", "--file=" + jarOutput)
            .shouldMatch("Unable to derive module descriptor for: .*tmp.jar");
        deleteAll(jarOutput);

        // ------------------------------
        // jdeps

        execTool("jdeps", "--help")
            .shouldContain("--ignore-missing-deps");
        execTool("jdeps", "-v", "TestSetupAOT.class")
            .shouldContain("-> JavacBenchApp");
    }

    static void deleteAll(String f) {
        deleteAll(new File(f));
    }

    static void deleteAll(File f) {
        File[] files = f.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteAll(file);
            }
        }
        System.out.println("Deleting: " + f);
        f.delete();
    }

    static ToolOutput execTool(String tool, String... args) throws Throwable {
        System.out.println("== Running tool ======================================================");
        System.out.print(tool);
        for (String s : args) {
            System.out.print(" " + s);
        }
        System.out.println();
        System.out.println("======================================================================");

        ToolOutput output = new ToolOutput();
        ToolProvider t = ToolProvider.findFirst(tool)
            .orElseThrow(() -> new RuntimeException(tool + " not found"));
        t.run(output.ps, output.ps, args);

        output.finish();
        return output;
    }


    // Run some operations with java.util.stream, lambda expressions and string concatenation. This
    // will lead to AOT resolution of invokedynamic call sites.
    static void invokedynamicTests(String args[]) {
        List<String> strings = Arrays.asList("Hello", "World!");

        String helloWorld = strings.parallelStream()
                .filter(s -> s.contains("o"))
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(joining(","));

        Stream.of(helloWorld.split("([,x-z]{1,3})([\\s]*)"))
                .map(String::toString)
                .forEach(System.out::println);

        // Common concatenation patterns
        int i = args.length * 12357; // Seed with this so javac will not perform constant folding.
        String s = String.valueOf(i);

        String SS     = s + s;
        String CS     = "string" + s;
        String SC     = s + "string";
        String SCS    = s + "string" + s;
        String CSS    = "string" + s + s;
        String CSC    = "string" + s + "string";
        String SSC    = s + s + "string";
        String CSCS   = "string" + s + "string" + s;
        String SCSC   = s + "string" + s + "string";
        String CSCSC  = "string" + s + "string" + s + "string";
        String SCSCS  = s + "string" + s + "string" + s;
        String SSCSS  = s + s + "string" + s + s;
        String S5     = s + s + s + s + s;
        String S6     = s + s + s + s + s + s;
        String S7     = s + s + s + s + s + s + s;
        String S8     = s + s + s + s + s + s + s + s;
        String S9     = s + s + s + s + s + s + s + s + s;
        String S10    = s + s + s + s + s + s + s + s + s + s;

        String CI     = "string" + i;
        String IC     = i + "string";
        String SI     = s + i;
        String IS     = i + s;
        String CIS    = "string" + i + s;
        String CSCI   = "string" + s + "string" + i;
        String CIC    = "string" + i + "string";
        String CICI   = "string" + i + "string" + i;

        float f = 0.1f;
        String CF     = "string" + f;
        String CFS    = "string" + f + s;
        String CSCF   = "string" + s + "string" + f;

        char c = 'a';
        String CC     = "string" + c;
        String CCS    = "string" + c + s;
        String CSCC   = "string" + s + "string" + c;

        long l = i + 12345678;
        String CJ     = "string" + l;
        String JC     = l + "string";
        String CJC    = "string" + l + "string";
        String CJCJ   = "string" + l + "string" + l;
        String CJCJC  = "string" + l + "string" + l + "string";
        double d = i / 2.0;
        String CD     = "string" + d;
        String CDS    = "string" + d + s;
        String CSCD   = "string" + s + "string" + d;
    }
}
