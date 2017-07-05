/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6652929
 * @summary verify handling of File.getPath()
 */

import java.awt.*;
import java.io.*;

public class FontFile {
    public static void main(String[] args) throws Exception {
        String sep = System.getProperty("file.separator");
        String fname = ".." + sep + "A.ttf";
        String dir = System.getProperty("test.src");
        if (dir != null) {
            fname = dir + sep + fname;
        }
        final String name = fname;
        System.out.println("Will try to access " + name);
        if (!(new File(name)).canRead()) {
           System.out.println("File not available : can't run test");
           return;
        }
        System.out.println("File is available. Verify no access under SM");

        System.setSecurityManager(new SecurityManager());


        // Check cannot read file.
        try {
            new FileInputStream(name);
            throw new Error("Something wrong with test environment");
        } catch (SecurityException exc) {
            // Good.
        }

        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT,
            new File("nosuchfile") {
                    private boolean read;
                    @Override public String getPath() {
                        if (read) {
                            return name;
                        } else {
                            read = true;
                            return "somefile";
                        }
                    }
                    @Override public boolean canRead() {
                        return true;
                    }
               }
            );
          System.err.println(font.getFontName());
          throw new RuntimeException("No expected exception");
        }  catch (IOException e) {
          System.err.println("Test passed.");
        }
    }
}
