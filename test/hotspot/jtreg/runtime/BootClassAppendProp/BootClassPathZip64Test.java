/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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
 * @bug 8334048
 * @summary -Xbootclasspath can not read some ZIP64 ZIP files
 *
 * The BootClassPathZip64Creator driver outputs a minimal reproducer
 * ZIP file which was generated with the following sequence of POSIX
 * shell commands:
 *
 *    $ javac -version
 *    javac 1.8.0_402
 *    $ zip -v | grep "This is"
 *    This is Zip 3.0 (July 5th 2008), by Info-ZIP.
 *    $ echo -n "class T{}" > T.java
 *    $ javac -g:none T.java
 *    $ echo -n | zip -Zstore Zip64 - T.class
 *      adding: - (stored 0%)
 *      adding: T.class (stored 0%)
 *    $ md5sum Zip64.zip
 *    45fe0ab09482d6bce7e2e903c16af9d6  Zip64.zip
 *
 * The class file is included in the ZIP file without compression so
 * that it is easily identifiable in the hexadecimal listing.
 *
 * @run driver BootClassPathZip64Creator
 * @run main/othervm -Xbootclasspath/a:./Zip64.zip BootClassPathZip64Test
 */

public class BootClassPathZip64Test {

    public static void main(String[] args) throws Exception {
        ClassLoader loader = BootClassPathZip64Test.class.getClassLoader();
        Class c = loader.loadClass("T");
    }

}
