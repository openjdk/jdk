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
 * @bug 4904495 5008037
 * @summary Test annotations of methods, fields, enum constants, and
 *          annotation type elements.
 *          Test an annotation type with a type nested within.
 * @library ../../lib
 * @compile  ../../lib/Tester.java Main.java
 * @run main Main
 */

import java.io.IOException;
import com.sun.javadoc.*;

public class Main extends Tester.Doclet {

    private static final Tester tester = new Tester("Main", "pkg1");

    public static void main(String[] args) throws IOException {
        tester.run();
        tester.verify();
    }

    public static boolean start(RootDoc root) {
        try {
            for (PackageDoc p : root.specifiedPackages()) {
                for (AnnotationTypeDoc a : p.annotationTypes()) {
                    for (AnnotationTypeElementDoc e : a.elements()) {
                        tester.printAnnotationTypeElement(e);
                    }
                    tester.println();
                }
                for (ClassDoc e : p.enums()) {
                    for (FieldDoc ec : e.enumConstants()) {
                        tester.printField(ec);
                    }
                    tester.println();
                }
                for (ClassDoc cd : p.ordinaryClasses()) {
                    for (FieldDoc f : cd.fields()) {
                        tester.printField(f);
                    }
                    tester.println();
                    for (MethodDoc m : cd.methods()) {
                        tester.printMethod(m);
                    }
                    tester.println();
                }
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
