/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.code;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.utilities.*;

/** NOTE that this class takes the address of a buffer. This means
    that it can read previously-generated debug information directly
    from the target VM. However, it also means that you can't create a
    "wrapper" object for a CompressedStream down in the VM. It looks
    like these are only kept persistently in OopMaps, and the code has
    been special-cased in OopMap.java to handle this. */

public class CompressedStream {
  protected Address buffer;
  protected int     position;

  /** Equivalent to CompressedStream(buffer, 0) */
  public CompressedStream(Address buffer) {
    this(buffer, 0);
  }

  public CompressedStream(Address buffer, int position) {
    this.buffer   = buffer;
    this.position = position;
  }

  public Address getBuffer() {
    return buffer;
  }

  public static final int LogBitsPerByte = 3;
  public static final int BitsPerByte = 1 << 3;

  // Positioning
  public int getPosition() {
    return position;
  }
  public void setPosition(int position) {
    this.position = position;
  }

  public void skipBytes(int bytes) {
    this.position += bytes;
  }

  public int encodeSign(int value) {
    return Unsigned5.encodeSign(value);
  }

  public int decodeSign(int value) {
    return Unsigned5.decodeSign(value);
  }

  // 32-bit self-inverse encoding of float bits
  // converts trailing zeros (common in floats) to leading zeros
  public int reverseInt(int i) {
    return Integer.reverse(i);
  }
}
