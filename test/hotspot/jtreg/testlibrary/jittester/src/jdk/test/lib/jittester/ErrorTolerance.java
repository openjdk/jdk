/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.test.lib.Asserts;

/**
  * Compares reference output to the one that's being verified.
  *
  * The class can tolerate some errors (like OutOfMemoryError),
  * as their nature is unpredictable and ignore missing intrinsics in
  * compiled code stack traces.
  */
public class ErrorTolerance {

    // Put the most annoying intrinsics here
    private static final List<Predicate<String>> PATTERNS = List.of(
        Pattern.compile(".*at java.base.*misc.Unsafe.allocateUninitializedArray0.*").asPredicate()
    );

    private static final Predicate<String> OOME = Pattern.compile(
            "Exception in thread \".*\" java.lang.OutOfMemoryError.*").asPredicate();

    private static boolean isIntrinsicCandidate(String line) {
        return PATTERNS.stream()
                       .map(pattern -> pattern.test(line))
                       .reduce(false, (acc, match) -> acc | match);
    }

    public static void assertIsAcceptable(String message, Stream<String> gold, Stream<String> run) {
            Iterator<String> goldIt = gold.iterator();
            Iterator<String> runIt = run.iterator();

            while (goldIt.hasNext() && runIt.hasNext()) {
                String goldLine = goldIt.next();
                String runLine = runIt.next();

                if (OOME.test(goldLine)) {
                    return;     // OOMEs are simply ignored.
                }

                // Skipping intrinsics in 'run'
                while (isIntrinsicCandidate(goldLine) &&
                       !runLine.equals(goldLine) &&
                       goldIt.hasNext()) {
                    goldLine = goldIt.next();
                }

                if (!goldLine.equals(runLine)) {
                    String fullMessage = "While " + message + ":\n" +
                                         "Expected: " + goldLine + "\n" +
                                         "Actual  : " + runLine + "\n";
                    Asserts.fail(fullMessage);
                }
            }
            Asserts.assertEquals(goldIt.hasNext(), runIt.hasNext(), message + ": files are different");
    }

    public static void assertIsAcceptable(Path gold, Path run) {
        String comparisonNames = "'" + gold + "' and '" + run + "'";
        try {
            assertIsAcceptable("comparing files " + comparisonNames,
                    Files.lines(gold), Files.lines(run));
        } catch (IOException e) {
            throw new Error("Could not compare files: '" + comparisonNames);
        }
    }
}
