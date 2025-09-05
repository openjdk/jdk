/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6282388
 * @summary Tests that AWT uses correct toolkit wrapped into HeadlessToolkit
 * @modules java.desktop/sun.awt:open
 * @library /test/lib
 * @run main/othervm -Djava.awt.headless=true TestWrapped
 */

import java.awt.Toolkit;
import java.lang.Class;
import java.lang.reflect.Field;

import jdk.test.lib.Platform;

public final class TestWrapped {

    private static final String HEADLESS_TOOLKIT = "sun.awt.HeadlessToolkit";
    private static final String MACOSX_TOOLKIT = "sun.lwawt.macosx.LWCToolkit";
    private static final String UNIX_TOOLKIT = "sun.awt.X11.XToolkit";
    private static final String WINDOWS_TOOLKIT = "sun.awt.windows.WToolkit";

    public static void main(String[] args) throws Exception {
        String expectedToolkitClassName;
        if (Platform.isWindows()) {
            expectedToolkitClassName = WINDOWS_TOOLKIT;
        } else if (Platform.isOSX()) {
            expectedToolkitClassName = MACOSX_TOOLKIT;
        } else {
            expectedToolkitClassName = UNIX_TOOLKIT;
        }

        Toolkit tk = Toolkit.getDefaultToolkit();
        Class<?> tkClass = tk.getClass();
        if (!tkClass.getName().equals(HEADLESS_TOOLKIT)) {
            System.err.println("Expected: " + HEADLESS_TOOLKIT);
            System.err.println("Actual: " + tkClass.getName());
            throw new RuntimeException("Wrong default toolkit");
        }

        Field f = tkClass.getDeclaredField("tk");
        f.setAccessible(true);
        Class<?> wrappedClass = f.get(tk).getClass();
        if (!wrappedClass.getName().equals(expectedToolkitClassName)) {
            System.err.println("Expected: " + expectedToolkitClassName);
            System.err.println("Actual: " + wrappedClass.getName());
            throw new RuntimeException("Wrong wrapped toolkit");
        }
    }
}
