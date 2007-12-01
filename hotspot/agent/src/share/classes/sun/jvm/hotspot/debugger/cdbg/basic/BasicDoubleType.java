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

public class BasicDoubleType extends BasicType implements DoubleType {
  public BasicDoubleType(String name, int size) {
    this(name, size, 0);
  }

  private BasicDoubleType(String name, int size, int cvAttributes) {
    super(name, size, cvAttributes);
  }

  public DoubleType asDouble() { return this; }

  public void iterateObject(Address a, ObjectVisitor v, FieldIdentifier f) {
    v.doDouble(f, a.getJDoubleAt(0));
  }

  protected Type createCVVariant(int cvAttributes) {
    return new BasicDoubleType(getName(), getSize(), cvAttributes);
  }

  public void visit(TypeVisitor v) {
    v.doDoubleType(this);
  }
}
