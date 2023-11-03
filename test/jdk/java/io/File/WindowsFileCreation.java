/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *  @bug 8315034
 *  @summary Verify that File.mkdirs() will not fail to create folders on Windows shared folder
 *  Passing remote shared folder as parent folder with the prefix of the subfolder
 *  (ex, \\\\192.168.1.110\\sharedfolder\\temp) as first parameter,
 *  second parameter will be the subfolder to be previously created folder(ex, letter a). For one loop, it will
 *  two folders.
 *  With different the parameters in command line, the test should run successfully without failure. This test
 *  needs to run three Java processes simultaneously to verify the fix.
 *  This test requires to run manually and the parent folder as Windows shared folder should be existing already.
 */

import java.io.File;
import java.io.IOException;

public class WindowsFileCreation {

    public static void main(String[] args) throws IOException {
        //String path = "\\\\192.168.1.229\\sharedfolder\\temp0\\a";
        if (args.length != 2) {
            throw new IOException("Two parameters in command line are required");
        }
        try {
            for (int i = 0; i < 100; i++) {
                StringBuilder path = new StringBuilder(args[0]);
                File f = new File(path.append(i).append("\\").append(args[1]).toString());
                if (!f.mkdirs()) {
                    throw new IllegalStateException("Folder " + f.getAbsolutePath() + " cannot be created");
                }
            }
        } catch (Exception e) {
            throw new IOException("File Creation Failed in Windows Shared Folder");
        }
    }
}
