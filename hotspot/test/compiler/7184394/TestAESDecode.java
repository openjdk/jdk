/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @author Tom Deneau
 */

import javax.crypto.Cipher;

public class TestAESDecode extends TestAESBase {
  @Override
  public void run() {
    try {
      if (!noReinit) dCipher.init(Cipher.DECRYPT_MODE, key, algParams);
      if (checkOutput) {
        // checked version creates new output buffer each time
        decode = dCipher.doFinal(encode, 0, encode.length);
        compareArrays(decode, expectedDecode);
      } else {
        // non-checked version outputs to existing encode buffer for maximum speed
        decode = new byte[dCipher.getOutputSize(encode.length)];
        dCipher.doFinal(encode, 0, encode.length, decode);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  @Override
  void childShowCipher() {
    showCipher(dCipher, "Decryption");
  }

}
