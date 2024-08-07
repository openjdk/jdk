/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Html5Entities {

    private static final Map<String, String> NAMED_CHARACTER_REFERENCES = readEntities();
    private static final String ENTITY_PATH = "/jdk/internal/org/commonmark/internal/util/entities.txt";

    public static String entityToString(String input) {
        if (!input.startsWith("&") || !input.endsWith(";")) {
            return input;
        }

        String value = input.substring(1, input.length() - 1);
        if (value.startsWith("#")) {
            value = value.substring(1);
            int base = 10;
            if (value.startsWith("x") || value.startsWith("X")) {
                value = value.substring(1);
                base = 16;
            }

            try {
                int codePoint = Integer.parseInt(value, base);
                if (codePoint == 0) {
                    return "\uFFFD";
                }
                return new String(Character.toChars(codePoint));
            } catch (IllegalArgumentException e) {
                return "\uFFFD";
            }
        } else {
            String s = NAMED_CHARACTER_REFERENCES.get(value);
            if (s != null) {
                return s;
            } else {
                return input;
            }
        }
    }

    private static Map<String, String> readEntities() {
        Map<String, String> entities = new HashMap<>();
        InputStream stream = Html5Entities.class.getResourceAsStream(ENTITY_PATH);
        Charset charset = StandardCharsets.UTF_8;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, charset))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                int equal = line.indexOf("=");
                String key = line.substring(0, equal);
                String value = line.substring(equal + 1);
                entities.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading data for HTML named character references", e);
        }
        entities.put("NewLine", "\n");
        return entities;
    }
}
