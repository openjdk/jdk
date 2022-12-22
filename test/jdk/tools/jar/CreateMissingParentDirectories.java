/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8281104
 * @modules jdk.jartool
 * @summary jar --create --file a/b/test.jar should create directories a/b
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class CreateMissingParentDirectories {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    /**
     * Remove dirs & files needed for test.
     */
    private static void cleanup(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    s.forEach(p -> cleanup(p));
                }
            }
            Files.delete(dir);
        } catch (Exception x) {
            fail(x.toString());
        }
    }

    public static void realMain(String[] args) throws Throwable {
        Path topDir = Files.createTempDirectory("delete");
        try {
            Path entry = Files.writeString(topDir.resolve("test.txt"), "Some text...");

            doHappyPathTest(topDir.resolve("test.jar"), entry);
            doHappyPathTest(topDir.resolve("a/test.jar"), entry);
            doHappyPathTest(topDir.resolve("a/b/test.jar"), entry);

            Path blocker = Files.writeString(topDir.resolve("blocker.txt"), "Blocked!");
            doFailingTest(topDir.resolve("blocker.txt/test.jar").toString(), entry);
        } finally {
            cleanup(topDir);
        }
    }

    private static void doHappyPathTest(Path jar, Path entry) throws Throwable {
        String[] jarArgs = new String[]{"cf", jar.toString(), entry.toString()};
        if (JAR_TOOL.run(System.out, System.err, jarArgs) != 0) {
            fail("Could not create jar file: " + List.of(jarArgs));
            return;
        }
        jarArgs = new String[]{"--create", "--file", jar.toString(), entry.toString()};
        if (JAR_TOOL.run(System.out, System.err, jarArgs) != 0) {
            fail("Could not create jar file: " + List.of(jarArgs));
            return;
        }
        pass();
    }

    private static void doFailingTest(String jar, Path entry) throws Throwable {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        String[] jarArgs = new String[]{"cf", jar, entry.toString()};

        if (JAR_TOOL.run(new PrintWriter(out, true), new PrintWriter(err, true), jarArgs) == 0) {
            fail("Should have failed creating jar file: " + jar);
            return;
        }
        pass();
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
