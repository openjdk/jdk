/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;

/*
 * @test
 * @bug 6863746
 * @summary javap should not scan ct.sym by default
 */

public class T6863746 {
    public static void main(String... args) throws Exception{
        new T6863746().run();
    }

    public void run() throws Exception {
        String[] args = { "-c", "java.lang.Object" };
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javap.Main.run(args, pw);
        pw.close();
        String out = sw.toString();
        System.out.println(out);
        String[] lines = out.split("\n");
        // If ct.sym is being read, the output does not include
        // Code attributes, so check for Code attributes as a
        // way of detecting that ct.sym is not being used.
        if (lines.length < 50 || out.indexOf("Code:") == -1)
            throw new Exception("unexpected output from javap");
    }
}
