/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4694497
 * @summary BDoclet API: Bad ClassDoc for nested classes when built from .class files
 * @author gafter
 * @compile NestedClass.java NestedClassB.java
 * @run main NestedClass
 */

import com.sun.javadoc.*;
import java.util.*;

public class NestedClass extends Doclet
{
    public NestedClassB b;

    public static void main(String[] args) {
        if (com.sun.tools.javadoc.Main.
            execute("javadoc", "NestedClass", NestedClass.class.getClassLoader(),
                    new String[] {System.getProperty("test.src", ".") +
                                  java.io.File.separatorChar +
                                  "NestedClass.java"})
            != 0)
            throw new Error();
    }

    public static boolean start(com.sun.javadoc.RootDoc root) {
        ClassDoc[] classes = root.classes();
        if (classes.length != 1)
            throw new Error("1 " + Arrays.asList(classes));
        ClassDoc self = classes[0];
        FieldDoc B = self.fields()[0];
        ClassDoc[] Binner = B.type().asClassDoc().innerClasses();
        return Binner.length == 1;
    }
}
