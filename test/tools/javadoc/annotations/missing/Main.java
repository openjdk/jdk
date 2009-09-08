/*
 * Copyright 2009 Google, Inc.  All Rights Reserved.
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
 * @bug 6709246
 * @summary Class-cast exception when annotation type is missing.
 * @library ../../lib
 */

import java.io.IOException;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.AnnotationDesc;

public class Main extends Tester.Doclet {

    private static final Tester tester = new Tester("Main", "somepackage");

    public static void main(String... args) throws Exception {
        tester.run();
    }

    public static boolean start(RootDoc root) {
        for (ClassDoc d : root.classes()) {
            for (AnnotationDesc a : d.annotations()) {
                System.out.println(a.annotationType());
            }
        }
        return true;
    }
}
