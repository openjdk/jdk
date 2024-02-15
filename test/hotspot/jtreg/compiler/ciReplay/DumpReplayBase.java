/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class DumpReplayBase extends CiReplayBase {

    private static final String DUMP_REPLAY_PATTERN = "replay_pid";
    private List<File> replayFiles;
    private String replayFileName;

    @Override
    public void runTest(boolean needCoreDump, String... args) {
        throw new RuntimeException("use runTests(String...)");
    }

    public void runTest(String... args) {
        if (generateReplay(args)) {
            testAction();
            cleanup();
        } else {
            throw new Error("Host is not configured to generate cores");
        }
    }

    @Override
    public void cleanup() {
        replayFiles.forEach(f -> remove(f.getName()));
    }

    @Override
    public String getReplayFileName() {
        Asserts.assertEQ(replayFiles.size(), 1, "Test should only dump 1 replay file when trying to replay compile");
        return replayFileName;
    }

    public boolean generateReplay(String... vmopts) {
        OutputAnalyzer oa;
        try {
            List<String> options = new ArrayList<>(Arrays.asList(vmopts));
            options.add("-XX:CompileCommand=option," + getTestClass() + "::" + getTestMethod() + ",bool,DumpReplay,true");
            options.add("-XX:+IgnoreUnrecognizedVMOptions");
            options.add("-XX:TypeProfileLevel=222");
            options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::" + getTestMethod());
            options.add("-Xbatch");
            options.add(getTestClass());
            oa = ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder(options));
            Asserts.assertEquals(oa.getExitValue(), 0, "Crash JVM exits gracefully");
            replayFiles = Files.list(Paths.get("."))
                                    .map(Path::toFile)
                                    .filter(f -> f.getName().startsWith(DUMP_REPLAY_PATTERN)).collect(Collectors.toList());
            Asserts.assertFalse(replayFiles.isEmpty(), "Did not find a replay file starting with " + DUMP_REPLAY_PATTERN);
            replayFileName = replayFiles.get(0).getName();
        } catch (Throwable t) {
            throw new Error("Can't create replay: " + t, t);
        }
        return true;
    }

    public int getCompileIdFromFile(String replayFileName) {
        Pattern p = Pattern.compile("replay_pid.*_compid([0-9]+)\\.log");
        Matcher matcher = p.matcher(replayFileName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Could not parse compile id from filename \"" + replayFileName + "\"");
            }
        } else {
            throw new RuntimeException("Could not find compile id in filename \"" + replayFileName + "\"");
        }
    }

    public List<File> getReplayFiles() {
        return replayFiles;
    }
}
