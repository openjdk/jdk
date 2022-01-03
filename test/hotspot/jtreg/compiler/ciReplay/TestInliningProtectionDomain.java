/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8275868
 * @library / /test/lib
 * @summary Testing that ciReplay inlining does not fail with unresolved signature classes.
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestInliningProtectionDomain
 */

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestInliningProtectionDomain extends DumpReplayBase {
    public static final String LOG_FILE_NORMAL = "hotspot_normal.log";
    public static final String LOG_FILE_REPLAY = "hotspot_replay.log";
    private final String[] commandLineReplay;

    private final String className;

    public static void main(String[] args) {
        new TestInliningProtectionDomain("ProtectionDomainTestCompiledBefore", true);
        new TestInliningProtectionDomain("ProtectionDomainTestNoOtherCompilationPublic", false);
        new TestInliningProtectionDomain("ProtectionDomainTestNoOtherCompilationPrivate", false);
        new TestInliningProtectionDomain("ProtectionDomainTestNoOtherCompilationPrivateString", false);
    }

    public TestInliningProtectionDomain(String className, boolean compileBar) {
        this.className = className;
        List<String> commandLineNormal = new ArrayList<>(List.of("-XX:LogFile=" + LOG_FILE_NORMAL + "", "-XX:+LogCompilation", "-XX:-TieredCompilation",
                                                           "-XX:CompileCommand=exclude," + getTestClass() + "::main",
                                                           "-XX:CompileCommand=option," + getTestClass()  + "::test,bool,PrintInlining,true"));
        if (compileBar) {
            commandLineNormal.add("-XX:CompileCommand=compileonly," + getTestClass() + "::bar");
        }
        commandLineReplay = new String[]
                {"-XX:LogFile=" + LOG_FILE_REPLAY + "", "-XX:+LogCompilation",
                 "-XX:CompileCommand=option," + getTestClass()  + "::test,bool,PrintInlining,true"};
        runTest(commandLineNormal.toArray(new String[0]));
    }

    @Override
    public void testAction() {
        positiveTest(commandLineReplay);
        String klass = "compiler.ciReplay." + className;
        String entryString = klass + " " + "test";
        boolean inlineFails = className.equals("ProtectionDomainTestNoOtherCompilationPrivate");
        int inlineeCount = inlineFails ? 1 : 5;

        List<Entry> inlineesNormal = parseLogFile(LOG_FILE_NORMAL, entryString, "compile_id='" + getCompileIdFromFile(getReplayFileName()), inlineeCount);
        List<Entry> inlineesReplay = parseLogFile(LOG_FILE_REPLAY, entryString, "test ()V", inlineeCount);
        verifyLists(inlineesNormal, inlineesReplay, inlineeCount);

        if (inlineFails) {
            Asserts.assertTrue(compare(inlineesNormal.get(0), "compiler.ciReplay.ProtectionDomainTestNoOtherCompilationPrivate",
                                       "bar", inlineesNormal.get(0).isUnloadedSignatureClasses()));
            Asserts.assertTrue(compare(inlineesReplay.get(0), "compiler.ciReplay.ProtectionDomainTestNoOtherCompilationPrivate",
                                       "bar", inlineesReplay.get(0).isDisallowedByReplay()));
        } else {
            Asserts.assertTrue(compare(inlineesNormal.get(4), "compiler.ciReplay.InliningBar", "bar2", inlineesNormal.get(4).isNormalInline()));
            Asserts.assertTrue(compare(inlineesReplay.get(4), "compiler.ciReplay.InliningBar", "bar2", inlineesReplay.get(4).isForcedByReplay()));
        }
        remove(LOG_FILE_NORMAL);
        remove(LOG_FILE_REPLAY);
    }

    private void verifyLists(List<Entry> inlineesNormal, List<Entry> inlineesReplay, int expectedSize) {
        if (!inlineesNormal.equals(inlineesReplay)) {
            System.err.println("Normal entries:");
            inlineesNormal.forEach(System.err::println);
            System.err.println("Replay entries:");
            inlineesReplay.forEach(System.err::println);
            Asserts.fail("different inlining decision in normal run vs. replay run");
        }
        Asserts.assertEQ(expectedSize, inlineesNormal.size(), "unexpected number of inlinees found");
    }

    public static boolean compare(Entry e, String klass, String method, boolean kind) {
        return e.klass.equals(klass) && e.method.equals(method) && kind;
    }

    public static List<Entry> parseLogFile(String logFile, String rootMethod, String nmethodMatch, int inlineeCount) {
        String nmethodStart = "<nmethod";
        List<Entry> inlinees = new ArrayList<>();
        int foundLines = 0;
        try (var br = Files.newBufferedReader(Paths.get(logFile))) {
            String line;
            boolean nmethodLine = false;
            boolean inlinineLine = false;
            while ((line = br.readLine()) != null) {
                if (nmethodLine) {
                    // Ignore other entries which could be in between nmethod entry and inlining statements
                    if (line.startsWith("             ")) {
                        inlinineLine = true;
                        Pattern p = Pattern.compile("(\\S+)::(\\S+).*bytes\\)\s+(.*)");
                        Matcher matcher = p.matcher(line);
                        Asserts.assertTrue(matcher.find(), "must find inlinee method");
                        inlinees.add(new Entry(matcher.group(1), matcher.group(2), matcher.group(3).trim()));
                        foundLines++;
                    } else if (inlinineLine) {
                        Asserts.assertEQ(foundLines, inlineeCount, "did not find all inlinees");
                        return inlinees;
                    }
                } else {
                    nmethodLine = line.startsWith(nmethodStart) && line.contains(nmethodMatch);
                    if (nmethodLine) {
                        Asserts.assertTrue(line.contains(rootMethod), "should only dump inline information for " + rootMethod);
                    }
                }
            }
        } catch (IOException e) {
            throw new Error("Failed to read " + logFile + " data: " + e, e);
        }
        Asserts.fail("Should have found inlinees");
        return inlinees;
    }


    @Override
    public String getTestClass() {
        return "compiler.ciReplay." + className;
    }

    static class Entry {
        String klass;
        String method;
        String reason;

        public Entry(String klass, String method, String reason) {
            this.klass = klass;
            this.method = method;
            this.reason = reason;
        }

        public boolean isNormalInline() {
            return reason.equals("inline (hot)");
        }

        public boolean isForcedByReplay() {
            return reason.equals("force inline by ciReplay");
        }

        public boolean isDisallowedByReplay() {
            return reason.equals("disallowed by ciReplay");
        }

        public boolean isUnloadedSignatureClasses() {
            return reason.equals("unloaded signature classes");
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof Entry)) {
                return false;
            }

            Entry e = (Entry)other;
            return klass.equals(e.klass) && method.equals(e.method);
        }
    }
}

class ProtectionDomainTestCompiledBefore {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            bar(); // Ensure that bar() was compiled
        }
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    public static void test() {
        bar();
    }

    // Integer should be resolved for the protection domain of this class because the separate compilation of bar() in
    // the normal run will resolve all classes in the signature. Inlining succeeds.
    private static Integer bar() {
        InliningFoo.foo();
        return null;
    }
}

class ProtectionDomainTestNoOtherCompilationPublic {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    public static void test() {
        bar(); // Not compiled before separately
    }

    // Integer should be resolved for the protection domain of this class because getDeclaredMethods is called in normal run
    // when validating main() method. In this process, all public methods of this class are visited and its signature classes
    // are resolved. Inlining of bar() succeeds.
    public static Integer bar() {
        InliningFoo.foo();
        return null;
    }
}

class ProtectionDomainTestNoOtherCompilationPrivate {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    public static void test() {
        bar(); // Not compiled before separately
    }

    // Integer should be unresolved for the protection domain of this class even though getDeclaredMethods is called in normal
    // run when validating main() method. In this process, only public methods of this class are visited and its signature
    // classes are resolved. Since this method is private, the signature classes are not resolved for this protection domain.
    // Inlining of bar() should fail in normal run with "unresolved signature classes". Therefore, replay compilation should
    // also not inline bar().
    private static Integer bar() {
        InliningFoo.foo();
        return null;
    }
}

class ProtectionDomainTestNoOtherCompilationPrivateString {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    public static void test() {
        bar(); // Not compiled before separately
    }

    // Integer should be resovled for the protection domain of this class because getDeclaredMethods is called in normal run
    // when validating main() method. In this process, public methods of this class are visited and its signature classes
    // are resolved. bar() is private and not visited in this process (i.e. no resolution of String). But since main()
    // has String[] as parameter, the String class will be resolved for this protection domain. Inlining of bar() succeeds.
    private static String bar() {
        InliningFoo.foo();
        return null;
    }
}

class InliningFoo {
    public static void foo() {
        foo2();
    }

    private static void foo2() {
        InliningBar.bar();
    }
}


class InliningBar {
    public static void bar() {
        bar2();
    }

    private static void bar2() {}
}
