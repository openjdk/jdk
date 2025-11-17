/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;

sealed interface RtfConverter {

    void convert(Path path) throws IOException;

    static boolean isRtfFile(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return false;
        }

        try (InputStream fin = Files.newInputStream(path)) {
            byte[] firstBits = new byte[7];

            if (fin.read(firstBits) == firstBits.length) {
                String header = new String(firstBits);
                return "{\\rtf1\\".equals(header);
            }
        }

        return false;
    }

    static Optional<RtfConverter> createSimple(Path path) throws IOException {
        if (isRtfFile(path)) {
            return Optional.of(Details.Simple.VALUE);
        } else {
            return Optional.empty();
        }
    }

    static final class Details {
        private enum Simple implements RtfConverter {

            VALUE;

            @Override
            public void convert(Path path) throws IOException {
                var content = Files.readAllLines(path);
                try (var w = Files.newBufferedWriter(path, Charset.forName("Windows-1252"))) {
                    convert(content.stream(), w);
                }
            }

            private void convert(Stream<String> textFile, Appendable sink) throws IOException {

                sink.append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1033"
                        + "{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\n"
                        + "\\viewkind4\\uc1\\pard\\sa200\\sl276"
                        + "\\slmult1\\lang9\\fs20 ");

                try {
                    textFile.forEach(toConsumer(l -> {
                        for (char c : l.toCharArray()) {
                            // 0x00 <= ch < 0x20 Escaped (\'hh)
                            // 0x20 <= ch < 0x80 Raw(non - escaped) char
                            // 0x80 <= ch <= 0xFF Escaped(\ 'hh)
                            // 0x5C, 0x7B, 0x7D (special RTF characters
                            // \,{,})Escaped(\'hh)
                            // ch > 0xff Escaped (\\ud###?)
                            if (c < 0x10) {
                                sink.append("\\'0");
                                sink.append(Integer.toHexString(c));
                            } else if (c > 0xff) {
                                sink.append("\\ud");
                                sink.append(Integer.toString(c));
                                // \\uc1 is in the header and in effect
                                // so we trail with a replacement char if
                                // the font lacks that character - '?'
                                sink.append("?");
                            } else if ((c < 0x20) || (c >= 0x80) ||
                                    (c == 0x5C) || (c == 0x7B) ||
                                    (c == 0x7D)) {
                                sink.append("\\'");
                                sink.append(Integer.toHexString(c));
                            } else {
                                sink.append(c);
                            }
                        }
                        // blank lines are interpreted as paragraph breaks
                        if (l.length() < 1) {
                            sink.append("\\par");
                        } else {
                            sink.append(" ");
                        }
                        sink.append("\r\n");
                    }));
                } catch (ExceptionBox ex) {
                    throw (IOException)ex.getCause();
                }

                sink.append("}\r\n");
            }
        }

    }
}
