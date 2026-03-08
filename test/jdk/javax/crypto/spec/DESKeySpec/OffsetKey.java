/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8364121
 * @summary DESKeySpec.isWeak should throw aiobe exception if the offset is
 * negative.
 */
import java.security.InvalidKeyException;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.DESKeySpec;

public class OffsetKey {

    public static void main(String[] args) throws Exception {
        byte[] strongKey = {
                (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78,
                (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0,
                (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78,
                (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0,
                (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78,
                (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0
        };

        // Test single-DES
        try {
            DESKeySpec desKey = new DESKeySpec(strongKey, -1);
            throw new Exception("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            boolean weak = DESKeySpec.isWeak(strongKey, -1);
            throw new Exception("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try{
            boolean parityAdjusted = DESKeySpec.isParityAdjusted(strongKey, -1);
            throw new Exception("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}

        // Test triple-DES
        try{
            DESedeKeySpec desEdeKey = new DESedeKeySpec(strongKey, -1);
            throw new Exception("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try{
            boolean parityAdjusted = DESedeKeySpec.isParityAdjusted(strongKey,
                    -1);
            throw new Exception("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
    }

}
