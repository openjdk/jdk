
/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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
import java.nio.file.StandardOpenOption;

import jdk.test.lib.Asserts;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.whitebox.WhiteBox;


public class LingeredAppWithValueObject extends LingeredApp {

    public static final Path ADDR_FILE_PATH = Path.of(System.getProperty("java.io.tmpdir"), "LingeredAppWithValueObject-address.txt");

    public static value record Rec(byte recA, byte recB){};

    public static value class ValueObj {

        public final byte a;
        public final byte b;
        public final Rec  rec;
        public final byte c;

        public ValueObj(byte a, byte b, byte c, byte recA, byte recB) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.rec = new Rec(recA, recB);
        }
    }

    private static ValueObj valObj;

    public static void main(String[] args) {
        valObj = new ValueObj((byte)1, (byte)2, (byte)3, (byte)10, (byte)20);
        WhiteBox wb = WhiteBox.getWhiteBox();
        long addr = wb.getObjectAddress(valObj);
        String addrInHex = String.format("0x%x", addr);
        IO.println("valObj address = " + addrInHex);

        try {
            Files.writeString(ADDR_FILE_PATH, addrInHex, StandardOpenOption.CREATE_NEW);
            ADDR_FILE_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            Asserts.fail("Unexpected exception happened", e);
        }

        LingeredApp.main(args);
    }
}
