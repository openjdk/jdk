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

/*
 * @test
 * @bug 8065554
 * @run main NamedGroupsTests
 */

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamedGroupsTests {

    public static void main(String[] args) {
        testPatternNamedGroups();
        testMatcherNamedGroups();
        testMatchResultNamedGroups();

        testMatcherHasMatch();
        testMatchResultHasMatch();

        testMatchResultStartEndGroup();
    }

    private static void testMatchResultStartEndGroup() {
        testMatchResultStartEndGroup1();
        testMatchResultStartEndGroup2();
        testMatchResultStartEndGroup3();
        testMatchResultStartEndGroup4();
    }

    private static void testMatchResultStartEndGroup1() {
        List<MatchResult> list = Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .results().toList();
        for (var result : list) {
            if (result.start("some") >= 0) {
                throw new RuntimeException("start(\"some\")");
            }
            if (result.start("rest") >= 0) {
                throw new RuntimeException("start(\"rest\")");
            }
            if (result.end("some") >= 0) {
                throw new RuntimeException("end(\"some\")");
            }
            if (result.end("rest") >= 0) {
                throw new RuntimeException("end(\"rest\")");
            }
            if (result.group("some") != null) {
                throw new RuntimeException("group(\"some\")");
            }
            if (result.group("rest") != null) {
                throw new RuntimeException("group(\"rest\")");
            }
        }
    }

    private static void testMatchResultStartEndGroup2() {
        List<MatchResult> list = Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("abc")
                .results().toList();
        for (var result : list) {
            if (result.start("some") < 0) {
                throw new RuntimeException("start(\"some\")");
            }
            if (result.start("rest") < 0) {
                throw new RuntimeException("start(\"rest\")");
            }
            if (result.end("some") < 0) {
                throw new RuntimeException("end(\"some\")");
            }
            if (result.end("rest") < 0) {
                throw new RuntimeException("end(\"rest\")");
            }
            if (result.group("some") == null) {
                throw new RuntimeException("group(\"some\")");
            }
            if (result.group("rest") == null) {
                throw new RuntimeException("group(\"rest\")");
            }
        }
    }

    private static void testMatchResultStartEndGroup3() {
        Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .results()
                .forEach(r -> {
                        try {
                            r.start("noSuchGroup");
                            r.end("noSuchGroup");
                            r.group("noSuchGroup");
                        } catch (IllegalArgumentException e) {  // swallowing intended
                        }
                    });
    }

    private static void testMatchResultStartEndGroup4() {
        List<MatchResult> list = Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("abc")
                .results().toList();
        for (var result : list) {
            try {
                result.start("noSuchGroup");
                result.end("noSuchGroup");
                result.group("noSuchGroup");
            } catch (IllegalArgumentException e) {  // swallowing intended
            }
        }
    }

    private static void testMatchResultHasMatch() {
        testMatchResultHasMatch1();
        testMatchResultHasMatch2();
    }

    private static void testMatchResultHasMatch1() {
        Matcher m = Pattern.compile(".+").matcher("");
        m.find();
        if (m.toMatchResult().hasMatch()) {
            throw new RuntimeException();
        };
    }

    private static void testMatchResultHasMatch2() {
        Matcher m = Pattern.compile(".+").matcher("abc");
        m.find();
        if (!m.toMatchResult().hasMatch()) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherHasMatch() {
        testMatcherHasMatch1();
        testMatcherHasMatch2();
    }

    private static void testMatcherHasMatch1() {
        Matcher m = Pattern.compile(".+").matcher("");
        m.find();
        if (m.hasMatch()) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherHasMatch2() {
        Matcher m = Pattern.compile(".+").matcher("abc");
        m.find();
        if (!m.hasMatch()) {
            throw new RuntimeException();
        };
    }

    private static void testMatchResultNamedGroups() {
        testMatchResultNamedGroups1();
        testMatchResultNamedGroups2();
        testMatchResultNamedGroups3();
        testMatchResultNamedGroups4();
    }

    private static void testMatchResultNamedGroups1() {
        if (!Pattern.compile(".*").matcher("")
                .toMatchResult().namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testMatchResultNamedGroups2() {
        if (!Pattern.compile("(.*)").matcher("")
                .toMatchResult().namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testMatchResultNamedGroups3() {
        if (!Pattern.compile("(?<all>.*)").matcher("")
                .toMatchResult().namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        };
    }

    private static void testMatchResultNamedGroups4() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .toMatchResult().namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherNamedGroups() {
        testmatcherNamedGroups1();
        testMatcherNamedGroups2();
        testMatcherNamedGroups3();
        testMatcherNamedGroups4();
    }

    private static void testmatcherNamedGroups1() {
        if (!Pattern.compile(".*").matcher("").namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherNamedGroups2() {
        if (!Pattern.compile("(.*)").matcher("").namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherNamedGroups3() {
        if (!Pattern.compile("(?<all>.*)").matcher("").namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        };
    }

    private static void testMatcherNamedGroups4() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("").namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        };
    }

    private static void testPatternNamedGroups() {
        testPatternNamedGroups1();
        testPatternNamedGroups2();
        testPatternNamedGroups3();
        testPatternNamedGroups4();
    }

    private static void testPatternNamedGroups1() {
        if (!Pattern.compile(".*").namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testPatternNamedGroups2() {
        if (!Pattern.compile("(.*)").namedGroups().isEmpty()) {
            throw new RuntimeException();
        };
    }

    private static void testPatternNamedGroups3() {
        if (!Pattern.compile("(?<all>.*)").namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        };
    }

    private static void testPatternNamedGroups4() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        };
    }

}
