/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jlink.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * This file was originally a copy of CommandLine.java in
 * com.sun.tools.javac.main.
 * It should track changes made to that file.
 */

/**
 * Various utility methods for processing Java tool command line arguments.
 */
public class CommandLine {

    static void loadCmdFile(InputStream in, List<String> args)
            throws IOException {
        try (Reader r = new InputStreamReader(in)) {
            Tokenizer t = new Tokenizer(r);
            String s;
            while ((s = t.nextToken()) != null) {
                args.add(s);
            }
        }
    }
    public static class Tokenizer {
        private final Reader in;
        private int ch;

        public Tokenizer(Reader in) throws IOException {
            this.in = in;
            ch = in.read();
        }

        public String nextToken() throws IOException {
            skipWhite();
            if (ch == -1) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            char quoteChar = 0;

            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\f':
                        if (quoteChar == 0) {
                            return sb.toString();
                        }
                        sb.append((char) ch);
                        break;

                    case '\n':
                    case '\r':
                        return sb.toString();

                    case '\'':
                    case '"':
                        if (quoteChar == 0) {
                            quoteChar = (char) ch;
                        } else if (quoteChar == ch) {
                            quoteChar = 0;
                        } else {
                            sb.append((char) ch);
                        }
                        break;

                    case '\\':
                        if (quoteChar != 0) {
                            ch = in.read();
                            switch (ch) {
                                case '\n':
                                case '\r':
                                    while (ch == ' ' || ch == '\n'
                                            || ch == '\r' || ch == '\t'
                                            || ch == '\f') {
                                        ch = in.read();
                                    }
                                    continue;

                                case 'n':
                                    ch = '\n';
                                    break;
                                case 'r':
                                    ch = '\r';
                                    break;
                                case 't':
                                    ch = '\t';
                                    break;
                                case 'f':
                                    ch = '\f';
                                    break;
                                default:
                                    break;
                            }
                        }
                        sb.append((char) ch);
                        break;

                    default:
                        sb.append((char) ch);
                }

                ch = in.read();
            }

            return sb.toString();
        }

        void skipWhite() throws IOException {
            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case '\f':
                        break;

                    case '#':
                        ch = in.read();
                        while (ch != '\n' && ch != '\r' && ch != -1) {
                            ch = in.read();
                        }
                        break;

                    default:
                        return;
                }

                ch = in.read();
            }
        }
    }
}
