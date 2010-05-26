/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     4994049
 * @summary Unit test for SourcePosition.column with respect to tab expansion
 * @author  Peter von der Ah\u00e9
 * @run main T4994049 FileWithTabs.java
 */

import com.sun.javadoc.*;
import java.io.File;
import static com.sun.tools.javadoc.Main.execute;

public class T4994049 extends Doclet {

    public static boolean start(RootDoc root) {
        for (ClassDoc klass : root.classes()) {
            for (MethodDoc method : klass.methods()) {
                if (method.name().equals("tabbedMethod")) {
                    if (method.position().column() == 21) {
                        System.out.println(method.position().column() + ": OK!");
                        return true;
                    } else {
                        System.err.println(method.position() + ": wrong tab expansion");
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public static void main(String... args) {
        for (String file : args) {
            File source = new File(System.getProperty("test.src", "."), file);
            if (execute("javadoc", "T4994049", T4994049.class.getClassLoader(),
                        new String[]{source.getPath()} ) != 0)
                throw new Error();
        }
    }

}
