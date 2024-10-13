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

/*
 * @test
 * @bug 8159337
 * @summary Test Locale.caseFoldLanguageTag(String languageTag)
 * @run junit CaseFoldLanguageTagTest
 */

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Test the implementation of Locale.caseFoldLanguageTag(String languageTag).
 * A variety of well-formed tags are tested, composed of the following subtags:
 * language, extlang, script, region, variant, extension, singleton, privateuse,
 * grandfathered, and irregular. For more info, see the following,
 * <a href="https://www.rfc-editor.org/rfc/rfc5646.html#section-2.1">Tag Syntax</a>).
 * In addition, the method is tested to ensure that IllformedLocaleException and
 * NullPointerException are thrown given the right circumstances.
 */
public class CaseFoldLanguageTagTest {

    @ParameterizedTest
    @MethodSource("wellFormedTags")
    public void wellFormedTags(String tag, String foldedTag) {
        assertEquals(foldedTag, Locale.caseFoldLanguageTag(tag), String.format("Folded %s", tag));
    }

    @ParameterizedTest
    @MethodSource("illFormedTags")
    public void illFormedTags(String tag) {
        assertThrows(IllformedLocaleException.class, () ->
                Locale.caseFoldLanguageTag(tag));
    }

    @Test
    public void throwNPE() {
        assertThrows(NullPointerException.class, () ->
                Locale.caseFoldLanguageTag(null));
    }

    private static Stream<Arguments> wellFormedTags() {
        return Stream.of(
                // langtag tests
                // language
                Arguments.of("AB", "ab"),
                // language - ext
                Arguments.of("AB-ABC", "ab-abc"),
                // language - ext - script
                Arguments.of("AB-ABC-ABCD", "ab-abc-Abcd"),
                // language - ext - script - region
                Arguments.of("AB-ABC-ABCD-ab", "ab-abc-Abcd-AB"),
                // language - region
                Arguments.of("AB-ab", "ab-AB"),
                // language - script
                Arguments.of("AB-aBCD", "ab-Abcd"),
                // language - private use
                Arguments.of("AB-X-AB-ABCD", "ab-x-ab-abcd"),
                // language - ext - script - region - variant
                Arguments.of("AB-ABC-ABCD-ab-ABCDE", "ab-abc-Abcd-AB-ABCDE"),
                // language - ext - script - region - variant x 2
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-fghij",
                        "ab-abc-Abcd-AB-ABCDE-fghij"),
                // language - ext - script - region - variant - extension
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-A-ABCD",
                        "ab-abc-Abcd-AB-ABCDE-a-abcd"),
                // language - ext - script - region - variant - private
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-X-ABCD",
                        "ab-abc-Abcd-AB-ABCDE-x-abcd"),
                // language - ext - script - region - variant - extension x2
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-A-ABCD-B-EFGHI",
                        "ab-abc-Abcd-AB-ABCDE-a-abcd-b-efghi"),
                // language - ext - script - region - variant - extension - private
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-A-ABCD-X-ABCD",
                        "ab-abc-Abcd-AB-ABCDE-a-abcd-x-abcd"),
                // language - ext - script - region - variant x2 - extension x2  - private (x2 ext)
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-A-ABCD-X-ABCD-EFGHI",
                        "ab-abc-Abcd-AB-ABCDE-a-abcd-x-abcd-efghi"),
                // language - variant x2 - extension x3 - private
                Arguments.of("AB-aBcDeF-GhIjKl-a-ABC-DEFGH-B-ABC-C-ABC-X-A-ABC-DEF",
                        "ab-aBcDeF-GhIjKl-a-abc-defgh-b-abc-c-abc-x-a-abc-def"),
                // language - ext- script - region - variant - extension x2 - private (x2 ext)
                Arguments.of("AB-ABC-ABCD-ab-abCDe12-A-AB-B-ABCD-X-AB-ABCD",
                        "ab-abc-Abcd-AB-abCDe12-a-ab-b-abcd-x-ab-abcd"),

                // Multiple singleton extensions
                Arguments.of("AB-ABC-ABCD-ab-ABCDE-A-ABCD-GGG-ZZZ-B-EFGHI",
                        "ab-abc-Abcd-AB-ABCDE-a-abcd-ggg-zzz-b-efghi"),

                // private use tests
                Arguments.of("X-Abc", "x-abc"), // regular private
                Arguments.of("X-A-ABC", "x-a-abc"), // private w/ extended (incl. 1)
                Arguments.of("X-A-AB-Abcd", "x-a-ab-abcd"), // private w/ extended (incl. 1, 2, 4)

                // Legacy tests
                // irregular
                Arguments.of("I-AMI", "i-ami"),
                Arguments.of("EN-gb-OED", "en-GB-oed"),
                Arguments.of("SGN-be-fr", "sgn-BE-FR"),
                // regular
                Arguments.of("NO-BOK", "no-bok"),
                Arguments.of("CEL-GAULISH", "cel-gaulish"),
                Arguments.of("ZH-MIN-NAN", "zh-min-nan"),

                // Special JDK Cases (Variant and x-lvariant)
                Arguments.of("de-POSIX-x-URP-lvariant-Abc-Def", "de-POSIX-x-urp-lvariant-Abc-Def"),
                Arguments.of("JA-JPAN-JP-U-CA-JAPANESE-x-RANDOM-lvariant-JP",
                        "ja-Jpan-JP-u-ca-japanese-x-random-lvariant-JP"),
                Arguments.of("ja-JP-u-ca-japanese-x-lvariant-JP", "ja-JP-u-ca-japanese-x-lvariant-JP"),
                Arguments.of("XX-ABCD-yy-VARIANT-x-TEST-lvariant-JDK",
                        "xx-Abcd-YY-VARIANT-x-test-lvariant-JDK"),
                Arguments.of("ja-kana-jp-x-lvariant-Oracle-JDK-Standard-Edition",
                        "ja-Kana-JP-x-lvariant-Oracle-JDK-Standard-Edition"),
                Arguments.of("ja-kana-jp-x-Oracle-JDK-Standard-Edition",
                        "ja-Kana-JP-x-oracle-jdk-standard-edition"),
                Arguments.of("ja-kana-jp-a-ABC-EFG-ZZZ-b-aaa-x-Oracle-JDK-Standard-Edition",
                        "ja-Kana-JP-a-abc-efg-zzz-b-aaa-x-oracle-jdk-standard-edition")
        );
    }

    private static Stream<Arguments> illFormedTags() {
        return Stream.of(
                // Starts with non-language
                Arguments.of("xabadadoo-me"),
                // Starts with singleton
                Arguments.of("a-abc"),
                Arguments.of("a-singleton-en-us"),
                // Hanging dash
                Arguments.of("en-"),
                // Double dash
                Arguments.of("en--US"),
                // Script before ext lang
                Arguments.of("ab-Script-ext"),
                // Region before ext lang
                Arguments.of("ab-AB-ext"),
                // Variants at start
                Arguments.of("variant-first-ab")
        );
    }
}
