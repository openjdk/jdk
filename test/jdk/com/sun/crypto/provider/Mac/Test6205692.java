/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @bug 6205692
 * @summary verify MacSpi NPE on engineUpdate(ByteBuffer)
 */

import jdk.test.lib.Utils;

import javax.crypto.MacSpi;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

public class Test6205692 {

    public boolean execute() throws Exception {

        ByteBuffer byteBuffer = null;

        MyMacSpi myMacSpi = new MyMacSpi();

        Utils.runAndCheckException(() -> myMacSpi.engineUpdate(byteBuffer),
                NullPointerException.class);

        return true;
    }

    public static void main(String[] args) throws Exception {
        Test6205692 test = new Test6205692();

        if (test.execute()) {
            System.out.println(test.getClass().getName() + ": passed!");
        }
    }

    private static class MyMacSpi extends MacSpi {

        /*
         * This is the important part; the rest is blank mandatory overrides
         */
        public void engineUpdate(ByteBuffer input) {
            super.engineUpdate(input);
        }

        @Override
        protected int engineGetMacLength() {
            return 0;
        }

        @Override
        protected void engineInit(Key key, AlgorithmParameterSpec params)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
        }

        @Override
        protected void engineUpdate(byte input) {
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {
        }

        @Override
        protected byte[] engineDoFinal() {
            return new byte[0];
        }

        @Override
        protected void engineReset() {
        }
    }
}
