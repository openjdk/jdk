/*
 * Copyright (c) 2024, Intel Corporation. All rights reserved.
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
 * @bug 8320448
 * @summary test String indexOf() intrinsic
 * @run driver IndexOf
 */

/*
 * @test
 * @bug 8320448
 * @summary test String indexOf() intrinsic
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xcomp -XX:-TieredCompilation -XX:UseAVX=2 -XX:+UnlockDiagnosticVMOptions -XX:+EnableX86ECoreOpts IndexOf
 */

 public class IndexOf {
  final int scope = 32*2+16+8;
  final char a, aa, b, c, d;
  enum Encoding {LL, UU, UL; }
  final Encoding ae;
  int failures;

  IndexOf(Encoding _ae) {
      failures = 0;
      ae = _ae;
      switch (ae) {
          case LL:
              a = 'a';
              aa = a;
              b = 'b';
              c = 'c';
              d = 'd';
              break;
          case UU:
              a = '\u0061';
              aa = a;
              b = '\u0062';
              c = '\u1063';
              d = '\u0064';
              break;
          default: //case UL:
              a = 'a';
              aa = '\u1061';
              b = 'b';
              c = 'c';
              d = 'd';
              break;
      }
  }

  // needle    =~ /ab*d/
  // badNeedle =~ /ab*db*d/
  interface Append {void append(int pos, char cc);}
  String newNeedle(int size, int badPosition) {
      if (size<2) {throw new RuntimeException("Fix testcase "+size);}

      StringBuilder needle = new StringBuilder(size);
      Append n = (int pos, char cc) -> {
          if (pos == badPosition)
              needle.append(c);
          else
              needle.append(cc);
      };

      n.append(0, a);
      for (int i=1; i<size-1; i++) {
          n.append(i, b);
      }
      n.append(size-1, d);

      return needle.toString();
  }

  // haystack  =~ /a*{needle}d*/
  String newHaystack(int size, String needle, int nPosition) {
      if (nPosition+needle.length()>size) {throw new RuntimeException("Fix testcase "+nPosition+" "+needle.length()+" "+size);}
      StringBuilder haystack = new StringBuilder(size);
      int i = 0;
      for (; i<nPosition; i++) {
          haystack.append(aa);
      }
      haystack.append(needle);
      i += needle.length();
      for (; i<size; i++) {
          haystack.append(d);
      }
      return haystack.toString();
  }

  // haystack =~ /a*{needle}+b*/
  String newHaystackRepeat(int size, String needle, int nPosition) {
      if (nPosition+needle.length()>size) {throw new RuntimeException("Fix testcase "+nPosition+" "+needle.length()+" "+size);}
      StringBuilder haystack = new StringBuilder(size);
      int i = 0;
      for (; i<nPosition; i++) {
          haystack.append(aa);
      }
      for (; i< nPosition+needle.length(); i += needle.length()) {
          haystack.append(needle);
      }
      for (; i<size; i++) {
          haystack.append(d);
      }
      return haystack.toString();
  }

  public static void main(String[] args) {
      int failures = 0;
      for (Encoding ae : Encoding.values()) {
          failures += (new IndexOf(ae))
              .test0()
              .test1()
              .test2()
              .test3()
              .test4()
              .failures;
      }
      if (failures != 0) {
          throw new RuntimeException("IndexOf test failed.");
      }
  }

  // Need to disable checks in String.java if intrinsic is to be tested
  IndexOf test0() { // Test 'trivial cases'
      // if (0==needle_len) return haystack_off;
      if (3 != "Hello".indexOf("", 3)) {
          System.out.println("FAILED: if (0==needle_len) return haystack_off");
          failures++;
      }
      //if (0==haystack_len) return -1;
      if (-1 != "".indexOf("Hello", 3)) {
          System.out.println("FAILED: if (0==haystack_len) return -1");
          failures++;
      }
      //if (needle_len>haystack_len) return -1;
      if (-1 != "Hello".indexOf("HelloWorld", 3)) {
          System.out.println("FAILED: if (needle_len>haystack_len) return -1");
          failures++;
      }
      return this;
  }

  IndexOf test1() { // Test expected to find one needle
      for (int nSize = 2; nSize<scope; nSize++) {
          String needle = newNeedle(nSize, -1);
          for (int hSize = nSize; hSize<scope; hSize++) {
              for (int i = 0; i<hSize-nSize; i++) {
                  String haystack = newHaystack(hSize, needle, i);
                  for (int j = 0; j<=i; j++) {
                      int found = haystack.indexOf(needle, j);
                      if (i != found) {
                          System.out.println("("+ae.name()+")(T1) Trying needle["+nSize+"] in haystack["+hSize+"] at offset["+i+"]");
                          System.out.println("    FAILED: Found " + needle + "@" + found + " in " + haystack + " from ["+j+"]");
                          failures++;
                      }
                  }
              }
          }
      }
      return this;
  }

  IndexOf test2() { // Test needle with one mismatched character
      for (int nSize = 2; nSize<scope; nSize++) {
          for (int hSize = nSize; hSize<scope; hSize++) {
              String needle = newNeedle(nSize, -1);
              for (int badPosition = 0; badPosition < nSize; badPosition+=1) {
                  String badNeedle = newNeedle(nSize, badPosition);
                  for (int i = 0; i<hSize-nSize; i++) {
                      String haystack = newHaystack(hSize, needle, i);
                      int found = haystack.indexOf(badNeedle, 1);
                      if (-1 != found) {
                          System.out.println("("+ae.name()+")(T2) Trying bad needle["+nSize+"]["+badPosition+"] in haystack["+hSize+"] at offset["+i+"]");
                          System.out.println("    FAILED: False " + found + " " + haystack + "["+needle+"]["+badNeedle+"]");
                          failures++;
                      }
                  }
              }
          }
      }
      return this;
  }

  IndexOf test3() { // Test expected to find first of the repeated needles
      for (int nSize = 2; nSize<scope; nSize++) {
          String needle = newNeedle(nSize, -1);
          for (int hSize = nSize; hSize<scope; hSize++) {
              for (int i = 0; i<hSize-nSize; i++) {
                  String haystack = newHaystackRepeat(hSize, needle, i);
                  for (int j = 0; j<=i; j++) {
                      int found = haystack.indexOf(needle, j);
                      if (i != found) {
                          System.out.println("("+ae.name()+")(T3) Trying repeaded needle["+nSize+"] in haystack["+hSize+"] at offset["+i+"]");
                          System.out.println("    FAILED: " + found + " " + haystack + "["+needle+"]");
                          failures++;
                      }
                  }
              }
          }
      }
      return this;
  }

  IndexOf test4() { // Test needle at unreachable offset
      for (int nSize = 2; nSize<scope; nSize++) {
          String needle = newNeedle(nSize, -1);
          for (int hSize = nSize; hSize<scope; hSize++) {
              for (int i = 0; i<hSize-nSize; i++) {
                  String haystack = newHaystack(hSize, needle, i);
                  // prefix lookup
                  for (int j = nSize-1; j<i+nSize; j++) {
                      int found = haystack.indexOf(needle, 0, j);
                      if (-1 != found) {
                          System.out.println("("+ae.name()+")(T4) Trying needle["+nSize+"] at offset ["+i+"] in haystack["+hSize+"] upto ["+j+"]");
                          System.out.println("    FAILED: False " + found + " " + haystack + "["+needle+"]");
                          failures++;
                      }
                  }

                  // sufix lookup
                  for (int j = i+1; j<hSize; j++) {
                      int found = haystack.indexOf(needle, j);
                      if (-1 != found) {
                          System.out.println("("+ae.name()+")(T4) Trying needle["+nSize+"] at offset ["+i+"] in haystack["+hSize+"] from ["+j+"]");
                          System.out.println("    FAILED: False " + found + " " + haystack + "["+needle+"]");
                          failures++;
                      }
                  }
              }
          }
      }
      return this;
  }
}