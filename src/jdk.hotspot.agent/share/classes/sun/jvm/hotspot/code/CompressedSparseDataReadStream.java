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
    curr_byte_ = (byte) read();
  }

  byte curr_byte_ = 0;
  int  byte_pos_  = 0;

  public byte readByteImpl() {
    byte b = (byte) (curr_byte_ << byte_pos_);
    curr_byte_ = (byte) read();
    if (byte_pos_ > 0) {
      b |= (byte) ((0xFF & curr_byte_) >> (8 - byte_pos_));
    }
    return b;
  }

  boolean readZero() {
    if (0 != (curr_byte_ & (1 << (7 - byte_pos_)))) {
      return false;
    }
    if (++byte_pos_ == 8) {
      byte_pos_ = 0;
      curr_byte_ = (byte) read();
    }
    return true;
  }

  public int readInt() {
    if (readZero()) {
      return 0;
    }
    byte b = readByteImpl();
    int result = b & 0x3f;
    for (int i = 0; 0 != ((i == 0) ? (b & 0x40) : (b & 0x80)); i++) {
        b = readByteImpl();
        result |= ((b & 0x7f) << (6 + 7 * i));
    }
    return result;
  }

  public boolean readBoolean() { return readInt() != 0; }
  public byte    readByte()    { return (byte) readInt(); }
}
