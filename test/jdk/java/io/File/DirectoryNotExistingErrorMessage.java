/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8290313
 * @summary Test error message when file directory is not existing
 */

import java.io.File;
import java.io.IOException;

public class DirectoryNotExistingErrorMessage {

    public static void main(String ... args) {

        String errorMsg = "The specified directory does no exist! Please contact system administrator!";
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir")+"/not-existing");
            System.out.println("Does " + tmpDir + " exists? " + tmpDir.exists());

            File tmpFile = File.createTempFile("prefix", ".suffix",tmpDir);

        } catch (IOException ioe) {
            System.out.println("errorMessage:" + ioe.getMessage());
            System.out.println(errorMsg.contains(ioe.getMessage()));
        }
    }
}
