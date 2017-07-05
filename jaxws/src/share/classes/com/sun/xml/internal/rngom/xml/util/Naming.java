/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.rngom.xml.util;

public class Naming {

  private Naming() { }

  private static final int CT_NAME = 1;
  private static final int CT_NMSTRT = 2;

  private static final String nameStartSingles =
  "\u003a\u005f\u0386\u038c\u03da\u03dc\u03de\u03e0\u0559\u06d5\u093d\u09b2" +
  "\u0a5e\u0a8d\u0abd\u0ae0\u0b3d\u0b9c\u0cde\u0e30\u0e84\u0e8a\u0e8d\u0ea5" +
  "\u0ea7\u0eb0\u0ebd\u1100\u1109\u113c\u113e\u1140\u114c\u114e\u1150\u1159" +
  "\u1163\u1165\u1167\u1169\u1175\u119e\u11a8\u11ab\u11ba\u11eb\u11f0\u11f9" +
  "\u1f59\u1f5b\u1f5d\u1fbe\u2126\u212e\u3007";
  private static final String nameStartRanges =
  "\u0041\u005a\u0061\u007a\u00c0\u00d6\u00d8\u00f6\u00f8\u00ff\u0100\u0131" +
  "\u0134\u013e\u0141\u0148\u014a\u017e\u0180\u01c3\u01cd\u01f0\u01f4\u01f5" +
  "\u01fa\u0217\u0250\u02a8\u02bb\u02c1\u0388\u038a\u038e\u03a1\u03a3\u03ce" +
  "\u03d0\u03d6\u03e2\u03f3\u0401\u040c\u040e\u044f\u0451\u045c\u045e\u0481" +
  "\u0490\u04c4\u04c7\u04c8\u04cb\u04cc\u04d0\u04eb\u04ee\u04f5\u04f8\u04f9" +
  "\u0531\u0556\u0561\u0586\u05d0\u05ea\u05f0\u05f2\u0621\u063a\u0641\u064a" +
  "\u0671\u06b7\u06ba\u06be\u06c0\u06ce\u06d0\u06d3\u06e5\u06e6\u0905\u0939" +
  "\u0958\u0961\u0985\u098c\u098f\u0990\u0993\u09a8\u09aa\u09b0\u09b6\u09b9" +
  "\u09dc\u09dd\u09df\u09e1\u09f0\u09f1\u0a05\u0a0a\u0a0f\u0a10\u0a13\u0a28" +
  "\u0a2a\u0a30\u0a32\u0a33\u0a35\u0a36\u0a38\u0a39\u0a59\u0a5c\u0a72\u0a74" +
  "\u0a85\u0a8b\u0a8f\u0a91\u0a93\u0aa8\u0aaa\u0ab0\u0ab2\u0ab3\u0ab5\u0ab9" +
  "\u0b05\u0b0c\u0b0f\u0b10\u0b13\u0b28\u0b2a\u0b30\u0b32\u0b33\u0b36\u0b39" +
  "\u0b5c\u0b5d\u0b5f\u0b61\u0b85\u0b8a\u0b8e\u0b90\u0b92\u0b95\u0b99\u0b9a" +
  "\u0b9e\u0b9f\u0ba3\u0ba4\u0ba8\u0baa\u0bae\u0bb5\u0bb7\u0bb9\u0c05\u0c0c" +
  "\u0c0e\u0c10\u0c12\u0c28\u0c2a\u0c33\u0c35\u0c39\u0c60\u0c61\u0c85\u0c8c" +
  "\u0c8e\u0c90\u0c92\u0ca8\u0caa\u0cb3\u0cb5\u0cb9\u0ce0\u0ce1\u0d05\u0d0c" +
  "\u0d0e\u0d10\u0d12\u0d28\u0d2a\u0d39\u0d60\u0d61\u0e01\u0e2e\u0e32\u0e33" +
  "\u0e40\u0e45\u0e81\u0e82\u0e87\u0e88\u0e94\u0e97\u0e99\u0e9f\u0ea1\u0ea3" +
  "\u0eaa\u0eab\u0ead\u0eae\u0eb2\u0eb3\u0ec0\u0ec4\u0f40\u0f47\u0f49\u0f69" +
  "\u10a0\u10c5\u10d0\u10f6\u1102\u1103\u1105\u1107\u110b\u110c\u110e\u1112" +
  "\u1154\u1155\u115f\u1161\u116d\u116e\u1172\u1173\u11ae\u11af\u11b7\u11b8" +
  "\u11bc\u11c2\u1e00\u1e9b\u1ea0\u1ef9\u1f00\u1f15\u1f18\u1f1d\u1f20\u1f45" +
  "\u1f48\u1f4d\u1f50\u1f57\u1f5f\u1f7d\u1f80\u1fb4\u1fb6\u1fbc\u1fc2\u1fc4" +
  "\u1fc6\u1fcc\u1fd0\u1fd3\u1fd6\u1fdb\u1fe0\u1fec\u1ff2\u1ff4\u1ff6\u1ffc" +
  "\u212a\u212b\u2180\u2182\u3041\u3094\u30a1\u30fa\u3105\u312c\uac00\ud7a3" +
  "\u4e00\u9fa5\u3021\u3029";
  private static final String nameSingles =
  "\u002d\u002e\u05bf\u05c4\u0670\u093c\u094d\u09bc\u09be\u09bf\u09d7\u0a02" +
  "\u0a3c\u0a3e\u0a3f\u0abc\u0b3c\u0bd7\u0d57\u0e31\u0eb1\u0f35\u0f37\u0f39" +
  "\u0f3e\u0f3f\u0f97\u0fb9\u20e1\u3099\u309a\u00b7\u02d0\u02d1\u0387\u0640" +
  "\u0e46\u0ec6\u3005";
  private static final String nameRanges =
  "\u0300\u0345\u0360\u0361\u0483\u0486\u0591\u05a1\u05a3\u05b9\u05bb\u05bd" +
  "\u05c1\u05c2\u064b\u0652\u06d6\u06dc\u06dd\u06df\u06e0\u06e4\u06e7\u06e8" +
  "\u06ea\u06ed\u0901\u0903\u093e\u094c\u0951\u0954\u0962\u0963\u0981\u0983" +
  "\u09c0\u09c4\u09c7\u09c8\u09cb\u09cd\u09e2\u09e3\u0a40\u0a42\u0a47\u0a48" +
  "\u0a4b\u0a4d\u0a70\u0a71\u0a81\u0a83\u0abe\u0ac5\u0ac7\u0ac9\u0acb\u0acd" +
  "\u0b01\u0b03\u0b3e\u0b43\u0b47\u0b48\u0b4b\u0b4d\u0b56\u0b57\u0b82\u0b83" +
  "\u0bbe\u0bc2\u0bc6\u0bc8\u0bca\u0bcd\u0c01\u0c03\u0c3e\u0c44\u0c46\u0c48" +
  "\u0c4a\u0c4d\u0c55\u0c56\u0c82\u0c83\u0cbe\u0cc4\u0cc6\u0cc8\u0cca\u0ccd" +
  "\u0cd5\u0cd6\u0d02\u0d03\u0d3e\u0d43\u0d46\u0d48\u0d4a\u0d4d\u0e34\u0e3a" +
  "\u0e47\u0e4e\u0eb4\u0eb9\u0ebb\u0ebc\u0ec8\u0ecd\u0f18\u0f19\u0f71\u0f84" +
  "\u0f86\u0f8b\u0f90\u0f95\u0f99\u0fad\u0fb1\u0fb7\u20d0\u20dc\u302a\u302f" +
  "\u0030\u0039\u0660\u0669\u06f0\u06f9\u0966\u096f\u09e6\u09ef\u0a66\u0a6f" +
  "\u0ae6\u0aef\u0b66\u0b6f\u0be7\u0bef\u0c66\u0c6f\u0ce6\u0cef\u0d66\u0d6f" +
  "\u0e50\u0e59\u0ed0\u0ed9\u0f20\u0f29\u3031\u3035\u309d\u309e\u30fc\u30fe";

  private final static byte[][] charTypeTable;

  static {
    charTypeTable = new byte[256][];
    for (int i = 0; i < nameSingles.length(); i++)
      setCharType(nameSingles.charAt(i), CT_NAME);
    for (int i = 0; i < nameRanges.length(); i += 2)
      setCharType(nameRanges.charAt(i), nameRanges.charAt(i + 1), CT_NAME);
    for (int i = 0; i < nameStartSingles.length(); i++)
      setCharType(nameStartSingles.charAt(i), CT_NMSTRT);
    for (int i = 0; i < nameStartRanges.length(); i += 2)
      setCharType(nameStartRanges.charAt(i), nameStartRanges.charAt(i + 1),
                  CT_NMSTRT);
    byte[] other = new byte[256];
    for (int i = 0; i < 256; i++)
      if (charTypeTable[i] == null)
        charTypeTable[i] = other;
  }

  private static void setCharType(char c, int type) {
    int hi = c >> 8;
    if (charTypeTable[hi] == null)
      charTypeTable[hi] = new byte[256];
    charTypeTable[hi][c & 0xFF] = (byte)type;
  }

  private static void setCharType(char min, char max, int type) {
    byte[] shared = null;
    do {
      if ((min & 0xFF) == 0) {
        for (; min + 0xFF <= max; min += 0x100) {
          if (shared == null) {
            shared = new byte[256];
            for (int i = 0; i < 256; i++)
              shared[i] = (byte)type;
          }
          charTypeTable[min >> 8] = shared;
          if (min + 0xFF == max)
            return;
        }
      }
      setCharType(min, type);
    } while (min++ != max);
  }

  private static boolean isNameStartChar(char c) {
    return charTypeTable[c >> 8][c & 0xff] == CT_NMSTRT;
  }

  private static boolean isNameStartCharNs(char c) {
    return isNameStartChar(c) && c != ':';
  }

  private static boolean isNameChar(char c) {
    return charTypeTable[c >> 8][c & 0xff] != 0;
  }

  private static boolean isNameCharNs(char c) {
    return isNameChar(c) && c != ':';
  }

  public static boolean isName(String s) {
    int len = s.length();
    if (len == 0)
      return false;
    if (!isNameStartChar(s.charAt(0)))
      return false;
    for (int i = 1; i < len; i++)
      if (!isNameChar(s.charAt(i)))
        return false;
    return true;
  }

  public static boolean isNmtoken(String s) {
    int len = s.length();
    if (len == 0)
      return false;
    for (int i = 0; i < len; i++)
      if (!isNameChar(s.charAt(i)))
        return false;
    return true;
  }

  public static boolean isNcname(String s) {
    int len = s.length();
    if (len == 0)
      return false;
    if (!isNameStartCharNs(s.charAt(0)))
      return false;
    for (int i = 1; i < len; i++)
      if (!isNameCharNs(s.charAt(i)))
        return false;
    return true;
  }

  public static boolean isQname(String s) {
    int len = s.length();
    if (len == 0)
      return false;
    if (!isNameStartCharNs(s.charAt(0)))
      return false;
    for (int i = 1; i < len; i++) {
      char c = s.charAt(i);
      if (!isNameChar(c)) {
        if (c == ':' && ++i < len && isNameStartCharNs(s.charAt(i))) {
          for (++i; i < len; i++)
            if (!isNameCharNs(s.charAt(i)))
              return false;
          return true;
        }
        return false;
      }
    }
    return true;
  }


}
