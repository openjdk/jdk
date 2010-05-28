/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6413876
 * @summary REGRESSION javac -d /directory/ creates destination directories
 */

import java.io.*;
import javax.tools.*;

public class T6413876 {
    public static void main(String... args) {
        test("-d");
        test("-s");
    }

    private static void test(String outOpt) {
        File testSrc = new File(System.getProperty("test.src", "."));
        File file = new File(testSrc, T6413876.class.getName()+".java");
        Tool t = ToolProvider.getSystemJavaCompiler();
        int rc = t.run(null, null, null, outOpt, "NotADir", file.getPath());
        if (rc == 0)
            throw new AssertionError("compilation succeeded unexpectedly");
    }
}
