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

/*
 * @test
 * @bug 8349755
 * @summary UL should put ISO8601 date stamp on file creation and file rotation
 * @requires vm.flagless
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver FileLocalLogOnStartAndRotation
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class FileLocalLogOnStartAndRotation {
    private static Pattern startLogRegex = Pattern.compile("^.*Started logging for file at.*$");
    private static Pattern rotateLogRegex = Pattern.compile("^.*Rotated file at.*$");

    // Match default decorations, ex: [0.001s][info][logging]
    private static Pattern decorationRegex = Pattern.compile("^\\[.*\\]\\[.*\\]\\[.*\\].*$");

    private static void analyzeFile(File f, bool shouldHaveDecorations) {
        String content = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);

        Matcher matchStart = startLogRegex.matcher(content);
        Matcher matchRotate = rotateLogRegex.matcher(content);
        long startMatchCount = matchStart.results().count();
        long rotateMatchCount = matchRotate.results().count();
        if (startMatchCount > 0 && rotateMatchCount > 0) {
            throw new RuntimeException("Should only have either start or log rotate message");
        }
        if (startMatchCount == 0 && rotateMatchCount == 0) {
            throw new RuntimeException("Must have either start or log rotate message");
        }


        Stream<Boolean> linesWithDecorations =
            content
                .lines()
                .map((l) -> decorationRegex.matcher(l))
                .map((m) -> m.find());
        if (shouldHaveDecorations) {
            boolean eachLineHasADecoration = linesWithDecorations.reduce(true, (a, b) -> a && b);
            if (!eachLineHasADecoration) {
                throw new RuntimeException("Expected that each line has decoration, but doesn't");
            }
        } else {
            boolean someLineHasADecoration = linesWithDecorations.reduce(false, (a, b) -> a || b);
            if (someLineHasADecoration) {
                throw new RuntimeException("Expected that no line has decoration, but some do");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        /*
        Run two tests:
        1. With file rotation and decorations
        2. With file rotations, but without decorations
        Check that in both of these cases we get log start and log rotate messages inside of the files.

        For case #2, ensure that the start and rotate messages also runs without decorations.
        This is a way for us to test that the file-local messages also abide by the decorator options specified.
        */
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:os=all:file=default.FileLocalLogOnStartAndRotationDecorations.log::filesize=100", "--version");
        Process p = pb.start();
        p.waitFor();

        File directory = new File(".");
        String fileRegex = ".*FileLocalLogOnStartAndRotationDecorations\\.log\\.[0-9]*";
        File[] files = directory.listFiles((dir, name) -> name.matches(fileRegex));

        for (File f : files) {
            analyzeFile(f, true);
        }

        // Re-do the same test, but this time run without any decorations.
        ProcessBuilder pb2 = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xlog:os=all:file=default.FileLocalLogOnStartAndRotation.log:none:filesize=100", "--version");
        Process p2 = pb2.start();
        p2.waitFor();

        String fileRegexNoDecs = ".*FileLocalLogOnStartAndRotation\\.log\\.[0-9]*";
        File[] filesNoDecs = directory.listFiles((dir, name) -> name.matches(fileRegexNoDecs));

        for (File f : filesNoDecs) {
            analyzeFile(f, false);
        }
    }
}
