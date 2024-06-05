/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @bug 8305072
 * @requires (os.family == "windows")
 * @modules java.desktop/sun.awt.shell
 * @summary Verifies consistency of Win32ShellFolder2.compareTo
 * @run main/othervm --add-opens java.desktop/sun.awt.shell=ALL-UNNAMED Win32FolderSort
 */
public class Win32FolderSort {
    public static void main(String[] args) throws Exception {
        Class<?> folderManager = Class.forName("sun.awt.shell.Win32ShellFolderManager2");
        Class<?> folder = Class.forName("sun.awt.shell.Win32ShellFolder2");

        Method getDesktop = folderManager.getDeclaredMethod("getDesktop");
        getDesktop.setAccessible(true);
        Method getPersonal = folderManager.getDeclaredMethod("getPersonal");
        getPersonal.setAccessible(true);

        Method createShellFolder = folderManager.getDeclaredMethod("createShellFolder", folder, File.class);
        createShellFolder.setAccessible(true);

        Method isFileSystem = folder.getMethod("isFileSystem");
        isFileSystem.setAccessible(true);
        Method isSpecial = folder.getMethod("isSpecial");
        isSpecial.setAccessible(true);
        Method getChildByPath = folder.getDeclaredMethod("getChildByPath", String.class);
        getChildByPath.setAccessible(true);

        File desktop = (File) getDesktop.invoke(null);
        File personal = (File) getPersonal.invoke(null);
        if (!((Boolean) isSpecial.invoke(personal))) {
            throw new RuntimeException("personal is not special");
        }
        File fakePersonal = (File) getChildByPath.invoke(desktop, personal.getPath());
        if (fakePersonal == null) {
            fakePersonal = (File) createShellFolder.invoke(null, desktop,
                                                           new File(personal.getPath()));
        }
        if ((Boolean) isSpecial.invoke(fakePersonal)) {
            throw new RuntimeException("fakePersonal is special");
        }
        File homeDir = (File) createShellFolder.invoke(null, desktop,
                                                       new File(System.getProperty("user.home")));

        File[] files = {fakePersonal, personal, homeDir};
        for (File f : files) {
            if (!((Boolean) isFileSystem.invoke(f))) {
                throw new RuntimeException(f + " is not on file system");
            }
        }

        List<String> errors = new ArrayList<>(2);
        for (File f1 : files) {
            for (File f2 : files) {
                for (File f3 : files) {
                    String result = verifyCompareTo(f1, f2, f3);
                    if (result != null) {
                        String error = result + "\nwhere"
                                       + "\n  a = " + formatFile(f1, isSpecial)
                                       + "\n  b = " + formatFile(f2, isSpecial)
                                       + "\n  c = " + formatFile(f3, isSpecial);
                        errors.add(error);
                    }
                }
            }
        }


        System.out.println("Unsorted:");
        for (File f : files) {
            System.out.println(formatFile(f, isSpecial));
        }
        System.out.println();

        Arrays.sort(files);
        System.out.println("Sorted:");
        for (File f : files) {
            System.out.println(formatFile(f, isSpecial));
        }


        if (!errors.isEmpty()) {
            System.err.println("Implementation of Win32ShellFolder2.compareTo is inconsistent:");
            errors.forEach(System.err::println);
            throw new RuntimeException("Inconsistencies found: " + errors.size()
                                       + " - " + errors.get(0));
        }
    }

    /**
     * Verifies consistency of {@code Comparable} implementation.
     *
     * @param a the first object
     * @param b the second object
     * @param c the third object
     * @return error message if inconsistency is found,
     *         or {@code null } otherwise
     */
    private static String verifyCompareTo(File a, File b, File c) {
        // a < b & b < c => a < c
        if (a.compareTo(b) < 0 && b.compareTo(c) < 0) {
            if (a.compareTo(c) >= 0) {
                return "a < b & b < c but a >= c";
            }
        }

        // a > b & b > c => a > c
        if (a.compareTo(b) > 0 && b.compareTo(c) > 0) {
            if (a.compareTo(c) <= 0) {
                return "a > b & b > c but a <= c";
            }
        }

        // a = b & b = c => a = c
        if (a.compareTo(b) == 0 && b.compareTo(c) == 0) {
            if (a.compareTo(c) != 0) {
                return "a = b & b = c but a != c";
            }
        }

        return null;
    }

    private static String formatFile(File f, Method isSpecial)
            throws InvocationTargetException, IllegalAccessException {
        return f + "(" + isSpecial.invoke(f) + ")";
    }
}
