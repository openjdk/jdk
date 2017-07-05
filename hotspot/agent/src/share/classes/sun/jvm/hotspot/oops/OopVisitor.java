/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.oops;

// An OopVisitor can be used to inspect all fields within an object.
// Fields include vm fields, java fields, indexable fields.

public interface OopVisitor {
  // Called before visiting an object
  public void prologue();

  // Called after visiting an object
  public void epilogue();

  public void setObj(Oop obj);

  // Returns the object being visited
  public Oop getObj();

  // Callback methods for each field type in an object
  public void doOop(OopField field, boolean isVMField);
  public void doOop(NarrowOopField field, boolean isVMField);
  public void doByte(ByteField field, boolean isVMField);
  public void doChar(CharField field, boolean isVMField);
  public void doBoolean(BooleanField field, boolean isVMField);
  public void doShort(ShortField field, boolean isVMField);
  public void doInt(IntField field, boolean isVMField);
  public void doLong(LongField field, boolean isVMField);
  public void doFloat(FloatField field, boolean isVMField);
  public void doDouble(DoubleField field, boolean isVMField);
  public void doCInt(CIntField field, boolean isVMField);
};
