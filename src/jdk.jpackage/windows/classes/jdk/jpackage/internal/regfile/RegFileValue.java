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

import jdk.jpackage.internal.regfile.parser.Token;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RegFileValue {
    private RegFileValue(String name, RegFileValueType type, String value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public static RegFileValue fromTokens(Token nameToken, Token typeToken, Token valueToken) {
        String name = nameFromToken(nameToken);
        RegFileValueType type = RegFileValueType.fromString(typeToken, typeToken.image);
        String value = valueFromToken(type, valueToken);
        return new RegFileValue(name, type, value);
    }

    public String getName() {
        return name;
    }

    public RegFileValueType getType() {
        return type;
    }

    public String getWixType() {
        switch(type) {
            case REG_SZ: return "string";
            case REG_BINARY: return "binary";
            case REG_DWORD: return "integer";
            // todo: removeme
            case REG_QWORD: return "integer";
            case REG_MULTI_SZ: return "multiString";
            case REG_EXPAND_SZ: return "expandable";
            default: throw new IllegalStateException(String.format(
                    "Invalid value: [%s]", type.name()));
        }
    }

    public String getValue() {
        return value;
    }

    public List<String> getWixValue() {
        switch(type) {
            case REG_SZ:
                return List.of(value);
            case REG_BINARY:
                return List.of(value.replaceAll(",", ""));
            case REG_DWORD:
                return List.of(String.valueOf(Integer.parseInt(value, 16)));
            case REG_MULTI_SZ: {
                byte[] bytes = fromHex(value);
                ArrayList<Byte> buf = new ArrayList<>();
                List<byte[]> byteLines = new ArrayList<>();
                for (int i = 0; i < bytes.length; i += 2) {
                    if (0 == bytes[i] && 0 == bytes[i + 1]) {
                        byte[] bl = new byte[buf.size()];
                        for (int j = 0; j < buf.size(); j++) {
                            bl[j] = buf.get(j);
                        }
                        byteLines.add(bl);
                        buf = new ArrayList<>();
                    } else {
                        buf.add(bytes[i]);
                        buf.add(bytes[i + 1]);
                    }
                }
                return byteLines.stream().map(bl -> new String(bl, StandardCharsets.UTF_16LE))
                        .collect(Collectors.toList());
            }
            case REG_EXPAND_SZ: {
                byte[] bytes = fromHex(value);
                return List.of(new String(bytes, StandardCharsets.UTF_16LE));
            }
            default: throw new IllegalStateException(String.format(
                    "Invalid value: [%s]", type.name()));
        }
    }

    private static String nameFromToken(Token token) {
        // example: '"my name"='
        final String name;
        if (DEFAULT_NAME.equals(token.image)) {
            name = "";
        } else if (token.image.startsWith(DOUBLE_QUOTE) &&
                token.image.endsWith(QUOTED_NAME_SUFFIX)) {
            name = token.image.substring(DOUBLE_QUOTE.length(),
                    token.image.length() - QUOTED_NAME_SUFFIX.length());
        } else {
            throw new RegFileTokenException(token,
                    "Registry value name image is invalid");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new RegFileTokenException(token, String.format(
                    "Registry value name length: %d exceeds max allowed length: %d",
                    name.length(), MAX_NAME_LENGTH));
        }
        return name;
    }

    @SuppressWarnings("fallthrough")
    private static String valueFromToken(RegFileValueType type, Token token) {
        // example: 'my string value"<EOL>'
        final int eolLength;
        if (token.image.endsWith(EOL_CR_LF)) {
            eolLength = EOL_CR_LF.length();
        } else if (token.image.endsWith(EOL_LF)) {
            eolLength = EOL_LF.length();
        } else {
            throw new RegFileTokenException(token,
                    "Registry value image is invalid");
        }
        final String valueImage = token.image
                .substring(0, token.image.length() - eolLength);
        switch (type) {
            case REG_SZ: // example: 'my string value"'
                return valueImage.substring(0, valueImage.length() - DOUBLE_QUOTE.length());
            case REG_BINARY:
            case REG_MULTI_SZ:
            case REG_EXPAND_SZ: // example: de,ad,be,...,ef,\<EOL>  de,ad
                if (valueImage.contains(EOL_CR_LF)) {
                    return BACKSLASH_EOL_CR_LF_REGEX.matcher(valueImage).replaceAll("");
                } else if (valueImage.contains(EOL_LF)) {
                    return BACKSLASH_EOL_LF_REGEX.matcher(valueImage).replaceAll("");
                } // fall through
            case REG_DWORD:
            case REG_QWORD: // example: 0000002a
                return valueImage;
            default:
                throw new RegFileTokenException(token, String.format(
                        "Registry value type: %s is not supported", type));
        }
    }

    private static byte[] fromHex(String hexString) {
        String[] strings = hexString.split( "," );
        byte[] bytes = new byte[strings.length - 2];
        for (int i = 0; i < strings.length - 2; i++) {
            bytes[i] = Byte.parseByte(strings[i], 16);
        }
        return bytes;
    }

    private final String name;
    private final RegFileValueType type;
    private final String value;

    private static final int MAX_NAME_LENGTH = 16383;
    private static final String DEFAULT_NAME = "@=";
    private static final String QUOTED_NAME_SUFFIX = "\"=";
    private static final String EOL_CR_LF = "\r\n";
    private static final String EOL_LF = "\n";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String DOUBLE_SPACE = "  ";
    private static final Pattern BACKSLASH_EOL_CR_LF_REGEX = Pattern.compile("\\\\" + EOL_CR_LF + DOUBLE_SPACE);
    private static final Pattern BACKSLASH_EOL_LF_REGEX = Pattern.compile("\\\\" + EOL_LF + DOUBLE_SPACE);

}
