/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8339280
 * @summary Test jarsigner -verify on a jar with an entry which size is > 64k,
 *     so its size (or csize) is stored in the extra field and/or data descriptor.
 * @library /test/lib
 * @run main VerifyCompressedLargeFile
 */

import java.nio.file.Path;
import jdk.test.lib.SecurityTools;

public class VerifyCompressedLargeFile {

    public static void main(String[] args) throws Exception {

        /**
         * largefile.zip created by:
         * - dd if=/dev/zero bs=1K count=100 of=largefile.txt
         * - zip -fz largefile.zip largefile.txt
         */
        String largeZip = Path.of(System.getProperty("test.src"), "largefile.zip").toString();

        SecurityTools.keytool("-genkeypair -keystore ks -storepass changeit " +
                        "-keyalg rsa -alias mykey -dname CN=me")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-keystore ks -storepass changeit -digestalg SHA256 " +
                        largeZip + " mykey")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-verify -verbose " + largeZip)
                .shouldMatch("102400 .*largefile.txt")
                .shouldHaveExitValue(0);
    }
}
