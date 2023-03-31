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


/**
 * @test
 * @bug 8303018
 * @summary  Check j.l.Character.isEmoji/isEmojiPresentation/isEmojiModifier
 *              isEmojiModifierBase/isEmojiComponent/isExtendedPictographic
 * @library /lib/testlibrary/java/lang
 */

import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.MIN_CODE_POINT;
import static java.lang.Character.isEmoji;
import static java.lang.Character.isEmojiPresentation;
import static java.lang.Character.isEmojiModifier;
import static java.lang.Character.isEmojiModifierBase;
import static java.lang.Character.isEmojiComponent;
import static java.lang.Character.isExtendedPictographic;

public class TestEmojiProperties {
    // Masks representing Emoji properties (16-bit `B` table masks in
    // CharacterData.java)
    private static final int EMOJI = 0x0040;
    private static final int EMOJI_PRESENTATION = 0x0080;
    private static final int EMOJI_MODIFIER = 0x0100;
    private static final int EMOJI_MODIFIER_BASE = 0x0200;
    private static final int EMOJI_COMPONENT = 0x0400;
    private static final int EXTENDED_PICTOGRAPHIC = 0x0800;

    public static void main(String[] args) throws IOException {
        var emojiProps = Files.readAllLines(UCDFiles.EMOJI_DATA).stream()
                .map(line -> line.split("#", 2)[0])
                .filter(Predicate.not(String::isBlank))
                .map(line -> line.split("[ \t]*;[ \t]*", 2))
                .flatMap(map -> {
                    var range = map[0].split("\\.\\.", 2);
                    var start = Integer.valueOf(range[0], 16);
                    return range.length == 1 ?
                        Stream.of(new AbstractMap.SimpleEntry<>(start, convertType(map[1].trim()))) :
                        IntStream.rangeClosed(start,
                            Integer.valueOf(range[1], 16))
                        .mapToObj(cp -> new AbstractMap.SimpleEntry<>(cp, convertType(map[1].trim())));
                })
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue, (v1, v2) -> v1 | v2));

        final var fails = new Integer[1];
        fails[0] = 0;
        IntStream.rangeClosed(MIN_CODE_POINT, MAX_CODE_POINT).forEach(cp -> {
            var props = emojiProps.getOrDefault(cp, 0L);
            if ((props & EMOJI) != 0 ^ isEmoji(cp)) {
                System.err.printf("""
                        isEmoji(0x%x) failed. Returned: %b
                        """, cp, isEmoji(cp));
                fails[0] ++;
            }

            if ((props & EMOJI_PRESENTATION) != 0 ^ isEmojiPresentation(cp)) {
                System.err.printf("""
                        isEmojiPresentation(0x%x) failed. Returned: %b
                        """, cp, isEmojiPresentation(cp));
                fails[0] ++;
            }

            if ((props & EMOJI_MODIFIER) != 0 ^ isEmojiModifier(cp)) {
                System.err.printf("""
                        isEmojiModifier(0x%x) failed. Returned: %b
                        """, cp, isEmojiModifier(cp));
                fails[0] ++;
            }

            if ((props & EMOJI_MODIFIER_BASE) != 0 ^ isEmojiModifierBase(cp)) {
                System.err.printf("""
                        isEmojiModifierBase(0x%x) failed. Returned: %b
                        """, cp, isEmojiModifierBase(cp));
                fails[0] ++;
            }

            if ((props & EMOJI_COMPONENT) != 0 ^ isEmojiComponent(cp)) {
                System.err.printf("""
                        isEmojiComponent(0x%x) failed. Returned: %b
                        """, cp, isEmojiComponent(cp));
                fails[0] ++;
            }

            if ((props & EXTENDED_PICTOGRAPHIC) != 0 ^ isExtendedPictographic(cp)) {
                System.err.printf("""
                        isExtendedPictographic(0x%x) failed. Returned: %b
                        """, cp, isExtendedPictographic(cp));
                fails[0] ++;
            }
        });
        if (fails[0] != 0) {
            throw new RuntimeException("TestEmojiProperties failed=" + fails);
        }
    }

    private static long convertType(String type) {
        return switch (type) {
            case "Emoji" -> EMOJI;
            case "Emoji_Presentation" -> EMOJI_PRESENTATION;
            case "Emoji_Modifier" -> EMOJI_MODIFIER;
            case "Emoji_Modifier_Base" -> EMOJI_MODIFIER_BASE;
            case "Emoji_Component" -> EMOJI_COMPONENT;
            case "Extended_Pictographic" -> EXTENDED_PICTOGRAPHIC;
            default -> throw new InternalError();
        };
    }
}
