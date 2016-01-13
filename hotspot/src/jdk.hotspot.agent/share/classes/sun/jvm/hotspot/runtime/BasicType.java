/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

/** Encapsulates the BasicType enum in globalDefinitions.hpp in the
    VM. */

public class BasicType {
  public static final int tBoolean  = 4;
  public static final int tChar     = 5;
  public static final int tFloat    = 6;
  public static final int tDouble   = 7;
  public static final int tByte     = 8;
  public static final int tShort    = 9;
  public static final int tInt      = 10;
  public static final int tLong     = 11;
  public static final int tObject   = 12;
  public static final int tArray    = 13;
  public static final int tVoid     = 14;
  public static final int tAddress  = 15;
  public static final int tConflict = 16;
  public static final int tIllegal  = 99;

  public static final BasicType T_BOOLEAN = new BasicType(tBoolean);
  public static final BasicType T_CHAR = new BasicType(tChar);
  public static final BasicType T_FLOAT = new BasicType(tFloat);
  public static final BasicType T_DOUBLE = new BasicType(tDouble);
  public static final BasicType T_BYTE = new BasicType(tByte);
  public static final BasicType T_SHORT = new BasicType(tShort);
  public static final BasicType T_INT = new BasicType(tInt);
  public static final BasicType T_LONG = new BasicType(tLong);
  public static final BasicType T_OBJECT = new BasicType(tObject);
  public static final BasicType T_ARRAY = new BasicType(tArray);
  public static final BasicType T_VOID = new BasicType(tVoid);
  public static final BasicType T_ADDRESS = new BasicType(tAddress);
  public static final BasicType T_CONFLICT = new BasicType(tConflict);
  public static final BasicType T_ILLEGAL = new BasicType(tIllegal);

  public static int getTBoolean() {
    return tBoolean;
  }

  public static int getTChar() {
    return tChar;
  }

  public static int getTFloat() {
    return tFloat;
  }

  public static int getTDouble() {
    return tDouble;
  }

  public static int getTByte() {
    return tByte;
  }

  public static int getTShort() {
    return tShort;
  }

  public static int getTInt() {
    return tInt;
  }

  public static int getTLong() {
    return tLong;
  }

  public static int getTObject() {
    return tObject;
  }

  public static int getTArray() {
    return tArray;
  }

  public static int getTVoid() {
    return tVoid;
  }

  public static int getTAddress() {
    return tAddress;
  }

  /** For stack value type with conflicting contents */
  public static int getTConflict() {
    return tConflict;
  }

  public static int getTIllegal() {
    return tIllegal;
  }

  public static BasicType charToBasicType(char c) {
    switch( c ) {
    case 'B': return T_BYTE;
    case 'C': return T_CHAR;
    case 'D': return T_DOUBLE;
    case 'F': return T_FLOAT;
    case 'I': return T_INT;
    case 'J': return T_LONG;
    case 'S': return T_SHORT;
    case 'Z': return T_BOOLEAN;
    case 'V': return T_VOID;
    case 'L': return T_OBJECT;
    case '[': return T_ARRAY;
    }
    return T_ILLEGAL;
  }

  public static int charToType(char c) {
    return charToBasicType(c).getType();
  }

  public int getType() {
    return type;
  }

  //-- Internals only below this point
  private BasicType(int type) {
    this.type = type;
  }

  private int type;
}
