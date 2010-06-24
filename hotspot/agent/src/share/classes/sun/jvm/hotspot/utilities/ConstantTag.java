/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

public class ConstantTag {
  // These replicated from the VM to save space
  private static int JVM_CONSTANT_Utf8                    = 1;
  private static int JVM_CONSTANT_Unicode                 = 2; // unused
  private static int JVM_CONSTANT_Integer                 = 3;
  private static int JVM_CONSTANT_Float                   = 4;
  private static int JVM_CONSTANT_Long                    = 5;
  private static int JVM_CONSTANT_Double                  = 6;
  private static int JVM_CONSTANT_Class                   = 7;
  private static int JVM_CONSTANT_String                  = 8;
  private static int JVM_CONSTANT_Fieldref                = 9;
  private static int JVM_CONSTANT_Methodref               = 10;
  private static int JVM_CONSTANT_InterfaceMethodref      = 11;
  private static int JVM_CONSTANT_NameAndType             = 12;
  private static int JVM_CONSTANT_Invalid                 = 0;   // For bad value initialization
  private static int JVM_CONSTANT_UnresolvedClass         = 100; // Temporary tag until actual use
  private static int JVM_CONSTANT_ClassIndex              = 101; // Temporary tag while constructing constant pool
  private static int JVM_CONSTANT_UnresolvedString        = 102; // Temporary tag until actual use
  private static int JVM_CONSTANT_StringIndex             = 103; // Temporary tag while constructing constant pool
  private static int JVM_CONSTANT_UnresolvedClassInError  = 104; // Resolution failed

  private byte tag;

  public ConstantTag(byte tag) {
    this.tag = tag;
  }

  public boolean isKlass()            { return tag == JVM_CONSTANT_Class; }
  public boolean isField ()           { return tag == JVM_CONSTANT_Fieldref; }
  public boolean isMethod()           { return tag == JVM_CONSTANT_Methodref; }
  public boolean isInterfaceMethod()  { return tag == JVM_CONSTANT_InterfaceMethodref; }
  public boolean isString()           { return tag == JVM_CONSTANT_String; }
  public boolean isInt()              { return tag == JVM_CONSTANT_Integer; }
  public boolean isFloat()            { return tag == JVM_CONSTANT_Float; }
  public boolean isLong()             { return tag == JVM_CONSTANT_Long; }
  public boolean isDouble()           { return tag == JVM_CONSTANT_Double; }
  public boolean isNameAndType()      { return tag == JVM_CONSTANT_NameAndType; }
  public boolean isUtf8()             { return tag == JVM_CONSTANT_Utf8; }

  public boolean isInvalid()          { return tag == JVM_CONSTANT_Invalid; }

  public boolean isUnresolvedKlass()  {
    return tag == JVM_CONSTANT_UnresolvedClass || tag == JVM_CONSTANT_UnresolvedClassInError;
  }
  public boolean isUnresolveKlassInError()  { return tag == JVM_CONSTANT_UnresolvedClassInError; }
  public boolean isKlassIndex()             { return tag == JVM_CONSTANT_ClassIndex; }
  public boolean isUnresolvedString()       { return tag == JVM_CONSTANT_UnresolvedString; }
  public boolean isStringIndex()            { return tag == JVM_CONSTANT_StringIndex; }

  public boolean isKlassReference()   { return isKlassIndex() || isUnresolvedKlass(); }
  public boolean isFieldOrMethod()    { return isField() || isMethod() || isInterfaceMethod(); }
  public boolean isSymbol()           { return isUtf8(); }
}
