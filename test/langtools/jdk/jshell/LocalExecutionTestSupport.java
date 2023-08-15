/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;

/*
 * This class installs a class in a temporary diretory so we can test
 * finding classes that are not visible to the system class loader.
 */
public class LocalExecutionTestSupport extends ReplToolTesting {

    /*
     * This is a classfile corresponding to this source:
     *
     *      package test;
     *      public class MyClass {
     *          public static final String FOO = "bar";
     *      }
     */
    private static final String MYCLASS =
          "cafebabe0000003400120a0003000e07"
        + "000f070010010003464f4f0100124c6a"
        + "6176612f6c616e672f537472696e673b"
        + "01000d436f6e7374616e7456616c7565"
        + "0800110100063c696e69743e01000328"
        + "2956010004436f646501000f4c696e65"
        + "4e756d6265725461626c6501000a536f"
        + "7572636546696c6501000c4d79436c61"
        + "73732e6a6176610c0008000901000c74"
        + "6573742f4d79436c6173730100106a61"
        + "76612f6c616e672f4f626a6563740100"
        + "03626172002100020003000000010019"
        + "00040005000100060000000200070001"
        + "0001000800090001000a0000001d0001"
        + "0001000000052ab70001b10000000100"
        + "0b000000060001000000020001000c00"
        + "000002000d";

    // The classes directory containing "test/MyClass.class"
    protected Path classesDir;

    // Install file "test/MyClass.class" in some temporary directory somewhere
    @BeforeTest
    public void installMyClass() throws IOException {
        classesDir = Files.createTempDirectory(getClass().getSimpleName()).toAbsolutePath();
        Path testPkgDir = classesDir.resolve("test");
        Path myclassFile = testPkgDir.resolve("MyClass.class");
        Files.createDirectory(testPkgDir);
        Files.write(myclassFile, string2bytes(MYCLASS));
    }

    protected String[] prependArgs(String[] args, String... prepends) {
        String[] newArgs = new String[prepends.length + args.length];
        System.arraycopy(prepends, 0, newArgs, 0, prepends.length);
        System.arraycopy(args, 0, newArgs, prepends.length, args.length);
        return newArgs;
    }

    protected byte[] string2bytes(String string) {
        byte[] buf = new byte[string.length() / 2];
        for (int i = 0; i < string.length(); i += 2) {
            int value = Integer.parseInt(string.substring(i, i + 2), 16);
            buf[i / 2] = (byte)value;
        }
        return buf;
    }
}
