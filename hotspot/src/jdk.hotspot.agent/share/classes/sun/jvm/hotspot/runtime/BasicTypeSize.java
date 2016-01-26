/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/** Encapsulates the BasicTypeSize enum in globalDefinitions.hpp in
    the VM. */

public class BasicTypeSize {
  private static boolean initialized = false;
  private static int tBooleanSize = 1;
  private static int tCharSize    = 1;
  private static int tFloatSize   = 1;
  private static int tDoubleSize  = 2;
  private static int tByteSize    = 1;
  private static int tShortSize   = 1;
  private static int tIntSize     = 1;
  private static int tLongSize    = 2;
  private static int tObjectSize  = 1;
  private static int tArraySize   = 1;
  private static int tVoidSize    = 0;

  public static int getTBooleanSize() {
    return tBooleanSize;
  }

  public static int getTCharSize() {
    return tCharSize;
  }

  public static int getTFloatSize() {
    return tFloatSize;
  }

  public static int getTDoubleSize() {
    return tDoubleSize;
  }

  public static int getTByteSize() {
    return tByteSize;
  }

  public static int getTShortSize() {
    return tShortSize;
  }

  public static int getTIntSize() {
    return tIntSize;
  }

  public static int getTLongSize() {
    return tLongSize;
  }

  public static int getTObjectSize() {
    return tObjectSize;
  }

  public static int getTArraySize() {
    return tArraySize;
  }

  public static int getTVoidSize() {
    return tVoidSize;
  }
}
