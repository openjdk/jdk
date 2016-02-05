/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * @test
 * @bug 8074165
 * @modules java.desktop/sun.applet
 * @run main/othervm -Duser.language=en DeprecatedAppletViewer
 */
public final class DeprecatedAppletViewer {

    private static final String TEXT = "AppletViewer is deprecated.";

    public static void main(final String[] args) {
        final PrintStream old = System.out;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        final PrintStream ps = new PrintStream(baos);
        try {
            System.setOut(ps);
            sun.applet.Main.main(new String[]{});
        } finally {
            System.setOut(old);
        }

        final String text = new String(baos.toByteArray());
        if (!text.contains(TEXT)) {
            System.err.println("The text should contain: \"" + TEXT + "\"");
            System.err.println("But the current text is: ");
            System.err.println(text);
            throw new RuntimeException("Error");
        }
    }
}
