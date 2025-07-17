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
 * @bug 8065554 8309515
 * @run main NamedGroupsTests
 */

import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamedGroupsTests {

    /* An implementation purposely not overriding any default method */
    private static class TestMatcherNoNamedGroups implements MatchResult {

        @Override
        public int start() {
            return 0;
        }

        @Override
        public int start(int group) {
            return 0;
        }

        @Override
        public int end() {
            return 0;
        }

        @Override
        public int end(int group) {
            return 0;
        }

        @Override
        public String group() {
            return null;
        }

        @Override
        public String group(int group) {
            return null;
        }

        @Override
        public int groupCount() {
            return 0;
        }

    }

    public static void main(String[] args) {
        testMatchResultNoDefault();

        testPatternNamedGroups();
        testMatcherNamedGroups();
        testMatchResultNamedGroups();

        testMatcherHasMatch();
        testMatchResultHasMatch();

        testMatchResultStartEndGroupBeforeMatchOp();
        testMatchResultStartEndGroupAfterMatchOp();

        testMatchAfterUsePattern();
    }

    private static void testMatchResultNoDefault() {
        TestMatcherNoNamedGroups m = new TestMatcherNoNamedGroups();
        try {
            m.hasMatch();
        } catch (UnsupportedOperationException e) {  // swallowing intended
        }
        try {
            m.namedGroups();
        } catch (UnsupportedOperationException e) {  // swallowing intended
        }
        try {
            m.start("anyName");
        } catch (UnsupportedOperationException e) {  // swallowing intended
        }
        try {
            m.end("anyName");
        } catch (UnsupportedOperationException e) {  // swallowing intended
        }
        try {
            m.group("anyName");
        } catch (UnsupportedOperationException e) {  // swallowing intended
        }
    }

    private static void testMatchResultStartEndGroupBeforeMatchOp() {
        Matcher m = Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("abc");
        try {
            m.start("anyName");
        } catch (IllegalStateException e) {  // swallowing intended
        }
        try {
            m.end("anyName");
        } catch (IllegalStateException e) {  // swallowing intended
        }
        try {
            m.group("anyName");
        } catch (IllegalStateException e) {  // swallowing intended
        }
    }

    private static void testMatchResultStartEndGroupAfterMatchOp() {
        testMatchResultStartEndGroupNoMatch();
        testMatchResultStartEndGroupWithMatch();
        testMatchResultStartEndGroupNoMatchNoSuchGroup();
        testMatchResultStartEndGroupWithMatchNoSuchGroup();
    }

    private static void testMatchResultStartEndGroupNoMatch() {
        Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .results()
                .forEach(r -> {
                    if (r.start("some") >= 0) {
                        throw new RuntimeException("start(\"some\")");
                    }
                    if (r.start("rest") >= 0) {
                        throw new RuntimeException("start(\"rest\")");
                    }
                    if (r.end("some") >= 0) {
                        throw new RuntimeException("end(\"some\")");
                    }
                    if (r.end("rest") >= 0) {
                        throw new RuntimeException("end(\"rest\")");
                    }
                    if (r.group("some") != null) {
                        throw new RuntimeException("group(\"some\")");
                    }
                    if (r.group("rest") != null) {
                        throw new RuntimeException("group(\"rest\")");
                    }
                });
    }

    private static void testMatchResultStartEndGroupWithMatch() {
        Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("abc")
                .results()
                .forEach(r -> {
                    if (r.start("some") < 0) {
                        throw new RuntimeException("start(\"some\")");
                    }
                    if (r.start("rest") < 0) {
                        throw new RuntimeException("start(\"rest\")");
                    }
                    if (r.end("some") < 0) {
                        throw new RuntimeException("end(\"some\")");
                    }
                    if (r.end("rest") < 0) {
                        throw new RuntimeException("end(\"rest\")");
                    }
                    if (r.group("some") == null) {
                        throw new RuntimeException("group(\"some\")");
                    }
                    if (r.group("rest") == null) {
                        throw new RuntimeException("group(\"rest\")");
                    }
                });
    }

    private static void testMatchResultStartEndGroupNoMatchNoSuchGroup() {
        Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .results()
                .forEach(r -> {
                    try {
                        r.start("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                    try {
                        r.end("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                    try {
                        r.group("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                });
    }

    private static void testMatchResultStartEndGroupWithMatchNoSuchGroup() {
        Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("abc")
                .results()
                .forEach(r -> {
                    try {
                        r.start("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                    try {
                        r.end("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                    try {
                        r.group("noSuchGroup");
                    } catch (IllegalArgumentException e) {  // swallowing intended
                    }
                });
    }

    private static void testMatchResultHasMatch() {
        testMatchResultHasMatchNoMatch();
        testMatchResultHasMatchWithMatch();
    }

    private static void testMatchResultHasMatchNoMatch() {
        Matcher m = Pattern.compile(".+").matcher("");
        m.find();
        if (m.toMatchResult().hasMatch()) {
            throw new RuntimeException();
        }
    }

    private static void testMatchResultHasMatchWithMatch() {
        Matcher m = Pattern.compile(".+").matcher("abc");
        m.find();
        if (!m.toMatchResult().hasMatch()) {
            throw new RuntimeException();
        }
    }

    private static void testMatcherHasMatch() {
        testMatcherHasMatchNoMatch();
        testMatcherHasMatchWithMatch();
    }

    private static void testMatcherHasMatchNoMatch() {
        Matcher m = Pattern.compile(".+").matcher("");
        m.find();
        if (m.hasMatch()) {
            throw new RuntimeException();
        }
    }

    private static void testMatcherHasMatchWithMatch() {
        Matcher m = Pattern.compile(".+").matcher("abc");
        m.find();
        if (!m.hasMatch()) {
            throw new RuntimeException();
        }
    }

    private static void testMatchResultNamedGroups() {
        testMatchResultNamedGroupsNoNamedGroups();
        testMatchResultNamedGroupsOneNamedGroup();
        testMatchResultNamedGroupsTwoNamedGroups();
    }

    private static void testMatchResultNamedGroupsNoNamedGroups() {
        if (!Pattern.compile(".*").matcher("")
                .toMatchResult().namedGroups().isEmpty()) {
            throw new RuntimeException();
        }
    }

    private static void testMatchResultNamedGroupsOneNamedGroup() {
        if (!Pattern.compile("(?<all>.*)").matcher("")
                .toMatchResult().namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        }
    }

    private static void testMatchResultNamedGroupsTwoNamedGroups() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("")
                .toMatchResult().namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        }
    }

    private static void testMatcherNamedGroups() {
        testMatcherNamedGroupsNoNamedGroups();
        testMatcherNamedGroupsOneNamedGroup();
        testMatcherNamedGroupsTwoNamedGroups();
    }

    private static void testMatcherNamedGroupsNoNamedGroups() {
        if (!Pattern.compile(".*").matcher("").namedGroups().isEmpty()) {
            throw new RuntimeException();
        }
    }

    private static void testMatcherNamedGroupsOneNamedGroup() {
        if (!Pattern.compile("(?<all>.*)").matcher("").namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        }
    }

    private static void testMatcherNamedGroupsTwoNamedGroups() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").matcher("").namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        }
    }

    private static void testPatternNamedGroups() {
        testPatternNamedGroupsNoNamedGroups();
        testPatternNamedGroupsOneNamedGroup();
        testPatternNamedGroupsTwoNamedGroups();
    }

    private static void testPatternNamedGroupsNoNamedGroups() {
        if (!Pattern.compile(".*").namedGroups().isEmpty()) {
            throw new RuntimeException();
        }
    }

    private static void testPatternNamedGroupsOneNamedGroup() {
        if (!Pattern.compile("(?<all>.*)").namedGroups()
                .equals(Map.of("all", 1))) {
            throw new RuntimeException();
        }
    }

    private static void testPatternNamedGroupsTwoNamedGroups() {
        if (!Pattern.compile("(?<some>.+?)(?<rest>.*)").namedGroups()
                .equals(Map.of("some", 1, "rest", 2))) {
            throw new RuntimeException();
        }
    }

    private static void testMatchAfterUsePattern() {
        Pattern p1 = Pattern.compile("(?<a>...)(?<b>...)");
        Matcher m = p1.matcher("foobar");
        if (!m.matches()) {
            throw new RuntimeException("matches() expected");
        }
        if (!m.group("a").equals("foo")) {
            throw new RuntimeException("\"foo\" expected for group(\"a\")");
        }

        Pattern p2 = Pattern.compile("(?<b>...)(?<a>...)");
        m.usePattern(p2);
        if (!m.matches()) {
            throw new RuntimeException("matches() expected");
        }
        if (!m.group("a").equals("bar")) {
            throw new RuntimeException("\"bar\" expected for group(\"a\")");
        }
    }

}
