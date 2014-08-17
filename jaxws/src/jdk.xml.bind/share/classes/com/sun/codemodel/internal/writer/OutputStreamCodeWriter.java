/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Output all source files into a single stream.
 *
 * This is primarily for test purposes.
 *
 * @author
 *      Aleksei Valikov (valikov@gmx.net)
 */
package com.sun.codemodel.internal.writer;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;

public class OutputStreamCodeWriter extends CodeWriter {
        private final PrintStream out;

        /**
         * @param os
         *            This stream will be closed at the end of the code generation.
         */
        public OutputStreamCodeWriter(OutputStream os, String encoding) {
                try {
                        this.out = new PrintStream(os, false, encoding);
                } catch (UnsupportedEncodingException ueex) {
                        throw new IllegalArgumentException(ueex);
                }
                this.encoding = encoding;
        }

        public OutputStream openBinary(JPackage pkg, String fileName)
                        throws IOException {
                return new FilterOutputStream(out) {
                        public void close() {
                                // don't let this stream close
                        }
                };
        }

        public void close() throws IOException {
                out.close();
        }
}
