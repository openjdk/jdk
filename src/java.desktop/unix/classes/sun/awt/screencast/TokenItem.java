/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.screencast;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static sun.awt.screencast.ScreencastHelper.SCREENCAST_DEBUG;

/**
 * Helper class used by {@link TokenStorage} as restore token record
 * with its associated screen boundaries.
 *
 * It helps in serialization/deserialization of screen boundaries
 * to/from string format.
 *
 * The screen boundary is represented as {@code _x_y_width_height}
 * and can be repeated several times.
 */
final class TokenItem {

    final String token;
    final List<Rectangle> allowedScreensBounds;

    public TokenItem(String token, int[] allowedScreenBounds) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("empty or null tokens are not allowed");
        }
        if (allowedScreenBounds.length % 4 != 0) {
            throw new RuntimeException("array with incorrect length provided");
        }

        this.token = token;

        this.allowedScreensBounds = IntStream
                        .iterate(0,
                                i -> i < allowedScreenBounds.length,
                                i -> i + 4)
                        .mapToObj(i -> new Rectangle(
                                allowedScreenBounds[i], allowedScreenBounds[i+1],
                                allowedScreenBounds[i+2], allowedScreenBounds[i+3]
                        ))
                        .collect(Collectors.toList());
    }

    public boolean hasAllScreensWithExactMatch(List<Rectangle> bounds) {
        return allowedScreensBounds.containsAll(bounds);
    }

    public boolean hasAllScreensOfSameSize(List<Dimension> screenSizes) {
        // We also need to consider duplicates, since there may be
        // multiple screens of the same size.
        // The token item must also have at least the same number
        // of screens with that size.

        List<Dimension> tokenSizes = allowedScreensBounds
                .stream()
                .map(bounds -> new Dimension(bounds.width, bounds.height))
                .collect(Collectors.toCollection(ArrayList::new));

        return screenSizes.size() == screenSizes
                .stream()
                .filter(tokenSizes::remove)
                .count();
    }

    private static final int MAX_SIZE = 50000;
    private static final int MIN_SIZE = 1;

    public boolean hasValidBounds() {
        //This check is very rough, in order to filter out abnormal values
        for (Rectangle bounds : allowedScreensBounds) {
            if (bounds.x < -MAX_SIZE || bounds.x > MAX_SIZE
                    || bounds.y < -MAX_SIZE || bounds.y > MAX_SIZE
                    || bounds.width < MIN_SIZE || bounds.width > MAX_SIZE
                    || bounds.height < MIN_SIZE || bounds.height > MAX_SIZE
            ) {
                return false;
            }
        }
        return true;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (Rectangle bounds : allowedScreensBounds) {
            sb.append("_%d_%d_%d_%d"
                    .formatted(bounds.x, bounds.y, bounds.width, bounds.height));
        }
        return sb.toString();
    }

    public static TokenItem parse(String token, Object input) {
        if (token == null || input == null) return null;

        try {
            int[] integers = Arrays.stream(String.valueOf(input)
                    .split("_"))
                    .filter(s -> !s.isBlank())
                    .mapToInt(Integer::parseInt)
                    .toArray();

            if (integers.length % 4 == 0) {
                TokenItem tokenItem = new TokenItem(token, integers);
                if (tokenItem.hasValidBounds()) {
                    return tokenItem;
                }
            }
        } catch (NumberFormatException ignored) {}

        if (SCREENCAST_DEBUG) {
            System.err.printf("Malformed record for token %s: %s\n",
                    token, input);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Token: " + token + "\n");
        for (Rectangle bounds : allowedScreensBounds) {
            sb.append("\t").append(bounds).append("\n");
        }
        return sb.toString();
    }
}