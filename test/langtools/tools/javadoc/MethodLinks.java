/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4628281
 * @summary Int. links missing from return/param types when .java files passd in
 * @author gafter
 * @modules jdk.javadoc
 * @run main MethodLinks
 */

import com.sun.javadoc.*;
import java.util.*;

public class MethodLinks extends Doclet
{
    public static void main(String[] args) {
        if (com.sun.tools.javadoc.Main.
            execute("javadoc", "MethodLinks", MethodLinks.class.getClassLoader(),
                    new String[] {System.getProperty("test.src", ".") +
                                  java.io.File.separatorChar + "MethodLinks.java"}
                    ) != 0)
            throw new Error();
    }

    /** The parameter type and return type should link to the current
     *  class. */
    public MethodLinks SAMPLE(MethodLinks x) {
        return x;
    }

    public static boolean start(com.sun.javadoc.RootDoc root) {
        ClassDoc[] classes = root.classes();
        if (classes.length != 1)
            throw new Error("1 " + Arrays.asList(classes));
        ClassDoc self = classes[0];
        MethodDoc[] allMethods = self.methods();
        MethodDoc SAMPLE = null;
        for (int i=0; i<allMethods.length; i++)
            if (allMethods[i].name().equals("SAMPLE"))
                SAMPLE = allMethods[i];
        return
            self == SAMPLE.parameters()[0].type().asClassDoc()
            &&
            self == SAMPLE.returnType().asClassDoc()
            ;
    }
}
