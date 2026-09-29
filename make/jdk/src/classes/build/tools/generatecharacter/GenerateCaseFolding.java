/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GenerateCaseFolding {

    public static void main(String[] args) throws Throwable {
        if (args.length != 3) {
            System.err.println("Usage: java GenerateCaseFolding TemplateFile CaseFolding.txt CaseFolding.java");
            System.exit(1);
        }
        var templateFile = Paths.get(args[0]);
        var caseFoldingTxt = Paths.get(args[1]);
        var genSrcFile = Paths.get(args[2]);

        // java.lang
        var supportedTypes = "^.*; [CF]; .*$";  // full/1:M case folding
        String[][] caseFoldings = Files.lines(caseFoldingTxt)
                .filter(line -> !line.startsWith("#") && line.matches(supportedTypes))
                .map(line -> {
                    var fields = line.split("; ");
                    var cp = fields[0];
                    fields = fields[2].trim().split(" ");
                    var folding = new String[fields.length + 1];
                    folding[0] = cp;
                    System.arraycopy(fields, 0, folding, 1, fields.length);
                    return folding;
                })
                .toArray(size -> new String[size][]);

        // util.regex
        var expandedSupportedTypes = "^.*; [CTS]; .*$";
        var expanded_caseFoldingEntries = Files.lines(caseFoldingTxt)
                .filter(line -> !line.startsWith("#") && line.matches(expandedSupportedTypes))
                .map(line -> {
                    String[] cols = line.split("; ");
                    return new String[]{cols[0], cols[1], cols[2]};
                })
                .filter(cols -> {
                    // the folding case doesn't map back to the original char.
                    var cp1 = Integer.parseInt(cols[0], 16);
                    var cp2 = Integer.parseInt(cols[2], 16);
                    return Character.toUpperCase(cp2) != cp1 && Character.toLowerCase(cp2) != cp1;
                })
                .map(cols -> String.format("        entry(0x%s, 0x%s)", cols[0], cols[2]))
                .collect(Collectors.joining(",\n", "", ""));

        // hack, hack, hack! the logic does not pick 0131. just add manually to support 'I's.
        // 0049; T; 0131; # LATIN CAPITAL LETTER I
        final String T_0x0131_0x49 = String.format("        entry(0x%04x, 0x%04x),\n", 0x0131, 0x49);

        Files.write(
                genSrcFile,
                Files.lines(templateFile)
                        .map(line -> line.contains("%%%Entries") ? genFoldingEntries(caseFoldings) : line)
                        .map(line -> line.contains("%%%Expanded_Case_Map_Entries") ? T_0x0131_0x49 + expanded_caseFoldingEntries : line)
                        .collect(Collectors.toList()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static long foldingToLong(String[] folding) {
        int cp = Integer.parseInt(folding[0], 16);
        long value = (long)Integer.parseInt(folding[1], 16);
        if (!Character.isSupplementaryCodePoint(cp) && folding.length != 2) {
            var shift = 16;
            for (int j = 2; j < folding.length; j++) {
                value |= (long)Integer.parseInt(folding[j], 16) << shift;
                shift <<= 1;
            }
            value = value | (long) (folding.length - 1) << 48;
        }
        return value;
    }

    private static String genFoldingEntries(String[][] foldings) {
        StringBuilder sb = new StringBuilder();
        sb.append("    private static final int[] CASE_FOLDING_CPS = {\n");
        int width = 10;
        for (int i = 0; i < foldings.length; i++) {
            if (i % width == 0)
                sb.append("        ");
            sb.append(String.format("0X%s", foldings[i][0]));
            if (i < foldings.length - 1)
                sb.append(", ");
            if (i % width == width - 1 || i == foldings.length - 1)
                sb.append("\n");
        }
        sb.append("    };\n\n");

        sb.append("    private static final long[] CASE_FOLDING_VALUES = {\n");
        width = 6;
        for (int i = 0; i < foldings.length; i++) {
            if (i % width == 0)
                sb.append("        "); // indent
            sb.append(String.format("0x%013xL", foldingToLong(foldings[i])));
            if (i < foldings.length - 1)
                sb.append(", ");
            if (i % width == width - 1 || i == foldings.length - 1) {
                sb.append("\n");
            }
        }
        sb.append("    };\n");
        return sb.toString();
    }
}
