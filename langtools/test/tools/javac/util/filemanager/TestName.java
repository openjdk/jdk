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
 * @bug     6409829
 * @summary JSR 199: enforce the use of valid package and class names
 *                   in get{Java,}FileFor{Input,Output}
 * @author  Peter von der Ah\u00e9
 */

import com.sun.tools.javac.util.JavacFileManager;

public class TestName {
    public static void main(String... args) {
        final boolean PACKAGE = true;
        final boolean CLASS = true;
        JavacFileManager.testName("", PACKAGE, !CLASS);
        JavacFileManager.testName(".", !PACKAGE, !CLASS);
        JavacFileManager.testName("java.lang.", !PACKAGE, !CLASS);
        JavacFileManager.testName(".java.lang.", !PACKAGE, !CLASS);
        JavacFileManager.testName(".java.lang", !PACKAGE, !CLASS);
        JavacFileManager.testName("java.lang", PACKAGE, CLASS);
        JavacFileManager.testName("java.lang.Foo Bar", !PACKAGE, !CLASS);
        JavacFileManager.testName("java.lang.Foo+Bar", !PACKAGE, !CLASS);
        JavacFileManager.testName("java.lang.Foo$Bar", PACKAGE, CLASS);
        JavacFileManager.testName("Peter.von.der.Ah\u00e9", PACKAGE, CLASS);
    }
}
