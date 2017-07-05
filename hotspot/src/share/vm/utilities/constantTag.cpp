/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_constantTag.cpp.incl"

#ifndef PRODUCT

void constantTag::print_on(outputStream* st) const {
  switch (_tag) {
    case JVM_CONSTANT_Class :
      st->print("Class");
      break;
    case JVM_CONSTANT_Fieldref :
      st->print("Field");
      break;
    case JVM_CONSTANT_Methodref :
      st->print("Method");
      break;
    case JVM_CONSTANT_InterfaceMethodref :
      st->print("InterfaceMethod");
      break;
    case JVM_CONSTANT_String :
      st->print("String");
      break;
    case JVM_CONSTANT_Integer :
      st->print("Integer");
      break;
    case JVM_CONSTANT_Float :
      st->print("Float");
      break;
    case JVM_CONSTANT_Long :
      st->print("Long");
      break;
    case JVM_CONSTANT_Double :
      st->print("Double");
      break;
    case JVM_CONSTANT_NameAndType :
      st->print("NameAndType");
      break;
    case JVM_CONSTANT_Utf8 :
      st->print("Utf8");
      break;
    case JVM_CONSTANT_UnresolvedClass :
      st->print("Unresolved class");
      break;
    case JVM_CONSTANT_ClassIndex :
      st->print("Unresolved class index");
      break;
    case JVM_CONSTANT_UnresolvedString :
      st->print("Unresolved string");
      break;
    case JVM_CONSTANT_StringIndex :
      st->print("Unresolved string index");
      break;
    default:
      ShouldNotReachHere();
      break;
  }
}

#endif // PRODUCT
