/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;

public class IndexOf {

  static Random generator = new Random(1999);
  private static boolean failure = false;

  public static void main(String[] args) throws Exception {
    String testName = "IndexOf";
    if (false) {
    String xx = "0#.!,))\"7-0#:02/62;+-\"\"0$25-5$#)1263'.&&(127+'*$%\"1+9,45'-/&,0;97*/, ,$':'8+#3%5:6+#  '3-:.!";
    String yy = "0#:02/62;+-\"\"0$25-5$#)1263";
    int gg = xx.indexOf(yy, 50);
    System.err.println(gg);

    } else {

    for (int i = 0; i < 20000; i++) {
      int foo = testName.indexOf("dex");
    }
    System.out.println("");
    generator.setSeed(1999);
    simpleTest();
    generator.setSeed(1999);
    compareIndexOfLastIndexOf();
    generator.setSeed(1999);
    compareStringStringBuffer();
    generator.setSeed(1999);
    compareExhaustive();
    }

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
    int y = 0;
    int len = haystack.length() - offset;
    if (needle.length() == 0) return 0;
    if (needle.length() > len) return -1;
    for (x = offset; x < len - needle.length() + 1; x++) {
      if (haystack.charAt(x) == needle.charAt(0)) {
        for (y = 1; y < needle.length(); y++) {
          if (haystack.charAt(x + y) != needle.charAt(y)) {
            break;
          }
        }
        if (y == needle.length()) return x;
      }
    }
    return -1;
  }

  private static void compareExhaustive() {
    int failCount = 0;
    String sourceString;
    StringBuffer sourceBuffer;
    String targetString;
    String targetStringBeginning;
    String targetStringMiddle;
    String targetStringEnd;
    int hsLen = 97;
    int maxNeedleLen = hsLen / 2;
    int haystackLen;
    int needleLen;
    int hsBegin, hsEnd, nBegin, nEnd;

    for (int i = 0; i < 10000; i++) {
      do {
        sourceString = generateTestString(hsLen - 1, hsLen);
        sourceBuffer = new StringBuffer(sourceString);
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

    for (int i = 0; i < 10000; i++) {
      do {
        sourceString = generateTestString(99, 100);
        sourceBuffer = new StringBuffer(sourceString);
        targetString = generateTestString(10, 11);
      } while (sourceString.indexOf(targetString) != -1);

      int index1 = generator.nextInt(90) + 5;
      sourceBuffer = sourceBuffer.replace(index1, index1, targetString);

      if ((sourceBuffer.indexOf(targetString) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0)))
        failCount++;
      if ((sourceBuffer.indexOf(targetString, 5) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0)))
        failCount++;
      if ((sourceBuffer.indexOf(targetString, 99) == index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0)))
        failCount++;
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
    generator.setSeed(1999);

    for (int x = 0; x < 1000000; x++) {
    if(make_new) {
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
        fragment = testString.substring(x1, x2);
    }

      int sAnswer = testString.indexOf(fragment);
      int sbAnswer = testBuffer.indexOf(fragment);

      if (sAnswer != sbAnswer) {
        System.err.println("IndexOf fragment '" + fragment + "' (" + fragment.length() + ") len String = "
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

    if(make_new)
        testIndex = getRandomIndex(-100, 100);

      sAnswer = testString.indexOf(fragment, testIndex);
      sbAnswer = testBuffer.indexOf(fragment, testIndex);

      if (sAnswer != sbAnswer) {
        System.err.println("IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
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
          System.err.println("IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
              + " len String = " + testString.length() + " len Buffer = " + testBuffer.length());
        }
      }

      sAnswer = testString.lastIndexOf(fragment);
      sbAnswer = testBuffer.lastIndexOf(fragment);

      if (sAnswer != sbAnswer) {
        System.err.println("lastIndexOf fragment '" + fragment + "' len String = " + testString.length()
            + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        failCount++;
      }

      if(make_new) testIndex = getRandomIndex(-100, 100);

      sAnswer = testString.lastIndexOf(fragment, testIndex);
      sbAnswer = testBuffer.lastIndexOf(fragment, testIndex);

      if (sAnswer != sbAnswer) {
        System.err.println("lastIndexOf fragment '" + fragment + "' index = " + testIndex + " len String = "
            + testString.length() + " len Buffer = " + testBuffer.length());
        failCount++;
      }
    }

    report("String vs StringBuffer       ", failCount);
  }

}
