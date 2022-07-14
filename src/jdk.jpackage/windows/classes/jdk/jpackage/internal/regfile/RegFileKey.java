/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.regfile;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.jpackage.internal.regfile.parser.Token;

import static java.util.stream.Collectors.toCollection;

public class RegFileKey {

    private RegFileKey(RegFileRootKey root, ArrayList<String> pathParts) {
       this.root = root;
       this.pathParts = pathParts;
       this.values = new ArrayList<>();
    }

    public static RegFileKey fromToken(Token token) {
        // example: [root\path\to\key]
        if (!(token.image.startsWith("[") && token.image.endsWith("]"))) {
            throw new RegFileTokenException(token,
                    "Registry key image is invalid");
        }
        String fullPath = token.image.substring(1, token.image.length() - 1);
        String[] parts = fullPath.split("\\\\");
        if (parts.length < 2) {
            throw new RegFileTokenException(token,
                    "Registry key cannot consist of root only");
        }
        if (parts.length > MAX_KEY_DEPTH) {
            throw new RegFileTokenException(token, String.format(
                    "Registry key depth: %d exceeds max allowed depth: %d",
                    parts.length, MAX_KEY_DEPTH));
        }
        RegFileRootKey root = RegFileRootKey.fromString(token, parts[0]);
        ArrayList<String> pathParts = Arrays.stream(parts).skip(1)
                .peek(p -> {
                    if (p.length() > MAX_KEY_PART_LENGTH) {
                        throw new RegFileTokenException(token, String.format(
                                "Registry key part length: %d exceeds max allowed length: %d",
                                p.length(), MAX_KEY_PART_LENGTH));
                    }
                })
                .collect(toCollection(ArrayList::new));
        return new RegFileKey(root, pathParts);
    }

    public void addValue(RegFileValue value) {
        this.values.add(value);
    }

    public RegFileRootKey getRoot() {
        return root;
    }

    public ArrayList<String> getPathParts() {
        return pathParts;
    }

    public ArrayList<RegFileValue> getValues() {
        return values;
    }

    private final RegFileRootKey root;
    private final ArrayList<String> pathParts;
    private final ArrayList<RegFileValue> values;

    private static final int MAX_KEY_PART_LENGTH = 255;
    private static final int MAX_KEY_DEPTH = 512;

}
