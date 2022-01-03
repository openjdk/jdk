/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7025809 8028543 6415644 8028544 8029942 8187951 8193291 8196551 8233096 8275308
 * @summary Test latest, latestSupported, underscore as keyword, etc.
 * @author  Joseph D. Darcy
 * @modules java.compiler
 *          jdk.compiler
 */

import java.util.*;
import java.util.function.Predicate;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

/**
 * Verify behavior of latest[Supported] and other methods.
 */
public class TestSourceVersion {
    public static void main(String... args) {
        testLatestSupported();
        testVersionVaryingKeywords();
        testRestrictedKeywords();
        testVar();
        testYield();
        testValueOfRV();
        testRuntimeVersion();
    }

    private static void testLatestSupported() {
        SourceVersion[] values = SourceVersion.values();
        SourceVersion last = values[values.length - 1];
        SourceVersion latest = SourceVersion.latest();
        SourceVersion latestSupported = SourceVersion.latestSupported();

        if (latest == last &&
            latestSupported == SourceVersion.valueOf("RELEASE_" +
                                                     Runtime.version().feature()) &&
            (latest == latestSupported ||
             (latest.ordinal() - latestSupported.ordinal() == 1)) )
            return;
        else {
            throw new RuntimeException("Unexpected release value(s) found:\n" +
                                       "latest:\t" + latest + "\n" +
                                       "latestSupported:\t" + latestSupported);
        }
    }

    private static void testVersionVaryingKeywords() {
        Map<String, SourceVersion> keyWordStart =
            Map.of("strictfp", RELEASE_2,
                   "assert",   RELEASE_4,
                   "enum",     RELEASE_5,
                   "_",        RELEASE_9);

        for (Map.Entry<String, SourceVersion> entry : keyWordStart.entrySet()) {
            String key = entry.getKey();
            SourceVersion value = entry.getValue();

            check(true,  key, (String s) -> isKeyword(s), "keyword", latest());
            check(false, key, (String s) -> isName(s),    "name",    latest());

            for(SourceVersion version : SourceVersion.values()) {
                boolean isKeyword = version.compareTo(value) >= 0;

                check(isKeyword,  key, (String s) -> isKeyword(s, version), "keyword", version);
                check(!isKeyword, key, (String s) -> isName(s, version),    "name",    version);
            }
        }
    }

    private static void testRestrictedKeywords() {
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

    private static void testVar() {
        for (SourceVersion version : SourceVersion.values()) {
            Predicate<String> isKeywordVersion = (String s) -> isKeyword(s, version);
            Predicate<String> isNameVersion = (String s) -> isName(s, version);

            for (String name : List.of("var", "foo.var", "var.foo")) {
                check(false, name, isKeywordVersion, "keyword", version);
                check(true, name,  isNameVersion, "name", version);
            }
        }
    }

    private static void testYield() {
        for (SourceVersion version : SourceVersion.values()) {
            Predicate<String> isKeywordVersion = (String s) -> isKeyword(s, version);
            Predicate<String> isNameVersion = (String s) -> isName(s, version);

            for  (String name : List.of("yield", "foo.yield", "yield.foo")) {
                check(false, name, isKeywordVersion, "keyword", version);
                check(true, name,  isNameVersion, "name", version);
            }
        }
    }

    private static void check(boolean expected,
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
    private static void testValueOfRV() {
        for (SourceVersion sv : SourceVersion.values()) {
            if (sv == RELEASE_0) {
                continue;
            } else {
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

    private static void checkValueOfResult(SourceVersion expected, String versionString) {
        Runtime.Version rv = Runtime.Version.parse(versionString);
        SourceVersion  result = SourceVersion.valueOf(rv);
        if (result != expected) {
            throw new RuntimeException("Unexpected result " + result +
                                       " of mapping Runtime.Version " + versionString +
                                       " intead of " + expected);
        }
    }

    private static void testRuntimeVersion() {
        for (SourceVersion sv : SourceVersion.values()) {
            Runtime.Version result = sv.runtimeVersion();
            if (sv.compareTo(RELEASE_6) < 0) {
                if (result != null) {
                    throw new RuntimeException("Unexpected result non-null " + result +
                                               " as runtime version of  " + sv);
                }
            } else {
                Runtime.Version expected = Runtime.Version.parse(Integer.toString(sv.ordinal()));
                if (!result.equals(expected)) {
                    throw new RuntimeException("Unexpected result " + result +
                                               " as runtime version of " + sv);
                }
            }
        }
    }
}
