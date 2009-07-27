/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 5031168
 * @summary Test package annotations and package-info.java package comments.
 * @library ../../lib
 * @compile  ../../lib/Tester.java Main.java
 * @run main Main
 */

import java.io.*;
import com.sun.javadoc.*;

public class Main extends Tester.Doclet {

    private static final Tester tester = new Tester("Main", "pkg1", "pkg2");

    public static void main(String[] args) throws IOException {
        tester.run();
        tester.verify();
    }

    public static boolean start(RootDoc root) {
        try {
            for (PackageDoc pkg : root.specifiedPackages()) {
                tester.println("/**");
                tester.println(pkg.commentText());
                for (Tag tag : pkg.tags())
                    tester.println(tag);
                tester.println("*/");

                tester.printPackage(pkg);
                tester.println();
            }
            for (ClassDoc cd : root.classes()) {
                tester.println(cd);
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
