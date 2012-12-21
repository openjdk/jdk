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
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.AlgorithmParameters;

import java.util.Random;
import java.util.Arrays;

abstract public class TestAESBase {
  int msgSize = Integer.getInteger("msgSize", 646);
  boolean checkOutput = Boolean.getBoolean("checkOutput");
  boolean noReinit = Boolean.getBoolean("noReinit");
  int keySize = Integer.getInteger("keySize", 128);
  String algorithm = System.getProperty("algorithm", "AES");
  String mode = System.getProperty("mode", "CBC");
  byte[] input;
  byte[] encode;
  byte[] expectedEncode;
  byte[] decode;
  byte[] expectedDecode;
  Random random = new Random(0);
  Cipher cipher;
  Cipher dCipher;
  String paddingStr = "PKCS5Padding";
  AlgorithmParameters algParams;
  SecretKey key;

  static int numThreads = 0;
  int  threadId;
  static synchronized int getThreadId() {
    int id = numThreads;
    numThreads++;
    return id;
  }

  abstract public void run();

  public void prepare() {
    try {
    System.out.println("\nalgorithm=" + algorithm + ", mode=" + mode + ", msgSize=" + msgSize + ", keySize=" + keySize + ", noReinit=" + noReinit + ", checkOutput=" + checkOutput);

      int keyLenBytes = (keySize == 0 ? 16 : keySize/8);
      byte keyBytes[] = new byte[keyLenBytes];
      if (keySize == 128)
        keyBytes = new byte[] {-8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7};
      else
        random.nextBytes(keyBytes);

      key = new SecretKeySpec(keyBytes, algorithm);
      if (threadId == 0) {
        System.out.println("Algorithm: " + key.getAlgorithm() + "("
                           + key.getEncoded().length * 8 + "bit)");
      }
      input = new byte[msgSize];
      for (int i=0; i<input.length; i++) {
        input[i] = (byte) (i & 0xff);
      }

      cipher = Cipher.getInstance(algorithm + "/" + mode + "/" + paddingStr, "SunJCE");
      dCipher = Cipher.getInstance(algorithm + "/" + mode + "/" + paddingStr, "SunJCE");

      if (mode.equals("CBC")) {
        int ivLen = (algorithm.equals("AES") ? 16 : algorithm.equals("DES") ? 8 : 0);
        IvParameterSpec initVector = new IvParameterSpec(new byte[ivLen]);
        cipher.init(Cipher.ENCRYPT_MODE, key, initVector);
      } else {
        algParams = cipher.getParameters();
        cipher.init(Cipher.ENCRYPT_MODE, key, algParams);
      }
      algParams = cipher.getParameters();
      dCipher.init(Cipher.DECRYPT_MODE, key, algParams);
      if (threadId == 0) {
        childShowCipher();
      }

      // do one encode and decode in preparation
      // this will also create the encode buffer and decode buffer
      encode = cipher.doFinal(input);
      decode = dCipher.doFinal(encode);
      if (checkOutput) {
        expectedEncode = (byte[]) encode.clone();
        expectedDecode = (byte[]) decode.clone();
        showArray(key.getEncoded()  ,  "key:    ");
        showArray(input,  "input:  ");
        showArray(encode, "encode: ");
        showArray(decode, "decode: ");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  void showArray(byte b[], String name) {
    System.out.format("%s [%d]: ", name, b.length);
    for (int i=0; i<Math.min(b.length, 32); i++) {
      System.out.format("%02x ", b[i] & 0xff);
    }
    System.out.println();
  }

  void compareArrays(byte b[], byte exp[]) {
    if (b.length != exp.length) {
      System.out.format("different lengths for actual and expected output arrays\n");
      showArray(b, "test: ");
      showArray(exp, "exp : ");
      System.exit(1);
    }
    for (int i=0; i< exp.length; i++) {
      if (b[i] != exp[i]) {
        System.out.format("output error at index %d: got %02x, expected %02x\n", i, b[i] & 0xff, exp[i] & 0xff);
        showArray(b, "test: ");
        showArray(exp, "exp : ");
        System.exit(1);
      }
    }
  }


  void showCipher(Cipher c, String kind) {
    System.out.println(kind + " cipher provider: " + cipher.getProvider());
    System.out.println(kind + " cipher algorithm: " + cipher.getAlgorithm());
  }

  abstract void childShowCipher();
}
