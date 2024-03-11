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

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Arrays;
import java.nio.charset.Charset;

public class ECoreIndexOf {

  static Random generator = new Random();
  private static boolean failure = false;
  static char[] haystack = new char[128];
  static char[] haystack_16 = new char[128];

  static boolean success = true;

  public static void main(String[] args) throws Exception {
    int foo = 0;
    String testName = "ECoreIndexOf";

    String data = "0000100101010010110101010010101110101001110110101010010101010010000010111010101010101010100010010101110111010101101010100010010100001010111111100001010101001010100001010101001010101010111010010101010101010101010101010";
    String sub = "10101010";
    StringBuffer sbdata = new StringBuffer(data);

    for (int k = 0; k < 200000; k++) {
      int dummy = 0;
      int index = 0;
      index = indexOfKernel(data, sub);
      while ((index = indexOfKernel(data, sub, index)) > -1) {
        index++;
        dummy += index;
      }
    }

    for (int k = 0; k < 200000; k++) {
      int dummy = 0;
      int index = 0;
      index = indexOfKernel(sbdata, sub);
      while ((index = indexOfKernel(sbdata, sub, index)) > -1) {
        index++;
        dummy += index;
      }
    }

    for (int i = 0; i < 200000; i++) {
      foo = foo + indexOfKernel(testName, "dex");
      foo = foo + indexOfKernel(testName, "dex", 2);
    }

    for (int i = 0; i < 128; i++) {
      haystack[i] = (char) i;
    }

    haystack_16[0] = '\u0000'; //(char) (23 + 256);
    for (int i = 1; i < 128; i++) {
      haystack_16[i] = (char) (i);
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    // simpleTest();
    // compareIndexOfLastIndexOf();
    // compareStringStringBuffer();

    System.out.println("Testing LL");

    exhaustive(false, false, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(false, true, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(true, false, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(true, true, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);

    System.out.println("Testing UL");

    exhaustive(false, false, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(false, true, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(true, false, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(true, true, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);

    System.out.println("Testing UU");

    exhaustive(false, false, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(false, true, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(true, false, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(true, true, StandardCharsets.UTF_16, StandardCharsets.UTF_16);

    for (int i = 0; i < 128; i++) {
      haystack[i] = (char) 'a';
    }

    for (int i = 0; i < 128; i++) {
      haystack_16[i] = (char) ('a' + 256);
    }

    System.out.println("Testing LL (same char)");

    exhaustive(false, false, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(false, true, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(true, false, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);
    exhaustive(true, true, StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1);

    System.out.println("Testing UL (same char)");

    exhaustive(false, false, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(false, true, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(true, false, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);
    exhaustive(true, true, StandardCharsets.UTF_16, StandardCharsets.ISO_8859_1);

    System.out.println("Testing UU (same char)");

    exhaustive(false, false, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(false, true, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(true, false, StandardCharsets.UTF_16, StandardCharsets.UTF_16);
    exhaustive(true, true, StandardCharsets.UTF_16, StandardCharsets.UTF_16);

    System.out.println(testName + " complete.");
    // compareExhaustive();

    if (failure)
      throw new RuntimeException("One or more failures.");
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

  private static int indexOfKernel(String haystack, String needle) {
    return haystack.indexOf(needle);
  }

  private static int indexOfKernel(String haystack, String needle, int offset) {
    return haystack.indexOf(needle, offset);
  }

  private static int indexOfKernel(StringBuffer haystack, String needle) {
    return haystack.indexOf(needle);
  }

  private static int indexOfKernel(StringBuffer haystack, String needle, int offset) {
    return haystack.indexOf(needle, offset);
  }

  private static void printStringBytes(byte[] bytes) {
    // byte[] bytes = str.getBytes();
    System.err.println("bytes.len=" + bytes.length);
    for (byte b : bytes) {
      System.err.print(String.format("0x%02x ", b));
    }
    System.err.println("");
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

  private static void exhaustive(boolean useOffset, boolean useStringBuffer, Charset hs_charset,
      Charset needleCharset) {
    int result = 0;
    int midresult = 0;
    int endresult = 0;
    int l_offset = 0;
    int failCount = 0;

    System.err.println("Use offset=" + useOffset + ", Use StringBuffer=" + useStringBuffer + ", Haystack=" + hs_charset
        + ", Needle=" + needleCharset);

    for (int needleSize = 0; needleSize < 128; needleSize++) {
      for (int haystackSize = 0; haystackSize < 128; haystackSize++) {
        for (l_offset = 0; l_offset <= haystackSize; l_offset++) {
          String needle = new String(Arrays.copyOfRange(
              (needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack, l_offset, l_offset + needleSize));
          int hsSize = (haystackSize - l_offset) >= 0 ? haystackSize - l_offset : 0;
          int midStart = hsSize / 2;
          int endStart = (hsSize > needleSize) ? hsSize - needleSize : 0;
          String midNeedle = new String(
              Arrays.copyOfRange((needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack,
                  midStart + l_offset, midStart + needleSize + l_offset));
          String endNeedle = new String(
              Arrays.copyOfRange((needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack,
                  endStart + l_offset, endStart + needleSize + l_offset));
          String shs = new String(
              Arrays.copyOfRange((hs_charset == StandardCharsets.UTF_16) ? haystack_16 : haystack, 0, haystackSize));

          // Truncate needles to correct lengths

          if (l_offset + needleSize > haystack.length + 1) {
            needle = needle.substring(0, needleSize);
            midNeedle = midNeedle.substring(0, needleSize);
            endNeedle = endNeedle.substring(0, needleSize);
          }

          if (!success && needleSize > 1) {
            needle = needle.substring(0, needle.length() - 1) + (char) ((int) (needle.charAt(needle.length() - 2) + 1));
            midNeedle = midNeedle.substring(0, midNeedle.length() - 1)
                + (char) ((int) (midNeedle.charAt(midNeedle.length() - 2) + 1));
            endNeedle = endNeedle.substring(0, endNeedle.length() - 1)
                + (char) ((int) (endNeedle.charAt(endNeedle.length() - 2) + 1));
          }

          StringBuffer hs = new StringBuffer(shs.length());
          hs.append(shs);
          if (!shs.equals(hs.toString()))
            throw new RuntimeException("Initial equality failure");

          if (useStringBuffer) {
            result = indexOfKernel(hs, needle, l_offset);
            midresult = indexOfKernel(hs, midNeedle, l_offset);
            endresult = indexOfKernel(hs, endNeedle, l_offset);
          } else {
            result = indexOfKernel(shs, needle, l_offset);
            midresult = indexOfKernel(shs, midNeedle, l_offset);
            endresult = indexOfKernel(shs, endNeedle, l_offset);
          }
          int nResult = naiveFind(hs.toString(), needle, l_offset);
          int midnResult = naiveFind(hs.toString(), midNeedle, l_offset);
          int endnResult = naiveFind(hs.toString(), endNeedle, l_offset);
          if (result != nResult) {
            failCount++;
            System.err.println("useOffset=" + useOffset + ", useStringBuffer=" + useStringBuffer);
            System.err.print("Haystack=");
            printStringBytes(shs.getBytes(hs_charset));
            System.err.print("Needle=");
            printStringBytes(needle.getBytes(needleCharset));
            System.err.println("l_offset=" + l_offset);
            System.err.println("haystackLen=" + haystackSize + " neeldeLen=" + needleSize +
                " result=" + result + " nResult=" + nResult);
          }
          // badResults = success ? ((midnResult == -1) || (midresult == -1)) :
          // ((midnResult != -1) || (midresult != -1));
          if ((midresult != midnResult)) {
            failCount++;
            System.err.println("useOffset=" + useOffset + ", useStringBuffer=" + useStringBuffer);
            System.err.print("Haystack=");
            printStringBytes(shs.getBytes(hs_charset));
            System.err.print("Needle=");
            printStringBytes(midNeedle.getBytes(needleCharset));
            System.err.println("l_offset=" + l_offset);
            System.err.println("haystackLen=" + haystackSize + " neeldeLen=" + needleSize +
                " midresult=" + midresult + " midnResult=" + midnResult);
          }
          // badResults = success ? ((endnResult == -1) || (endresult == -1)) :
          // ((endnResult != -1) || (endresult != -1));
          if ((endresult != endnResult)) {
            failCount++;
            System.err.println("useOffset=" + useOffset + ", useStringBuffer=" + useStringBuffer);
            System.err.print("Haystack=");
            printStringBytes(shs.getBytes(hs_charset));
            System.err.print("Needle=");
            printStringBytes(endNeedle.getBytes(needleCharset));
            System.err.println("l_offset=" + l_offset);
            System.err.println("haystackLen=" + haystackSize + " neeldeLen=" + needleSize +
                " endresult=" + endresult + " endnResult=" + endnResult);
          }

          if (!useOffset)
            l_offset = haystackSize + 100;
        }
      }
    }

    report("Exhaustive                   ", failCount);
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
              int iResult = sourceString.substring(hsBegin, hsBegin + haystackLen)
                  .indexOf(targetString.substring(nBegin, nBegin + needleLen));
              if (iResult != nResult) {
                System.out.println("Source=" + sourceString.substring(hsBegin, hsBegin + haystackLen));
                System.out.println("Target=" + targetString.substring(nBegin, nBegin + needleLen));
                System.out.println("haystackLen=" + haystackLen + " neeldeLen=" + needleLen + " hsBegin=" + hsBegin
                    + " nBegin=" + nBegin +
                    " iResult=" + iResult + " nResult=" + nResult);
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
      } while (indexOfKernel(sourceString, targetString) != -1);

      int index1 = generator.nextInt(90) + 5;
      sourceBuffer = sourceBuffer.replace(index1, index1, targetString);

      if ((indexOfKernel(sourceBuffer, targetString) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + indexOfKernel(sourceBuffer, targetString));
        failCount++;
      }
      if ((indexOfKernel(sourceBuffer, targetString, 5) != index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString, 5) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + indexOfKernel(sourceBuffer, targetString, 5));
        failCount++;
      }
      if ((indexOfKernel(sourceBuffer, targetString, 99) == index1) ||
          (index1 != naiveFind(sourceBuffer.toString(), targetString, 0))) {
        System.err.println("sourceBuffer.indexOf(targetString, 99) fragment '" + targetString + "' ("
            + targetString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), targetString, 0) + ", IndexOf = "
            + indexOfKernel(sourceBuffer, targetString, 99));
        failCount++;
      }
      if ((indexOfKernel(sourceBuffer, emptyString, 99) != 99) ||
          (99 != naiveFind(sourceBuffer.toString(), emptyString, 99))) {
        System.err.println("sourceBuffer.indexOf(emptyString, 99) fragment '" + emptyString + "' ("
            + emptyString.length() + ") String = "
            + sourceBuffer.toString() + " len Buffer = " + sourceBuffer.toString().length());
        System.err.println("  naive = " + naiveFind(sourceBuffer.toString(), emptyString, 99) + ", IndexOf = "
            + indexOfKernel(sourceBuffer, emptyString, 99));
        failCount++;
      }
      if ((indexOfKernel(allAsBuffer.substring(1, 3), allAsBuffer.substring(5, 12)) != -1) ||
          (-1 != naiveFind(allAsBuffer.substring(1, 3).toString(), allAsBuffer.substring(5, 12), 0))) {
        System.err.println("allAsBuffer.substring(1, 3).indexOf(allAsBuffer.substring(5, 12)) fragment '"
            + allAsBuffer.substring(5, 12) + "' ("
            + allAsBuffer.substring(5, 12).length() + ") String = "
            + allAsBuffer.substring(1, 3) + " len Buffer = " + allAsBuffer.substring(1, 3).length());
        System.err.println(
            "  naive = " + naiveFind(allAsBuffer.substring(1, 3).toString(), allAsBuffer.substring(5, 12), 0)
                + ", IndexOf = " + indexOfKernel(allAsBuffer.substring(1, 3), allAsBuffer.substring(5, 12)));
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
      } while (indexOfKernel(sourceString, targetString) != -1);

      int index1 = generator.nextInt(100);
      sourceBuffer = sourceBuffer.replace(index1, index1, targetString);

      // extremely remote possibility of > 1 match
      int matches = 0;
      int index2 = -1;
      while ((index2 = indexOfKernel(sourceBuffer, targetString, index2 + 1)) != -1)
        matches++;
      if (matches > 1)
        continue;

      if (indexOfKernel(sourceBuffer, targetString) != sourceBuffer.lastIndexOf(targetString))
        failCount++;
      sourceString = sourceBuffer.toString();
      if (indexOfKernel(sourceString, targetString) != sourceString.lastIndexOf(targetString))
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

    failCount = indexOfKernel("", "");

    for (int x = 0; x < 1000000; x++) {
      if (make_new) {
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

      int sAnswer = indexOfKernel(testString, fragment);
      int sbAnswer = indexOfKernel(testBuffer, fragment);

      if (sAnswer != sbAnswer) {
        while (sAnswer != sbAnswer) {
          System.err.println("(1) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") len String = "
              + testString.length() + " len Buffer = " + testBuffer.length());
          System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
          System.err.println("  testString = '" + testString + "'");
          System.err.println("  testBuffer = '" + testBuffer + "'");
          failCount++;

          sAnswer = indexOfKernel(testString, fragment);
          sbAnswer = indexOfKernel(testBuffer, fragment);
        }
      } else {
        if (sAnswer > testString.length()) {
          System.err.println(
              "IndexOf returned value out of range; return: " + sAnswer + " length max: " + testBuffer.length());
        }
      }

      if ((fragment == "0#:02/62;+-\"\"0$25-5$#)1263") && (testBuffer.length() == 94)) {
        String xx = "abc";
        String yy = "abcdefg";
        int sA = indexOfKernel(xx, yy);
      }

      if (make_new)
        testIndex = getRandomIndex(-100, 100);

      sAnswer = indexOfKernel(testString, fragment, testIndex);
      sbAnswer = indexOfKernel(testBuffer, fragment, testIndex);

      if (sAnswer != sbAnswer) {
        while (sAnswer != sbAnswer) {
          System.err.println("(2) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
            + " len String = " + testString.length() + " len Buffer = " + testBuffer.length());
          System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
          System.err.println("  testString = '" + testString + "'");
          System.err.println("  testBuffer = '" + testBuffer + "'");
          failCount++;
          make_new = false;

          sAnswer = indexOfKernel(testString, fragment, testIndex);
          sbAnswer = indexOfKernel(testBuffer, fragment, testIndex);
        }
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
        while (sAnswer != sbAnswer) {
          System.err.println("(1) lastIndexOf fragment '" + fragment + "' len String = " + testString.length()
            + " len Buffer = " + testBuffer.length());
          System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
          failCount++;

          sAnswer = testString.lastIndexOf(fragment);
          sbAnswer = testBuffer.lastIndexOf(fragment);
        }
      }

      if (make_new)
        testIndex = getRandomIndex(-100, 100);

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
