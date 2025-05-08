/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7025809 8028543 6415644 8028544 8029942 8187951 8193291 8196551 8233096
 *      8275308 8355536
 * @summary Test latest, latestSupported, underscore as keyword, etc.
 * @author  Joseph D. Darcy
 * @modules java.compiler
 *          jdk.compiler
 * @run junit/othervm -DTestSourceVersion.DIFFERENT_LATEST_SUPPORTED=false TestSourceVersion
 * @build java.compiler/javax.lang.model.SourceVersion
 * @run junit/othervm -DTestSourceVersion.DIFFERENT_LATEST_SUPPORTED=true TestSourceVersion
 */

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static javax.lang.model.SourceVersion.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify behavior of latest[Supported] and other methods.
 * There's a copy of "updated" SourceVersion in java.compiler subdirectory
 * to emulate running with a newer java.compiler module.
 */
public class TestSourceVersion {

    private static final boolean DIFFERENT_LATEST_SUPPORTED = Boolean.getBoolean("TestSourceVersion.DIFFERENT_LATEST_SUPPORTED");

    @Test
    void testLatestSupported() {
        SourceVersion[] values = SourceVersion.values();
        SourceVersion last = values[values.length - 2];
        SourceVersion latest = SourceVersion.latest();
        SourceVersion latestSupported = SourceVersion.latestSupported();

        assertSame(last, latest);
        assertSame(latestSupported, SourceVersion.valueOf("RELEASE_" + Runtime.version().feature()));
        assertSame(Runtime.version().feature(), latestSupported.runtimeVersion().feature());
        if (DIFFERENT_LATEST_SUPPORTED) {
            assertEquals(latestSupported.ordinal(), latest.ordinal() - 1, () -> latestSupported.toString() + " ordinal");
        } else {
            assertSame(latest, latestSupported);
        }
    }

    @Test
    void testCurrentPreview() {
        final SourceVersion preview = CURRENT_PREVIEW;

        assertFalse(preview.isSupported());
        assertNotSame(CURRENT_PREVIEW, SourceVersion.latest());
        assertNotSame(CURRENT_PREVIEW, SourceVersion.latestSupported());
        assertEquals(values().length - 1, CURRENT_PREVIEW.ordinal());

        assertEquals(latest().runtimeVersion(), CURRENT_PREVIEW.runtimeVersion());
    }

    static Stream<SourceVersion> actualSvs() {
        return Arrays.stream(SourceVersion.values()).filter(SourceVersion::isSupported);
    }

    @ParameterizedTest
    @MethodSource("actualSvs")
    void testEachVersion(SourceVersion sv) {
        if (sv.compareTo(SourceVersion.RELEASE_6) >= 0) {
            assertEquals(sv, SourceVersion.valueOf(sv.runtimeVersion()));
        }
        Runtime.Version result = sv.runtimeVersion();
        if (sv.compareTo(RELEASE_6) < 0) {
            assertNull(result);
        } else {
            Runtime.Version expected = Runtime.Version.parse(Integer.toString(sv.ordinal()));
            assertEquals(expected, result);
        }
    }

    static Stream<Arguments> keywordStart() {
        Map<String, SourceVersion> keyWordStart =
            Map.of("strictfp", RELEASE_2,
                   "assert",   RELEASE_4,
                   "enum",     RELEASE_5,
                   "_",        RELEASE_9);

        return keyWordStart.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest
    @MethodSource("keywordStart")
    void testVersionVaryingKeywords(String key, SourceVersion value) {
        check(true,  key, (String s) -> isKeyword(s), "keyword", latest());
        check(false, key, (String s) -> isName(s),    "name",    latest());

        for(SourceVersion version : SourceVersion.values()) {
            boolean isKeyword = version.compareTo(value) >= 0;

            check(isKeyword,  key, (String s) -> isKeyword(s, version), "keyword", version);
            check(!isKeyword, key, (String s) -> isName(s, version),    "name",    version);
        }
    }

    @Test
    void testRestrictedKeywords() {
        // Restricted keywords are not full keywords

        /*
         * JLS 3.9
         * " A further ten character sequences are restricted
         * keywords: open, module, requires, transitive, exports,
         * opens, to, uses, provides, and with"
         */
        Set<String> restrictedKeywords =
            Set.of("open", "module", "requires", "transitive", "exports",
                   "opens", "to", "uses", "provides", "with",
                   // Assume "record" and "sealed" will be restricted keywords.
                   "record", "sealed");

        for (String key : restrictedKeywords) {
            for (SourceVersion version : SourceVersion.values()) {
                check(false, key, (String s) -> isKeyword(s, version), "keyword", version);
                check(true,  key, (String s) -> isName(s, version),    "name",    version);
            }
        }
    }

    @Test
    void testVar() {
        for (SourceVersion version : SourceVersion.values()) {
            Predicate<String> isKeywordVersion = (String s) -> isKeyword(s, version);
            Predicate<String> isNameVersion = (String s) -> isName(s, version);

            for (String name : List.of("var", "foo.var", "var.foo")) {
                check(false, name, isKeywordVersion, "keyword", version);
                check(true, name,  isNameVersion, "name", version);
            }
        }
    }

    @Test
    void testYield() {
        for (SourceVersion version : SourceVersion.values()) {
            Predicate<String> isKeywordVersion = (String s) -> isKeyword(s, version);
            Predicate<String> isNameVersion = (String s) -> isName(s, version);

            for  (String name : List.of("yield", "foo.yield", "yield.foo")) {
                check(false, name, isKeywordVersion, "keyword", version);
                check(true, name,  isNameVersion, "name", version);
            }
        }
    }

    void check(boolean expected,
               String input,
               Predicate<String> predicate,
               String message,
               SourceVersion version) {
        boolean result  = predicate.test(input);
        if (result != expected) {
            throw new RuntimeException("Unexpected " + message +  "-ness of " + input +
                                       " on " + version);
        }
    }

    /**
     * Test that SourceVersion.valueOf() maps a Runtime.Version to a
     * SourceVersion properly. The SourceVersion result is only a
     * function of the feature() component of a Runtime.Version.
     */
    @Test
    void testValueOfRV() {
        for (SourceVersion sv : SourceVersion.values()) {
            if (sv != RELEASE_0 && sv.isSupported()) {
                // Plain mapping; e.g. "17" -> RELEASE_17
                String featureBase = Integer.toString(sv.ordinal());
                checkValueOfResult(sv, featureBase);

                // More populated runtime version, N.N
                checkValueOfResult(sv, featureBase + "." + featureBase);
            }
        }

        // Out of range test
        try {
            int latestFeature = SourceVersion.latest().runtimeVersion().feature();
            SourceVersion.valueOf(Runtime.Version.parse(Integer.toString(latestFeature +1)));
            throw new RuntimeException("Should not reach");
        } catch (IllegalArgumentException iae) {
            ; // Expected
        }
    }

    void checkValueOfResult(SourceVersion expected, String versionString) {
        Runtime.Version rv = Runtime.Version.parse(versionString);
        SourceVersion  result = SourceVersion.valueOf(rv);
        if (result != expected) {
            throw new RuntimeException("Unexpected result " + result +
                                       " of mapping Runtime.Version " + versionString +
                                       " intead of " + expected);
        }
    }
}
