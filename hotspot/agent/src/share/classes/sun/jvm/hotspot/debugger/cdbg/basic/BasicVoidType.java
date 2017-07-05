/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.debugger.cdbg.basic;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;

public class BasicVoidType extends BasicType implements VoidType {
  public BasicVoidType() {
    super("void", 0);
  }

  public VoidType asVoid() { return this; }

  public void iterateObject(Address a, ObjectVisitor v, FieldIdentifier f) {}

  protected Type createCVVariant(int cvAttributes) {
    // FIXME
    System.err.println("WARNING: Should not attempt to create const/volatile variants for void type");
    return this;
    //    throw new RuntimeException("Should not attempt to create const/volatile variants for void type");
  }

  public void visit(TypeVisitor v) {
    v.doVoidType(this);
  }
}
