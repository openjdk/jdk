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
 * @bug 4587562
 * @summary tool: Indentation messed up for javadoc comments omitting preceding *
 * @author gafter
 * @run main NoStar
 */

import com.sun.javadoc.*;
import java.util.*;

/** First sentence.
0
 1
  2
   3
    4
     5
*/
public class NoStar extends Doclet
{
    public static void main(String[] args) {
        if (com.sun.tools.javadoc.Main.
            execute("javadoc", "NoStar", NoStar.class.getClassLoader(),
                    new String[] {System.getProperty("test.src", ".") + java.io.File.separatorChar + "NoStar.java"}) != 0)
            throw new Error();
    }

    public static boolean start(com.sun.javadoc.RootDoc root) {
        ClassDoc[] classes = root.classes();
        if (classes.length != 1)
            throw new Error("1 " + Arrays.asList(classes));
        ClassDoc self = classes[0];
        String c = self.commentText();
        System.out.println("\"" + c + "\"");
        return c.equals("First sentence.\n0\n 1\n  2\n   3\n    4\n     5");
    }
}
