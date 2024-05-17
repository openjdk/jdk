/*
 * Copyright (c) 2000, 2024 Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4162796 4162796
 * @summary Test indexOf and lastIndexOf
 * @key randomness
 */

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class IndexOf {

  static Random generator = new Random();
  private static boolean failure = false;
  public static void main(String[] args) throws Exception {
    String testName = "IndexOf";

  // ///////////////////////////////////////////////////////////////////////////////////
  // ///////////////////////////////////////////////////////////////////////////////////
  char[] haystack = new char[128];
  char[] haystack_16 = new char[128];

  for (int i = 0; i < 128; i++) {
    haystack[i] = (char) i;
  }

  // haystack_16[0] = (char) (23 + 256);
  // for (int i = 1; i < 128; i++) {
  //   haystack_16[i] = (char) (i);
  // }

  for (int i = 0; i < 128; i++) {
    haystack_16[i] = (char) (i + 256);
  }


  // Charset hs_charset = StandardCharsets.ISO_8859_1;
  Charset hs_charset = StandardCharsets.UTF_16;
  Charset needleCharset = StandardCharsets.ISO_8859_1;
  // Charset needleCharset = StandardCharsets.UTF_16;
  int l_offset = 0;
  int needleSize = 65;
  int haystackSize = 66;
  int result = 0;

  String needle = new String(Arrays.copyOfRange((needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack, l_offset, l_offset + needleSize));
  int hsSize = (haystackSize - l_offset) >= 0 ? haystackSize - l_offset : 0;
  int midStart = hsSize / 2;
  int endStart = (hsSize > needleSize) ? hsSize - needleSize : 0;
  String midNeedle = new String(
      Arrays.copyOfRange((needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack, midStart + l_offset, midStart + needleSize + l_offset));
  String endNeedle = new String(
      Arrays.copyOfRange((needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack, endStart + l_offset, endStart + needleSize + l_offset));
  // String shs = new String(Arrays.copyOfRange((hs_charset == StandardCharsets.UTF_16) ? haystack_16 : haystack, 0, haystackSize));
  String shs = (new String((hs_charset == StandardCharsets.UTF_16) ? haystack_16 : haystack)).substring(0, haystackSize);

  shs = "$&),,18+-!'8)+";
  endNeedle = "8)-";
  l_offset = 9;
  endNeedle = "/'!(\"3//";
  shs = ");:(/!-+ %*/'!(\"3//;9";
  l_offset = 0;
  StringBuffer bshs = new StringBuffer(shs);

  // printStringBytes(shs.getBytes(hs_charset));
  for (int i = 0; i < 200000; i++) {
    if(shs.indexOf(endNeedle, l_offset) != -1) {
      // System.out.println("result="+bshs.indexOf(endNeedle, l_offset));
    }
    result += bshs.indexOf(endNeedle, l_offset);
    // System.out.print(result + " " + needle + " " + shs);
  }

  ///////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////


    for (int i = 0; i < 20000; i++) {
      int foo = testName.indexOf("dex");
    }
    System.out.println("");
    simpleTest();
    compareIndexOfLastIndexOf();
    compareStringStringBuffer();
    compareExhaustive();

    if (failure)
      throw new RuntimeException("One or more BitSet failures.");
  }

  private static void report(String testName, int failCount) {
    System.err.println(testName + ": " +
        (failCount == 0 ? "Passed" : "Failed(" + failCount + ")"));
    if (failCount > 0)
      failure = true;
  }

  private static String generateTestString(int min, int max) {
    StringBuffer aNewString = new StringBuffer(120);
    int aNewLength = getRandomIndex(min, max);
    for (int y = 0; y < aNewLength; y++) {
      int achar = generator.nextInt(30) + 30;
      char test = (char) (achar);
      aNewString.append(test);
    }
    return aNewString.toString();
  }

  private static int getRandomIndex(int constraint1, int constraint2) {
    int range = constraint2 - constraint1;
    int x = generator.nextInt(range);
    return constraint1 + x;
  }

  private static int naiveFind(String haystack, String needle, int offset) {
    int x = offset;
    int len = haystack.length() - offset;
    if (needle.length() == 0)
      return offset;
    if (needle.length() > len)
      return -1;
    int hsndx = 0;
    int nndx = 0;
    for (int xx = 0; xx < offset; xx++) {
      hsndx += Character.charCount(haystack.codePointAt(hsndx));
    }
    // System.out.println("(1) hsndx=" + hsndx);
    for (x = offset; x < haystack.length() - needle.length() + 1; x++) {
      if (haystack.codePointAt(hsndx) == needle.codePointAt(0)) {
        nndx = Character.charCount(needle.codePointAt(0));
        int hsndx_tmp = hsndx + Character.charCount(haystack.codePointAt(hsndx));;
        // System.out.println("(2) hsndx_tmp=" + hsndx_tmp + " nndx=" + nndx);
        while (nndx < needle.length()) {
          if (haystack.codePointAt(hsndx_tmp) != needle.codePointAt(nndx)) {
            break;
          }
          hsndx_tmp += Character.charCount(haystack.codePointAt(hsndx_tmp));
          nndx += Character.charCount(needle.codePointAt(nndx));
        }
        if (nndx == needle.length()) {
          return x;
        }
      }
      hsndx += Character.charCount(haystack.codePointAt(hsndx));
    }
    return -1;
  }

  private static void compareExhaustive() {
    int failCount = 0;
    String sourceString;
    String targetString;
    int hsLen = 97;
    int maxNeedleLen = hsLen;// / 2;
    int haystackLen;
    int needleLen;
    int hsBegin, nBegin;

    for (int i = 0; i < 10000; i++) {
      do {
        sourceString = generateTestString(hsLen - 1, hsLen);
        targetString = generateTestString(maxNeedleLen - 1, maxNeedleLen);
      } while (naiveFind(sourceString, targetString, 0) != -1);

      for (haystackLen = 0; haystackLen < hsLen; haystackLen += 7) {
        for (needleLen = 0; (needleLen < maxNeedleLen) && (needleLen <= haystackLen); needleLen++) {
          for (hsBegin = 0; (hsBegin < haystackLen - needleLen) && (hsBegin + haystackLen < hsLen); hsBegin += 3) {
            for (nBegin = 0; (nBegin < needleLen) && (nBegin + needleLen < maxNeedleLen); nBegin += 3) {
              int nResult = naiveFind(sourceString.substring(hsBegin, hsBegin + haystackLen),
                                      targetString.substring(nBegin, nBegin + needleLen), 0);
              int iResult = sourceString.substring(hsBegin, hsBegin + haystackLen).indexOf(targetString.substring(nBegin, nBegin + needleLen));
              if (iResult != nResult) {
                System.out.println("Source="+sourceString.substring(hsBegin, hsBegin + haystackLen));
                System.out.println("Target="+targetString.substring(nBegin, nBegin + needleLen));
                System.out.println("haystackLen="+haystackLen+" neeldeLen="+needleLen+" hsBegin="+hsBegin+" nBegin="+nBegin+
                                   " iResult="+iResult+" nResult="+nResult);
                failCount++;
              }
            }
          }
        }
      }
    }

    report("Exhaustive                   ", failCount);
  }

  private static void simpleTest() {
    int failCount = 0;
    String sourceString;
    StringBuffer sourceBuffer;
    String targetString;
    String emptyString = "";
    String allAs = new String("aaaaaaaaaaaaaaaaaaaaaaaaa");
    StringBuffer allAsBuffer = new StringBuffer(allAs);

    for (int i = 0; i < 10000; i++) {
      do {
        sourceString = generateTestString(99, 100);
        sourceBuffer = new StringBuffer(sourceString);
        targetString = generateTestString(10, 11);
      } while (sourceString.indexOf(targetString) != -1);

      int index1 = generator.nextInt(90) + 5;
      sourceBuffer = sourceBuffer.replace(index1, index1, targetString);

      if ((sourceBuffer.indexOf(targetString) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + sourceBuffer.indexOf(targetString));
        failCount++;
      }
      if ((sourceBuffer.indexOf(targetString, 5) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString, 5) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + sourceBuffer.indexOf(targetString, 5));
        failCount++;
      }
      if ((sourceBuffer.indexOf(targetString, 99) == index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString, 99) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + sourceBuffer.indexOf(targetString, 99));
        failCount++;
      }
      if ((sourceBuffer.indexOf(emptyString, 99) != 99) ||
          (99 != naiveFind(sourceBuffer.toString(), emptyString, 99))) {
        System.err.println("sourceBuffer.indexOf(emptyString, 99) fragment '" + emptyString + "' ("
            + emptyString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), emptyString, 99) + ", IndexOf = "
            + sourceBuffer.indexOf(emptyString, 99));
        failCount++;
      }
      if ((allAsBuffer.substring(1, 3).indexOf(allAsBuffer.substring(5, 12)) != -1) ||
          (-1 != naiveFind(allAsBuffer.substring(1, 3).toString(), allAsBuffer.substring(5, 12), 0))) {
        System.err.println("allAsBuffer.substring(1, 3).indexOf(allAsBuffer.substring(5, 12)) fragment '"
            + allAsBuffer.substring(5, 12) + "' ("
            + allAsBuffer.substring(5, 12).length() + ") String = "
            + allAsBuffer.substring(1, 3) + " len Buffer = " + allAsBuffer.substring(1, 3).length());
        System.err.println(
            "  naive = " + naiveFind(allAsBuffer.substring(1, 3).toString(), allAsBuffer.substring(5, 12), 0)
                + ", IndexOf = " + allAsBuffer.substring(1, 3).indexOf(allAsBuffer.substring(5, 12)));
        failCount++;
      }
    }

    report("Basic Test                   ", failCount);
  }

  // Note: it is possible although highly improbable that failCount will
  // be > 0 even if everthing is working ok
  private static void compareIndexOfLastIndexOf() {
    int failCount = 0;
    String sourceString;
    StringBuffer sourceBuffer;
    String targetString;

    for (int i = 0; i < 10000; i++) {
      do {
        sourceString = generateTestString(99, 100);
        sourceBuffer = new StringBuffer(sourceString);
        targetString = generateTestString(10, 11);
      } while (sourceString.indexOf(targetString) != -1);

      int index1 = generator.nextInt(100);
      sourceBuffer = sourceBuffer.replace(index1, index1, targetString);

      // extremely remote possibility of > 1 match
      int matches = 0;
      int index2 = -1;
      while ((index2 = sourceBuffer.indexOf(targetString, index2 + 1)) != -1)
        matches++;
      if (matches > 1)
        continue;

      if (sourceBuffer.indexOf(targetString) != sourceBuffer.lastIndexOf(targetString))
        failCount++;
      sourceString = sourceBuffer.toString();
      if (sourceString.indexOf(targetString) != sourceString.lastIndexOf(targetString))
        failCount++;
    }

    report("IndexOf vs LastIndexOf       ", failCount);
  }

  private static void compareStringStringBuffer() {
    int failCount = 0;
    boolean make_new = true;

    String fragment = null;
    StringBuffer testBuffer = null;
    String testString = null;
    int testIndex = 0;

    failCount = "".indexOf("");

    for (int x = 0; x < 1000000; x++) {
      if (make_new) {
        testString = " ".repeat(1000);
        testString = generateTestString(1, 100);
        int len = testString.length();

        testBuffer = new StringBuffer(len);
        testBuffer.append(testString);
        if (!testString.equals(testBuffer.toString()))
          throw new RuntimeException("Initial equality failure");

        int x1 = 0;
        int x2 = 1000;
        while (x2 > testString.length()) {
          x1 = generator.nextInt(len);
          x2 = generator.nextInt(100);
          x2 = x1 + x2;
        }
        fragment = " ".repeat(1000);
        fragment = new String(testString.substring(x1, x2));
      }

      int sAnswer = testString.indexOf(fragment);
      int sbAnswer = testBuffer.indexOf(fragment);

      if (sAnswer != sbAnswer) {
        System.err.println("(1) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") len String = "
            + testString.length() + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        System.err.println("  testString = '" + testString + "'");
        System.err.println("  testBuffer = '" + testBuffer + "'");
        failCount++;
      } else {
        if (sAnswer > testString.length()) {
          System.err.println(
              "IndexOf returned value out of range; return: " + sAnswer + " length max: " + testBuffer.length());
        }
      }

      if ((fragment == "0#:02/62;+-\"\"0$25-5$#)1263") && (testBuffer.length() == 94)) {
        String xx = "abc";
        String yy = "abcdefg";
        int sA = xx.indexOf(yy);
      }

      if (make_new) testIndex = getRandomIndex(-100, 100);

      sAnswer = testString.indexOf(fragment, testIndex);
      sbAnswer = testBuffer.indexOf(fragment, testIndex);

      if (sAnswer != sbAnswer) {
        System.err.println("(2) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
            + " len String = " + testString.length() + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        System.err.println("  testString = '" + testString + "'");
        System.err.println("  testBuffer = '" + testBuffer + "'");
        failCount++;
        make_new = false;
      } else {
        if ((sAnswer > testString.length()) || ((sAnswer != -1) && (sAnswer < testIndex) && (fragment.length() != 0))) {
          System.err.println("IndexOf returned value out of range; return: " + sAnswer + " length max: "
              + testString.length() + " index: " + testIndex);
          System.err.println("(3) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
              + " len String = " + testString.length() + " len Buffer = " + testBuffer.length());
        }
      }

      sAnswer = testString.lastIndexOf(fragment);
      sbAnswer = testBuffer.lastIndexOf(fragment);

      if (sAnswer != sbAnswer) {
        System.err.println("(1) lastIndexOf fragment '" + fragment + "' len String = " + testString.length()
            + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        failCount++;
      }

      if (make_new) testIndex = getRandomIndex(-100, 100);

      sAnswer = testString.lastIndexOf(fragment, testIndex);
      sbAnswer = testBuffer.lastIndexOf(fragment, testIndex);

      if (sAnswer != sbAnswer) {
        System.err.println("(2) lastIndexOf fragment '" + fragment + "' index = " + testIndex + " len String = "
            + testString.length() + " len Buffer = " + testBuffer.length());
        failCount++;
      }
    }

    report("String vs StringBuffer       ", failCount);
  }

}
