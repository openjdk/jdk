/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug     6411310
 * @summary JSR 199: FileObject should support user-friendly names via getName()
 * @author  Peter von der Ah\u00e9
 * @library ../lib
 * @compile T6411310.java
 * @run main T6411310
 */

import java.io.IOException;
import javax.tools.JavaFileObject;
import static javax.tools.StandardLocation.PLATFORM_CLASS_PATH;
import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.JavaFileObject.Kind.CLASS;

// Limited test while we wait for 6419926: 6419926 is now closed

public class T6411310 extends ToolTester {

    void test(String... args) throws IOException {
        JavaFileObject file = fm.getJavaFileForInput(PLATFORM_CLASS_PATH,
                                                     "java.lang.Object",
                                                     CLASS);
        String fileName = file.getName();
        if (!fileName.matches(".*java/lang/Object.class\\)?")) {
            System.err.println(fileName);
            throw new AssertionError(file);
        }
    }

    public static void main(String... args) throws IOException {
        new T6411310().test(args);
    }
}
