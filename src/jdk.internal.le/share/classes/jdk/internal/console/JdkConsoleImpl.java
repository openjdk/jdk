/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.console;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import jdk.internal.console.SimpleConsoleReader.CleanableBuffer;

import jdk.internal.io.BaseJdkConsoleImpl;

/**
 * JdkConsole implementation based on the platform's TTY, with basic keyboard navigation.
 */
public final class JdkConsoleImpl extends BaseJdkConsoleImpl {

    private final boolean isTTY;

    @Override
    public char[] readPassword() {
        return readPassword(Locale.getDefault(Locale.Category.FORMAT), "");
    }

    @Override
    public void flush() {
        pw.flush();
    }

    @Override
    public Charset charset() {
        return charset;
    }

    protected char[] readline(boolean password) throws IOException {
        if (isTTY) {
            return NativeConsoleReader.readline(reader, out, password);
        } else {
            //dumb input:
            CleanableBuffer buffer = new CleanableBuffer();

            try {
                int r;

                OUTER: while ((r = reader.read()) != (-1)) {
                    switch (r) {
                        case '\r', '\n' -> { break OUTER; }
                        default -> buffer.insert(buffer.length(), (char) r);
                    }
                }

                return buffer.getData();
            } finally {
                buffer.zeroOut();
            }
        }
    }

    public JdkConsoleImpl(boolean isTTY, Charset charset) {
        super(charset);
        this.isTTY = isTTY;
    }

}
