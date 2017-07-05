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
package com.sun.xml.internal.rngom.util;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.UnsupportedEncodingException;

public class Uri {
  private static String utf8 = "UTF-8";

  public static boolean isValid(String s) {
    return isValidPercent(s) && isValidFragment(s) && isValidScheme(s);
  }

  private static final String HEX_DIGITS = "0123456789abcdef";

  public static String escapeDisallowedChars(String s) {
    StringBuffer buf = null;
    int len = s.length();
    int done = 0;
    for (;;) {
      int i = done;
      for (;;) {
        if (i == len) {
          if (done == 0)
            return s;
          break;
        }
        if (isExcluded(s.charAt(i)))
          break;
        i++;
      }
      if (buf == null)
        buf = new StringBuffer();
      if (i > done) {
        buf.append(s.substring(done, i));
        done = i;
      }
      if (i == len)
        break;
      for (i++; i < len && isExcluded(s.charAt(i)); i++)
        ;
      String tem = s.substring(done, i);
      byte[] bytes;
      try {
        bytes = tem.getBytes(utf8);
      }
      catch (UnsupportedEncodingException e) {
        utf8 = "UTF8";
        try {
          bytes = tem.getBytes(utf8);
        }
        catch (UnsupportedEncodingException e2) {
          // Give up
          return s;
        }
      }
      for (int j = 0; j < bytes.length; j++) {
        buf.append('%');
        buf.append(HEX_DIGITS.charAt((bytes[j] & 0xFF) >> 4));
        buf.append(HEX_DIGITS.charAt(bytes[j] & 0xF));
      }
      done = i;
    }
    return buf.toString();
  }

  private static final String excluded = "<>\"{}|\\^`";

  private static boolean isExcluded(char c) {
    return c <= 0x20 || c >= 0x7F || excluded.indexOf(c) >= 0;
  }

  private static boolean isAlpha(char c) {
    return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
  }

  private static boolean isHexDigit(char c) {
    return ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F') || isDigit(c);
  }

  private static boolean isDigit(char c) {
    return '0' <= c && c <= '9';
  }

  private static boolean isSchemeChar(char c) {
    return isAlpha(c) || isDigit(c) || c == '+' || c == '-' || c =='.';
  }

  private static boolean isValidPercent(String s) {
    int len = s.length();
    for (int i = 0; i < len; i++)
      if (s.charAt(i) == '%') {
        if (i + 2 >= len)
          return false;
        else if (!isHexDigit(s.charAt(i + 1))
                 || !isHexDigit(s.charAt(i + 2)))
          return false;
      }
    return true;
  }

  private static boolean isValidFragment(String s) {
    int i = s.indexOf('#');
    return i < 0 || s.indexOf('#', i + 1) < 0;
  }

  private static boolean isValidScheme(String s) {
    if (!isAbsolute(s))
      return true;
    int i = s.indexOf(':');
    if (i == 0
        || i + 1 == s.length()
        || !isAlpha(s.charAt(0)))
      return false;
    while (--i > 0)
      if (!isSchemeChar(s.charAt(i)))
        return false;
    return true;
  }

  public static String resolve(String baseUri, String uriReference) {
    if (!isAbsolute(uriReference) && baseUri != null && isAbsolute(baseUri)) {
      try {
        return new URL(new URL(baseUri), uriReference).toString();
      }
      catch (MalformedURLException e) { }
    }
    return uriReference;
  }

  public static boolean hasFragmentId(String uri) {
    return uri.indexOf('#') >= 0;
  }

  public static boolean isAbsolute(String uri) {
    int i = uri.indexOf(':');
    if (i < 0)
      return false;
    while (--i >= 0) {
      switch (uri.charAt(i)) {
      case '#':
      case '/':
      case '?':
        return false;
      }
    }
    return true;
  }
}
