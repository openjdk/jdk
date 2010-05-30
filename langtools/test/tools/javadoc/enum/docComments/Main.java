/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4421066
 * @summary Verify the comments in an enum type.
 * @library ../../lib
 * @compile  ../../lib/Tester.java Main.java
 * @run main Main
 */

import java.io.IOException;
import com.sun.javadoc.*;

public class Main extends Tester.Doclet {

    private static final Tester tester =
            new Tester("Main", "-package", "pkg1");

    public static void main(String[] args) throws IOException {
        tester.run();
    }

    public static boolean start(RootDoc root) {
        ClassDoc operation = root.classes()[0];
        boolean ok =
            checkComment(operation.commentText(), "Arithmetic operations.");

        for (FieldDoc f : operation.fields()) {
            if (f.name().equals("plus")) {
                ok = checkComment(f.commentText(), "Addition") && ok;
                for (MethodDoc m : operation.methods()) {
                    if (m.name().equals("eval")) {
                        ok = checkComment(m.commentText(),
                                          "Perform arithmetic operation " +
                                          "represented by this constant.") &&
                            ok;
                        break;
                    }
                }
                break;
            }
        }
        if (!ok) {
            throw new Error("Comments don't match expectations.");
        } else {
            return true;
        }
    }

    private static boolean checkComment(String found, String expected) {
        System.out.println("expected: \"" + expected + "\"");
        System.out.println("found:    \"" + found + "\"");
        return expected.equals(found);
    }
}
