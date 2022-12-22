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

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InliningBase extends DumpReplayBase {
    public static final String LOG_FILE_NORMAL = "hotspot_normal.log";
    public static final String LOG_FILE_REPLAY = "hotspot_replay.log";
    protected final String[] commandLineReplay;
    protected final List<String> commandLineNormal;
    protected final Class<?> testClass;

    protected InliningBase(Class<?> testClass) {
        this.testClass = testClass;
        commandLineNormal = new ArrayList<>(List.of("-XX:LogFile=" + LOG_FILE_NORMAL + "", "-XX:+LogCompilation", "-XX:-TieredCompilation",
                                                                 "-XX:CompileCommand=exclude," + testClass.getName() + "::main",
                                                                 "-XX:CompileCommand=option," + testClass.getName() + "::test,bool,PrintInlining,true"));
        commandLineReplay = new String[]
                {"-XX:LogFile=" + LOG_FILE_REPLAY, "-XX:+LogCompilation",
                 "-XX:CompileCommand=option," + testClass.getName()  + "::test,bool,PrintInlining,true"};
    }

    protected void runTest() {
        runTest(commandLineNormal.toArray(new String[0]));
    }

    @Override
    public String getTestClass() {
        return testClass.getName();
    }

    @Override
    public void cleanup() {
        super.cleanup();
        remove(LOG_FILE_NORMAL);
        remove(LOG_FILE_REPLAY);
    }

    static class InlineEntry {
        String klass;
        String method;
        String reason;

        public InlineEntry(String klass, String method, String reason) {
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

        public boolean isForcedIncrementalInlineByReplay() {
            return reason.equals("force (incremental) inline by ciReplay");
        }

        public boolean isForcedInline() {
            return reason.equals("force inline by annotation");
        }

        public boolean isTooDeep() {
            return reason.equals("inlining too deep");
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof InlineEntry)) {
                return false;
            }

            InlineEntry e = (InlineEntry)other;
            return klass.equals(e.klass) && method.equals(e.method);
        }

        public boolean compare(String klass, String method, boolean kind) {
            return this.klass.equals(klass) && this.method.equals(method) && kind;
        }
    }

    protected static List<InlineEntry> parseLogFile(String logFile, String rootMethod, String nmethodMatch, int inlineeCount) {
        String nmethodStart = "<nmethod";
        List<InlineEntry> inlinees = new ArrayList<>();
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
                        inlinees.add(new InlineEntry(matcher.group(1), matcher.group(2), matcher.group(3).trim()));
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

    protected void verifyLists(List<InlineEntry> inlineesNormal, List<InlineEntry> inlineesReplay, int expectedSize) {
        if (!inlineesNormal.equals(inlineesReplay)) {
            System.err.println("Normal entries:");
            inlineesNormal.forEach(System.err::println);
            System.err.println("Replay entries:");
            inlineesReplay.forEach(System.err::println);
            Asserts.fail("different inlining decision in normal run vs. replay run");
        }
        Asserts.assertEQ(expectedSize, inlineesNormal.size(), "unexpected number of inlinees found");
    }
}

