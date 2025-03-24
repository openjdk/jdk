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
package jdk.internal.console;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class NativeConsoleReader {

    public static char[] readline(Reader reader, Writer out, boolean password) throws IOException {
        byte[] originalTermios = switchToRaw();
        Thread restoreConsole = new Thread(() -> {
            restore(originalTermios);
        });
        try {
            Runtime.getRuntime().addShutdownHook(restoreConsole);
            int width = terminalWidth();
            out.append("\033[6n").flush(); //ask the terminal to provide cursor location
            return SimpleConsoleReader.doRead(reader, out, password, -1, () -> width);
        } finally {
            restoreConsole.run();
            Runtime.getRuntime().removeShutdownHook(restoreConsole);
        }
    }

    static {
        loadNativeLibrary();
    }

    @SuppressWarnings("restricted")
    private static void loadNativeLibrary() {
        System.loadLibrary("le");
        initIDs();
    }

    private static native void initIDs();
    private static native byte[] switchToRaw();
    private static native void restore(byte[] termios);
    private static native int terminalWidth();
}
