/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320448
 * @summary Test indexOf and lastIndexOf
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=dontinline,ECoreIndexOf.indexOfKernel ECoreIndexOf
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,ECoreIndexOf.indexOfKernel ECoreIndexOf
 * @key randomness
 */

/* @test
 * @bug 8320448
 * @summary Test indexOf and lastIndexOf
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+EnableX86ECoreOpts -XX:UseAVX=2 -Xbatch -XX:-TieredCompilation -XX:CompileCommand=dontinline,ECoreIndexOf.indexOfKernel ECoreIndexOf
 * @key randomness
 */

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.Charset;
import java.lang.Math;

// @ECoreIndexOf(singleThreaded=true)
public class ECoreIndexOf {

  static Random generator;
  private static boolean failure = false;
  static char[] haystack = new char[128];
  static char[] haystack_16 = new char[128];

  static boolean verbose = false;
  static boolean success = true;

  static Map<Charset, String> titles = new HashMap<Charset, String>();
  static Random rng = new Random(1999);

  public static void main(String[] args) throws Exception {
    int foo = 0;
    String testName = "ECoreIndexOf";

    generator = new Random();
    long seed = generator.nextLong();
    generator.setSeed(seed);
    System.out.println("Seed set to "+ seed);

    ///////////////////////////  WARM-UP //////////////////////////

    for (int i = 0; i < 20000; i++) {
      char c = 65;
      char c16 = 0x1ed;
      StringBuffer sb = new StringBuffer("a");
      StringBuffer sb16 = new StringBuffer("\u01fe");

      foo += indexOfKernel("\u01fe", "a");
      foo += indexOfKernel("\u01fe", "a", 0);
      foo += indexOfKernel("\u01fe", "\u01ff");
      foo += indexOfKernel("\u01fe", "\u01ff", 0);
      foo += indexOfKernel("a", "a");
      foo += indexOfKernel("a", "a", 0);
      foo += indexOfKernel("a", "\u01ff");
      foo += indexOfKernel("a", "\u01ff", 0);

      foo += indexOfKernel("\u01fe", c);
      foo += indexOfKernel("\u01fe", c, 0);
      foo += indexOfKernel("\u01fe", c16);
      foo += indexOfKernel("\u01fe", c16, 0);
      foo += indexOfKernel("a", c);
      foo += indexOfKernel("a", c, 0);
      foo += indexOfKernel("a", c16);
      foo += indexOfKernel("a", c16, 0);

      foo += indexOfKernel(sb16, c);
      foo += indexOfKernel(sb16, c, 0);
      foo += indexOfKernel(sb16, c16);
      foo += indexOfKernel(sb16, c16, 0);
      foo += indexOfKernel(sb, c);
      foo += indexOfKernel(sb, c, 0);
      foo += indexOfKernel(sb, c16);
      foo += indexOfKernel(sb, c16, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    String[] decorators = {"", " (same char)"};
    Charset[] charSets = {StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16};
    boolean[] truefalse = {true, false};

    titles.put(StandardCharsets.ISO_8859_1, "L");
    titles.put(StandardCharsets.UTF_16, "U");

    for (int xxy = 0; xxy < 2; xxy++) { // Run at least twice to ensure stub called

      for (int i = 0; i < 128; i++) {
        haystack[i] = (char) i;
      }

      haystack_16[0] = '\u0000'; // (char) (23 + 256);
      for (int i = 1; i < 128; i++) {
        haystack_16[i] = (char) (i);
      }

      simpleTest();
      compareIndexOfLastIndexOf();
      compareStringStringBuffer();
      StringIndexof();
      StringIndexofChar();
      StringIndexofHuge();

      for (String decorator : decorators) {
        for (Charset csHaystack : charSets) {
          for (Charset csNeedle : charSets) {
            System.out.println("Testing " + titles.get(csHaystack) + titles.get(csNeedle) + decorator);
            for (boolean useOffset : truefalse) {
              for (boolean useBuffer : truefalse) {
                exhaustive(useOffset, useBuffer, csHaystack, csNeedle);
              }
            }
          }
        }

        for (int i = 0; i < 128; i++) {
          haystack[i] = (char) 'a';
        }

        for (int i = 0; i < 128; i++) {
          haystack_16[i] = (char) ('a' + 256);
        }
      }
    }

    System.out.println(testName + " complete.");

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

  private static String makeRndString(boolean isUtf16, int length) {
    StringBuilder sb = new StringBuilder(length);
    if (length > 0) {
      sb.append(isUtf16 ? '\u2026' : 'b'); // ...

      for (int i = 1; i < length - 1; i++) {
        sb.append((char) ('b' + rng.nextInt(26)));
      }

      sb.append(rng.nextInt(3) >= 1 ? 'a' : 'b');// 66.6% of time 'a' is in string
    }
    return sb.toString();
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

  private static int indexOfKernel(StringBuffer haystack, char cneedle) {
    String needle = String.valueOf(cneedle);
    return haystack.indexOf(needle);
  }

  private static int indexOfKernel(StringBuffer haystack, String needle, int offset) {
    return haystack.indexOf(needle, offset);
  }

  private static int indexOfKernel(StringBuffer haystack, char cneedle, int offset) {
    String needle = String.valueOf(cneedle);
    return haystack.indexOf(needle, offset);
  }

  private static int indexOfKernel(String haystack, char needle) {
    return haystack.indexOf(needle);
  }

  private static int indexOfKernel(String haystack, char needle, int offset) {
    return haystack.indexOf(needle, offset);
  }

  private static void printStringBytes(byte[] bytes) {
    System.err.println(" bytes.len=" + bytes.length);
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

  private static int naiveFind(String haystack, String needle) {
    return naiveFind(haystack, needle, 0);
  }

  private static int naiveFind(String haystack, char needle) {
    return naiveFind(haystack, needle, 0);
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

    for (x = offset; x < haystack.length() - needle.length() + 1; x++) {
      if (haystack.codePointAt(hsndx) == needle.codePointAt(0)) {
        nndx = Character.charCount(needle.codePointAt(0));
        int hsndx_tmp = hsndx + Character.charCount(haystack.codePointAt(hsndx));

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

  private static int naiveFind(String haystack, char cneedle, int offset) {
    int x = offset;
    int len = haystack.length() - offset;
    String needle = String.valueOf(cneedle);
    if (len == 0)
      return -1;
    int hsndx = 0;
    for (int xx = 0; xx < offset; xx++) {
      hsndx += Character.charCount(haystack.codePointAt(hsndx));
    }

    for (x = offset; x < haystack.length(); x++) {
      if (haystack.codePointAt(hsndx) == needle.codePointAt(0)) {
        return x;
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

    String thisTest = titles.get(hs_charset) + titles.get(needleCharset) + (useOffset ? " w/offset" : "") + (useStringBuffer ? " StringBuffer" : "");

    for (int needleSize = 0; needleSize < 128; needleSize++) {
      for (int haystackSize = 0; haystackSize < 128; haystackSize++) {
        for (l_offset = 0; l_offset <= haystackSize; l_offset++) {
          String needle = new String(Arrays.copyOfRange(
              (needleCharset == StandardCharsets.UTF_16) ? haystack_16 : haystack, l_offset, l_offset + needleSize));
          int hsSize = (haystackSize - l_offset) >= 0 ? haystackSize - l_offset : 0;
          int midStart = Math.max((hsSize / 2) - (needleSize / 2), 0);
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
            System.err.println("haystackLen=" + haystackSize + " needleLen=" + needleSize +
                " result=" + result + " nResult=" + nResult);
            System.err.println("");
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
            System.err.println("haystackLen=" + haystackSize + " needleLen=" + needleSize +
                " midresult=" + midresult + " midnResult=" + midnResult);
            System.err.println("");
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
            System.err.println("haystackLen=" + haystackSize + " needleLen=" + needleSize +
                " endresult=" + endresult + " endnResult=" + endnResult);
            System.err.println("");
          }

          if (!useOffset)
            l_offset = haystackSize + 100;
        }
      }
    }

    report("Exhaustive " + thisTest, failCount);
  }

  private static void PrintError(int kernel, int naive, int num, String prefix, String hs, char needle) {
    PrintError(kernel, naive, num, prefix, hs, String.valueOf(needle));
  }

  private static void PrintError(int kernel, int naive, int num, String prefix, String hs, String needle) {
    if (!verbose)
      return;
    System.err.println(prefix + ": (" + num + "): kernel=" + kernel + ", naive=" + naive);
    System.err.print("Haystack=");
    printStringBytes(hs.getBytes());
    System.err.print("Needle=");
    printStringBytes(needle.getBytes());
    System.err.println("");
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
        System.err.println("(1) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") len String = "
            + testString.length() + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        System.err.println("  testString = '" + testString + "'");
        System.err.println("  testBuffer = '" + testBuffer + "'");
        failCount++;

        sAnswer = indexOfKernel(testString, fragment);
        sbAnswer = indexOfKernel(testBuffer, fragment);
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
        System.err.println("(2) IndexOf fragment '" + fragment + "' (" + fragment.length() + ") index = " + testIndex
            + " len String = " + testString.length() + " len Buffer = " + testBuffer.length());
        System.err.println("  sAnswer = " + sAnswer + ", sbAnswer = " + sbAnswer);
        System.err.println("  testString = '" + testString + "'");
        System.err.println("  testBuffer = '" + testBuffer + "'");
        failCount++;
        make_new = true;

        sAnswer = indexOfKernel(testString, fragment, testIndex);
        sbAnswer = indexOfKernel(testBuffer, fragment, testIndex);
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

          sAnswer = testString.lastIndexOf(fragment);
          sbAnswer = testBuffer.lastIndexOf(fragment);
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

  //////////////////////////////////////////////////////////////////////
  // Test routines used in benchmarks
  //
  // From StringIndexofHuge
  private static void StringIndexofHuge() {
    int stubResult = 0;
    int failCount = 0;

    for (int xx = 0; xx < 2; xx++) {
      int num = 1;

      String dataString = "ngdflsoscargfdgf";
      String dataString16 = "ngdfilso\u01facargfd\u01eef";
      String dataStringHuge = (("A".repeat(32) + "B".repeat(32)).repeat(16) + "X").repeat(2) + "bB";
      String dataStringHuge16 = "\u01de" + (("A".repeat(32) + "B".repeat(32)).repeat(16) + "\u01fe").repeat(2)
          + "\u01eeB";
      String earlyMatchString = dataStringHuge.substring(0, 34);
      String earlyMatchString16 = dataStringHuge16.substring(0, 34);
      String midMatchString = dataStringHuge.substring(dataStringHuge.length() / 2 - 16,
          dataStringHuge.length() / 2 + 32);
      String midMatchString16 = dataStringHuge16.substring(dataStringHuge16.length() / 2 - 16,
          dataStringHuge16.length() / 2 + 32);
      String lateMatchString = dataStringHuge.substring(dataStringHuge.length() - 31);
      String lateMatchString16 = dataStringHuge16.substring(dataStringHuge16.length() - 31);

      String searchString = "oscar";
      String searchString16 = "o\u01facar";
      String searchStringSmall = "dgf";
      String searchStringSmall16 = "d\u01eef";

      String searchStringHuge = "capaapapapasdkajdlkajskldjaslkajdlkajskldjaslkjdlkasjdsalk";
      String searchStringHuge16 = "capaapapapasdkajdlka\u01feskldjaslkajdlkajskldjaslkjdlkasjdsalk";

      String searchNoMatch = "XYXyxYxy".repeat(22);
      String searchNoMatch16 = "\u01ab\u01ba\u01cb\u01bc\u01de\u01ed\u01fa\u01af".repeat(22);

      stubResult = indexOfKernel(dataStringHuge16, earlyMatchString);
      int nResult = naiveFind(dataStringHuge16, earlyMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, earlyMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, earlyMatchString);
      nResult = naiveFind(dataStringHuge, earlyMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge, earlyMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, midMatchString);
      nResult = naiveFind(dataStringHuge, midMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge, midMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, lateMatchString);
      nResult = naiveFind(dataStringHuge, lateMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge, lateMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, searchNoMatch);
      nResult = naiveFind(dataStringHuge, searchNoMatch);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge, searchNoMatch);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchString, searchString);
      nResult = naiveFind(searchString, searchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", searchString, searchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString, searchString);
      nResult = naiveFind(dataString, searchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString, searchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString, searchStringSmall);
      nResult = naiveFind(dataString, searchStringSmall);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString, searchStringSmall);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, "B".repeat(30) + "X" + "A".repeat(30), 74);
      nResult = naiveFind(dataStringHuge, "B".repeat(30) + "X" + "A".repeat(30), 74);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge,
            "B".repeat(30) + "X" + "A".repeat(30));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, "A".repeat(32) + "F" + "B".repeat(32), 64);
      nResult = naiveFind(dataStringHuge, "A".repeat(32) + "F" + "B".repeat(32), 64);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge,
            "A".repeat(32) + "F" + "B".repeat(32));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(midMatchString, dataStringHuge, 3);
      nResult = naiveFind(midMatchString, dataStringHuge, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", midMatchString, dataStringHuge);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge, "A".repeat(32) + "B".repeat(30) + "bB");
      nResult = naiveFind(dataStringHuge, "A".repeat(32) + "B".repeat(30) + "bB");
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge,
            "A".repeat(32) + "B".repeat(30) + "bB");
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, earlyMatchString);
      nResult = naiveFind(dataStringHuge16, earlyMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, earlyMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, midMatchString);
      nResult = naiveFind(dataStringHuge16, midMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, midMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, lateMatchString);
      nResult = naiveFind(dataStringHuge16, lateMatchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, lateMatchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, searchNoMatch);
      nResult = naiveFind(dataStringHuge16, searchNoMatch);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, searchNoMatch);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchString16, searchString);
      nResult = naiveFind(searchString16, searchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", searchString16, searchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString16, searchString);
      nResult = naiveFind(dataString16, searchString);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString16, searchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString16, searchStringSmall);
      nResult = naiveFind(dataString16, searchStringSmall);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString16, searchStringSmall);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "B".repeat(30) + "X" + "A".repeat(30), 74);
      nResult = naiveFind(dataStringHuge16, "B".repeat(30) + "X" + "A".repeat(30), 74);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "B".repeat(30) + "X" + "A".repeat(30));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "A".repeat(32) + "F" + "B".repeat(32), 64);
      nResult = naiveFind(dataStringHuge16, "A".repeat(32) + "F" + "B".repeat(32), 64);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "A".repeat(32) + "F" + "B".repeat(32));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(midMatchString16, dataStringHuge, 3);
      nResult = naiveFind(midMatchString16, dataStringHuge, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", midMatchString16, dataStringHuge);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "A".repeat(32) + "B".repeat(30) + "bB");
      nResult = naiveFind(dataStringHuge16, "A".repeat(32) + "B".repeat(30) + "bB");
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "A".repeat(32) + "B".repeat(30) + "bB");
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, earlyMatchString16);
      nResult = naiveFind(dataStringHuge16, earlyMatchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, earlyMatchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, midMatchString16);
      nResult = naiveFind(dataStringHuge16, midMatchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, midMatchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, lateMatchString16);
      nResult = naiveFind(dataStringHuge16, lateMatchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, lateMatchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, searchNoMatch16);
      nResult = naiveFind(dataStringHuge16, searchNoMatch16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16, searchNoMatch16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchString16, searchString16);
      nResult = naiveFind(searchString16, searchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", searchString16, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString16, searchString16);
      nResult = naiveFind(dataString16, searchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString16, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString16, searchStringSmall16);
      nResult = naiveFind(dataString16, searchStringSmall16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataString16, searchStringSmall16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "B".repeat(30) + "X" + "A".repeat(30), 74);
      nResult = naiveFind(dataStringHuge16, "B".repeat(30) + "X" + "A".repeat(30), 74);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "B".repeat(30) + "X" + "A".repeat(30));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "A".repeat(32) + "\u01ef" + "B".repeat(32), 64);
      nResult = naiveFind(dataStringHuge16, "A".repeat(32) + "\u01ef" + "B".repeat(32), 64);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "A".repeat(32) + "\u01ef" + "B".repeat(32));
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(midMatchString16, dataStringHuge16, 3);
      nResult = naiveFind(midMatchString16, dataStringHuge16, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", midMatchString16, dataStringHuge16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringHuge16, "A".repeat(32) + "B".repeat(30) + "\u01eeB");
      nResult = naiveFind(dataStringHuge16, "A".repeat(32) + "B".repeat(30) + "\u01eeB");
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexofHuge", dataStringHuge16,
            "A".repeat(32) + "B".repeat(30) + "\u01eeB");
        failCount++;
      }
      num++;
    }

    report("StringIndexofHuge            ", failCount);
  }

  /////////////////////////////////////////////////////////////////////
  //
  // From StringIndexof
  private static void StringIndexof() {
    int stubResult = 0;
    int failCount = 0;

    for (int xx = 0; xx < 2; xx++) {
      int num = 1;

      String dataString = "ngdfilsoscargfdgf";
      String searchString = "oscar";
      String dataStringBig = "2937489745890797905764956790452976742965790437698498409583479067ngdcapaapapapasdkajdlkajskldjaslkjdlkasjdsalkjas";
      String searchStringBig = "capaapapapasdkajdlkajskldjaslkjdlkasjdsalk";
      String data = "0000100101010010110101010010101110101001110110101010010101010010000010111010101010101010100010010101110111010101101010100010010100001010111111100001010101001010100001010101001010101010111010010101010101010101010101010";
      String sub = "10101010";
      String shortSub1 = "1";
      String data2 = "00001001010100a10110101010010101110101001110110101010010101010010000010111010101010101010a100010010101110111010101101010100010010a100a0010101111111000010101010010101000010101010010101010101110a10010101010101010101010101010";
      String shortSub2 = "a";
      char searchChar = 's';

      String string16Short = "scar\u01fe1";
      String string16Medium = "capaapapapasdkajdlkajskldjaslkjdlkasjdsalksca1r\u01fescar";
      String string16Long = "2937489745890797905764956790452976742965790437698498409583479067ngdcapaapapapasdkajdlkajskldjaslkjdlkasjdsalkja1sscar\u01fescar";
      char searchChar16 = 0x1fe;
      String searchString16 = "\u01fe";

      stubResult = indexOfKernel(dataStringBig, searchChar);
      int nResult = naiveFind(dataStringBig, searchChar);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", dataStringBig, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchStringBig, searchChar);
      nResult = naiveFind(searchStringBig, searchChar);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", searchStringBig, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchString, searchChar);
      nResult = naiveFind(searchString, searchChar);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", searchString, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, searchChar16);
      nResult = naiveFind(string16Long, searchChar16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, searchChar16);
      nResult = naiveFind(string16Medium, searchChar16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Short, searchChar16);
      nResult = naiveFind(string16Short, searchChar16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Short, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringBig, searchChar, 3);
      nResult = naiveFind(dataStringBig, searchChar, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", dataStringBig, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchStringBig, searchChar, 3);
      nResult = naiveFind(searchStringBig, searchChar, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", searchStringBig, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(searchString, searchChar, 1);
      nResult = naiveFind(searchString, searchChar, 1);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", searchString, searchChar);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, searchChar16, 3);
      nResult = naiveFind(string16Long, searchChar16, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, searchChar16, 3);
      nResult = naiveFind(string16Medium, searchChar16, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Short, searchChar16, 2);
      nResult = naiveFind(string16Short, searchChar16, 2);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Short, searchChar16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, shortSub1);
      nResult = naiveFind(string16Long, shortSub1);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, shortSub1);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, shortSub1);
      nResult = naiveFind(string16Medium, shortSub1);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, shortSub1);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, shortSub2);
      nResult = naiveFind(string16Long, shortSub2);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, shortSub2);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, shortSub1, 3);
      nResult = naiveFind(string16Long, shortSub1, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, shortSub1);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, shortSub1, 3);
      nResult = naiveFind(string16Medium, shortSub1, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, shortSub1);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Short, shortSub2, 1);
      nResult = naiveFind(string16Short, shortSub2, 1);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Short, shortSub2);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, searchString16, 3);
      nResult = naiveFind(string16Long, searchString16, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, searchString16, 3);
      nResult = naiveFind(string16Medium, searchString16, 3);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Short, searchString16, 2);
      nResult = naiveFind(string16Short, searchString16, 2);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Short, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Long, searchString16);
      nResult = naiveFind(string16Long, searchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Long, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Medium, searchString16);
      nResult = naiveFind(string16Medium, searchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Medium, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(string16Short, searchString16);
      nResult = naiveFind(string16Short, searchString16);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", string16Short, searchString16);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataString, searchString, 2);
      nResult = naiveFind(dataString, searchString, 2);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", dataString, searchString);
        failCount++;
      }
      num++;
      stubResult = indexOfKernel(dataStringBig, searchStringBig, 2);
      nResult = naiveFind(dataStringBig, searchStringBig, 2);
      if (nResult != stubResult) {
        PrintError(stubResult, nResult, num, "StringIndexof", dataStringBig, searchStringBig);
      }
      {
        int index = 0;
        int dummy = 0;
        while ((index = indexOfKernel(data, sub, index)) > -1) {
          nResult = naiveFind(data, sub, index);
          if (index != nResult) {
            PrintError(stubResult, nResult, num, "StringIndexof", data, sub);
            failCount++;
          }
          index++;
          dummy += index;
        }
        num++;
      }
      {
        int dummy = 0;
        int index = 0;
        while ((index = indexOfKernel(data, shortSub1, index)) > -1) {
          nResult = naiveFind(data, shortSub1, index);
          if (index != nResult) {
            PrintError(stubResult, nResult, num, "StringIndexof", data, shortSub1);
            failCount++;
          }
          index++;
          dummy += index;
        }
        num++;
      }
      {
        int dummy = 0;
        int index = 0;
        while ((index = indexOfKernel(data2, shortSub2, index)) > -1) {
          nResult = naiveFind(data2, shortSub2, index);
          if (index != nResult) {
            PrintError(stubResult, nResult, num, "StringIndexof", data2, shortSub2);
            failCount++;
          }
          index++;
          dummy += index;
        }
        num++;
      }
      {
        String tmp = "simple-hash:SHA-1/UTF-8";
        if (!tmp.contains("SHA-1")) {
          PrintError(stubResult, nResult, num, "StringIndexof", "simple-hash:SHA-1/UTF-8", "SHA-1");
          failCount++;
        }
        num++;
      }
    }

    report("StringIndexof                ", failCount);
  }

  /////////////////////////////////////////////////////////////////////
  //
  // From StringIndexofChar
  private static void StringIndexofChar() {
    int stubResult = 0;
    int failCount = 0;

    for (int xx = 0; xx < 2; xx++) {
      stubResult = 0;
      int nResult = 0;
      int num = 1;

      String[] latn1_short = new String[100];
      String[] latn1_sse4 = new String[100];
      String[] latn1_avx2 = new String[100];
      String[] latn1_mixedLength = new String[100];
      String[] utf16_short = new String[100];
      String[] utf16_sse4 = new String[100];
      String[] utf16_avx2 = new String[100];
      String[] utf16_mixedLength = new String[100];

      for (int i = 0; i < 100; i++) {
        latn1_short[i] = makeRndString(false, 15);
        latn1_sse4[i] = makeRndString(false, 16);
        latn1_avx2[i] = makeRndString(false, 32);
        utf16_short[i] = makeRndString(true, 7);
        utf16_sse4[i] = makeRndString(true, 8);
        utf16_avx2[i] = makeRndString(true, 16);
        latn1_mixedLength[i] = makeRndString(false, rng.nextInt(65));
        utf16_mixedLength[i] = makeRndString(true, rng.nextInt(65));
      }
      for (String what : latn1_mixedLength) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : utf16_mixedLength) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : latn1_mixedLength) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : utf16_mixedLength) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : latn1_short) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : latn1_sse4) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : latn1_avx2) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : utf16_short) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : utf16_sse4) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : utf16_avx2) {
        stubResult = indexOfKernel(what, 'a');
        nResult = naiveFind(what, 'a');
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, 'a');
          failCount++;
        }
      }
      num++;
      for (String what : latn1_short) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : latn1_sse4) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : latn1_avx2) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : utf16_short) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : utf16_sse4) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
      for (String what : utf16_avx2) {
        stubResult = indexOfKernel(what, "a");
        nResult = naiveFind(what, "a");
        if (nResult != stubResult) {
          PrintError(stubResult, nResult, num, "StringIndexofChar", what, "a");
          failCount++;
        }
      }
      num++;
    }

    report("StringIndexofChar            ", failCount);
  }

}
