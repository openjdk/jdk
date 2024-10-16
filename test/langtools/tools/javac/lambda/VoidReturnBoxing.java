/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8336491
 * @summary Verify that void returning expression lambdas don't box their result
 * @modules jdk.compiler
 *          jdk.jdeps/com.sun.tools.javap
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

public class VoidReturnBoxing {

    public static void main(String[] args) {
        new VoidReturnBoxing().run();
    }

    void run() {
        Path path = Path.of(System.getProperty("test.classes"), "T.class");
        StringWriter s;
        String out;
        try (PrintWriter pw = new PrintWriter(s = new StringWriter())) {
            com.sun.tools.javap.Main.run(new String[] {"-p", "-c", path.toString()}, pw);
            out = s.toString();
        }
        if (out.contains("java/lang/Integer.valueOf")) {
            throw new AssertionError(
                    "Unnecessary boxing of void returning expression lambda result:\n\n" + out);
        }
    }
}

class T {
    int g() {
        return 0;
    }

    Runnable r = () -> g();
}
