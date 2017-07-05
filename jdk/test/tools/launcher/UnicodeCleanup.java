/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * Used by UnicodeTest.sh.
 *
 * Recursively deletes the given file/directory and its contents.
 * Equivalent to "rm -rf args...", but on NT-based Windows can
 * handle files with full Unicode names inside the given directories
 * while shells are generally limited to names using the system encoding.
 *
 * @author Norbert Lindenberg
 */



import java.io.File;

public class UnicodeCleanup {

    public static void main(String[] args) {

        for (int i = 0; i < args.length; i++) {
            delete(new File(args[i]));
        }
    }

    private static void delete(File file) {
        // paranoia is healthy in rm -rf
        String name = file.toString();
        if (name.equals(".") || name.equals("..") ||
                name.endsWith(File.separator + ".") ||
                name.endsWith(File.separator + "..")) {
            throw new RuntimeException("too risky to process: " + name);
        }
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            for (int i = 0; i < contents.length; i++) {
                delete(contents[i]);
            }
        }
        if (!file.delete()) {
            throw new RuntimeException("Unable to delete " + file);
        }
    }
}
