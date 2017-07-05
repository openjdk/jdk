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

import java.io.UnsupportedEncodingException;

public abstract class EncodingMap {
  private static final String[] aliases = {
    "UTF-8", "UTF8",
    "UTF-16", "Unicode",
    "UTF-16BE", "UnicodeBigUnmarked",
    "UTF-16LE", "UnicodeLittleUnmarked",
    "US-ASCII", "ASCII",
    "TIS-620", "TIS620"
  };

  static public String getJavaName(String enc) {
    try {
      "x".getBytes(enc);
    }
    catch (UnsupportedEncodingException e) {
      for (int i = 0; i < aliases.length; i += 2) {
        if (enc.equalsIgnoreCase(aliases[i])) {
          try {
            "x".getBytes(aliases[i + 1]);
            return aliases[i + 1];
          }
          catch (UnsupportedEncodingException e2) {}
        }
      }
    }
    return enc;
  }

  static public void main(String[] args) {
    System.err.println(getJavaName(args[0]));
  }
}
