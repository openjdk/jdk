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

package sun.jvm.hotspot.debugger;

/** An extension of the Debugger interface which can be configured
    with Java type sizes to allow the sizes of primitive Java types to
    be read from the remote JVM. */

public interface JVMDebugger extends Debugger {
  /** This intent is that this can be called late in the bootstrapping
      sequence, after which the debugger can handle reading of Java
      primitive types, and thereby implement the Java functionality in
      class Address. FIXME: consider adding oop size here as well and
      removing it from the MachineDescription. */
  public void configureJavaPrimitiveTypeSizes(long jbooleanSize,
                                              long jbyteSize,
                                              long jcharSize,
                                              long jdoubleSize,
                                              long jfloatSize,
                                              long jintSize,
                                              long jlongSize,
                                              long jshortSize);
  public void putHeapConst(long heapOopSize, long narrowOopBase, int narrowOopShift);
}
