
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
import java.util.Properties;

import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.whitebox.WhiteBox;


public class LingeredAppWithValueObject extends LingeredApp {

    public static final Path ADDR_FILE_PATH = Path.of(System.getProperty("java.io.tmpdir"), "LingeredAppWithValueObject-addresses.properties");

    public static value record Rec(byte recA, byte recB){};

    public static value class ValueObj {

        public final byte a;
        public final Rec  rec;
        public final Rec  nullField;

        public ValueObj(byte a, byte recA, byte recB) {
            this.a = a;
            this.rec = new Rec(recA, recB);
            this.nullField = null;
        }
    }

    public static value class NonNullValueObj {

        public final byte a;
        public final Rec  rec;

        public NonNullValueObj(byte a, byte recA, byte recB) {
            this.a = a;
            this.rec = new Rec(recA, recB);
        }
    }

    public static value class NonFlattenedValueObj {

        public final int a;
        public final Object obj;

        public NonFlattenedValueObj(int a, Object obj) {
            this.a = a;
            this.obj = obj;
        }
    }

    private static ValueObj valObj;

    @NullRestricted
    private static NonNullValueObj nonNullValObj;

    private static NonFlattenedValueObj nonFlattenedValObj;

    private static ValueObj[] valObjArray;

    private static NonFlattenedValueObj[] nonFlattenedValObjArray;

    static {
        valObj = new ValueObj((byte)1, (byte)10, (byte)20);
        nonNullValObj = new NonNullValueObj((byte)2, (byte)30, (byte)40);
        nonFlattenedValObj = new NonFlattenedValueObj(100, new Object());

        valObjArray = new ValueObj[] {
          new ValueObj((byte)1, (byte)10, (byte)20),
          new ValueObj((byte)2, (byte)30, (byte)40)
        };
        nonFlattenedValObjArray = new NonFlattenedValueObj[] {
          new NonFlattenedValueObj(100, new Object()),
          new NonFlattenedValueObj(200, new Object())
        };
    }

    public static void main(String[] args) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        Properties addresses = new Properties();
        addresses.setProperty("valObj", String.format("0x%x", wb.getObjectAddress(valObj)));
        addresses.setProperty("nonNullValObj", String.format("0x%x", wb.getObjectAddress(nonNullValObj)));
        addresses.setProperty("nonFlattenedValObj", String.format("0x%x", wb.getObjectAddress(nonFlattenedValObj)));
        addresses.setProperty("valObjArray", String.format("0x%x", wb.getObjectAddress(valObjArray)));
        addresses.setProperty("nonFlattenedValObjArray", String.format("0x%x", wb.getObjectAddress(nonFlattenedValObjArray)));

        try (var out = Files.newOutputStream(ADDR_FILE_PATH)) {
            addresses.store(out, null);
            ADDR_FILE_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            Asserts.fail("Unexpected exception happened", e);
        }

        LingeredApp.main(args);
    }
}
