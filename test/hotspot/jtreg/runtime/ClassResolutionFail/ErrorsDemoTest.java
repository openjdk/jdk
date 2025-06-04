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

/*
* @test 8289164
* @summary Test that tests the ResolutionErrorTable
*/

import java.io.File;
import java.io.*;


public class ErrorsDemoTest {
    static int x = 0;

    public static void main(String args[]) {
        String classDirectory = System.getProperty("test.classes");
        String filename = classDirectory + File.separator + "DeleteMe.class";
        File file = new File(filename);
        boolean success = file.delete();
        String oldMessage = null;

        for (int i = 0; i < 2; i++) {
            try {
                ErrorInResolution.doit();
            }
            catch (Throwable t) {
                String s = t.getMessage();
                if (oldMessage == null){
                oldMessage = s;
                }
                else {
                    if(!s.equals(oldMessage)){
                        RuntimeException e = new RuntimeException();
                        throw e;
                    }
                }
            }
        }
    }
}

class DeleteMe {
    static int x;
}

class ErrorInResolution {
    static int doit() {
        return DeleteMe.x;
    }
}
