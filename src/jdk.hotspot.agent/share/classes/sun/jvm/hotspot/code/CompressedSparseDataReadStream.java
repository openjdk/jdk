/*
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
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
 */

package sun.jvm.hotspot.code;

import sun.jvm.hotspot.debugger.*;

public class CompressedSparseDataReadStream extends CompressedReadStream {

  public CompressedSparseDataReadStream(Address buffer, int position) {
    super(buffer, position);
  }

  int bit_pos = 0;

  protected short buffer(int position) {
    return (short)buffer.getCIntegerAt(position, 1, true);
  }

  public byte readByteImpl() {
    if (bit_pos == 0) {
      return (byte)buffer(position++);
    }
    byte b1 = (byte)(buffer(position) << bit_pos);
    byte b2 = (byte)(buffer(++position) >> (8 - bit_pos));
    return (byte)(b1 | b2);
  }

  public int readInt() {
    if (readZero()) {
      return 0;
    }
    byte b = readByteImpl();
    int result = b & 0x3f;
    for (int i = 0; (0 != ((i == 0) ? (b & 0x40) : (b & 0x80))); i++) {
      b = readByteImpl();
      result |= ((b & 0x7f) << (6 + 7 * i));
    }
    return result;
  }

  boolean readZero() {
    if (0 != (buffer(position) & (1 << (7 - bit_pos)))) {
      return false; // not a zero data
    }
    if (++bit_pos == 8) {
      position++;
      bit_pos = 0;
    }
    return true;
  }

  public boolean readBoolean() { return readInt() != 0; }
  public byte    readByte()    { return (byte) readInt(); }
}
