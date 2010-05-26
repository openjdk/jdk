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
 * @bug 4587494
 * @summary Constant field values for boolean Data-Types don't use true and false
 * @author gafter
 * @run main BooleanConst
 */

import com.sun.javadoc.*;
import java.util.*;

public class BooleanConst extends Doclet
{
    public static void main(String[] args) {
        // run javadoc on package p
        if (com.sun.tools.javadoc.Main.
            execute("javadoc", "BooleanConst", BooleanConst.class.getClassLoader(),
                    new String[] {System.getProperty("test.src", ".") + java.io.File.separatorChar + "BooleanConst.java"}) != 0)
            throw new Error();
    }

    public static final boolean b1 = false;
    public static final boolean b2 = true;

    public static boolean start(com.sun.javadoc.RootDoc root) {
        ClassDoc[] classes = root.classes();
        if (classes.length != 1)
            throw new Error("1 " + Arrays.asList(classes));
        ClassDoc self = classes[0];
        FieldDoc[] fields = self.fields();
        if (fields.length != 2)
            throw new Error("2 " + Arrays.asList(fields));
        for (int i=0; i<fields.length; i++) {
            FieldDoc f = fields[i];
            if (f.name().equals("b1")) {
                Object value = f.constantValue();
                if (value == null || !(value instanceof Boolean) || ((Boolean)value).booleanValue())
                    throw new Error("4 " + value);
            } else if (f.name().equals("b2")) {
                Object value = f.constantValue();
                if (value == null || !(value instanceof Boolean) || !((Boolean)value).booleanValue())
                    throw new Error("5 " + value);
            } else throw new Error("3 " + f.name());
        }
        return true;
    }
}
