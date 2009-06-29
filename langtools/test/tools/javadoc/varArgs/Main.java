/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4421066 5006659
 * @summary Verify the contents of a ClassDoc containing a varArgs method.
 *          Verify that see/link tags can use "..." notation.
 * @library ../lib
 * @compile  ../lib/Tester.java Main.java
 * @run main Main
 */

import java.io.IOException;
import com.sun.javadoc.*;

public class Main extends Tester.Doclet {

    private static final Tester tester =
            new Tester("Main", "-Xwerror", "pkg1");

    public static void main(String[] args) throws IOException {
        tester.run();
        tester.verify();
    }

    public static boolean start(RootDoc root) {
        try {
            for (ClassDoc cd : root.classes()) {
                tester.printClass(cd);

                for (SeeTag tag : cd.seeTags()) {
                    if (tag.referencedMember() != cd.methods()[0]) {
                        throw new Error("5006659: @see tag meets varArgs");
                    }
                }
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
