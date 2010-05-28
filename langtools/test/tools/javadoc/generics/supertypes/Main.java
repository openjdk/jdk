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
 * @bug 4922918
 * @summary Check supertypes and superinterfaces of parameterized types.
 * @library ../../lib
 * @compile  ../../lib/Tester.java Main.java
 * @run main Main
 */

import java.io.IOException;
import java.util.Comparator;
import java.util.Arrays;
import com.sun.javadoc.*;

public class Main extends Tester.Doclet {

    private static final Tester tester = new Tester("Main", "pkg1");

    public static void main(String[] args) throws IOException {
        tester.run();
        tester.verify();
    }

    public static boolean start(RootDoc root) {
        try {
            ClassDoc[] cds = root.classes();
            Arrays.sort(cds);
            for (ClassDoc cd : cds) {
                ParameterizedType arrayList =
                    cd.superclassType().asParameterizedType();
                tester.println(arrayList);
                tester.println();

                tester.println(arrayList.superclassType());
                Type[] interfaces = arrayList.interfaceTypes();
                // Sort interfaces by type name, for consistent output.
                Arrays.sort(interfaces,
                            new Comparator<Type>() {
                                    public int compare(Type t1, Type t2) {
                                        String name1 = t1.qualifiedTypeName();
                                        String name2 = t2.qualifiedTypeName();
                                        return name1.compareTo(name2);
                                    }
                                });
                for (Type t : interfaces) {
                    tester.println(t);
                }
                tester.println();
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
