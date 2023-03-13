/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package build.tools.generatecharacter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A class holding emoji character properties
 * https://unicode.org/reports/tr51/#Emoji_Properties_and_Data_Files
 */
class EmojiData {
    // Masks representing Emoji properties (16-bit `B` table masks in
    // CharacterData.java)
    private static final int EMOJI = 0x0040;
    private static final int EMOJI_PRESENTATION = 0x0080;
    private static final int EMOJI_MODIFIER = 0x0100;
    private static final int EMOJI_MODIFIER_BASE = 0x0200;
    private static final int EMOJI_COMPONENT = 0x0400;
    private static final int EXTENDED_PICTOGRAPHIC = 0x0800;

    // Emoji properties map
    private final Map<Integer, Long> emojiProps;

    static EmojiData readSpecFile(Path file, int plane) throws IOException {
        return new EmojiData(file, plane);
    }

    EmojiData(Path file, int plane) throws IOException {
        emojiProps = Files.readAllLines(file).stream()
            .map(line -> line.split("#", 2)[0])
            .filter(Predicate.not(String::isBlank))
            .map(line -> line.split("[ \t]*;[ \t]*", 2))
            .flatMap(map -> {
                var range = map[0].split("\\.\\.", 2);
                var start = Integer.valueOf(range[0], 16);
                if ((start >> 16) != plane) {
                    return Stream.empty();
                } else {
                    return range.length == 1 ?
                        Stream.of(new AbstractMap.SimpleEntry<>(start, convertType(map[1].trim()))) :
                        IntStream.rangeClosed(start, Integer.valueOf(range[1], 16))
                            .mapToObj(cp -> new AbstractMap.SimpleEntry<>(cp, convertType(map[1].trim())));
                }
            })
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey,
                    AbstractMap.SimpleEntry::getValue,
                    (v1, v2) -> v1 | v2));
    }

    long properties(int cp) {
        return emojiProps.get(cp);
    }

    Set<Integer> codepoints() {
        return emojiProps.keySet();
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
