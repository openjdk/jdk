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

public abstract class Utf16 {
  // 110110XX XXXXXX 110111XX XXXXXX
  static public boolean isSurrogate(char c) {
    return (c & 0xF800) == 0xD800;
  }
  static public boolean isSurrogate1(char c) {
    return (c & 0xFC00) == 0xD800;
  }
  static public boolean isSurrogate2(char c) {
    return (c & 0xFC00) == 0xDC00;
  }
  static public int scalarValue(char c1, char c2) {
    return (((c1 & 0x3FF) << 10) | (c2 & 0x3FF)) + 0x10000;
  }
  static public char surrogate1(int c) {
    return (char)(((c - 0x10000) >> 10) | 0xD800);
  }
  static public char surrogate2(int c) {
    return (char)(((c - 0x10000) & 0x3FF) | 0xDC00);
  }
}
