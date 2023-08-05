/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;

import sun.jvm.hotspot.debugger.*;

/**
 * Decompression algorithm from utilities/unsigned5.hpp.
 */
public class Unsigned5 {
  public static final int LogBitsPerByte = 3;
  public static final int BitsPerByte = 1 << 3;

  // Constants for UNSIGNED5 coding of Pack200
  private static final int lg_H = 6;     // log-base-2 of H (lg 64 == 6)
  private static final int H = 1<<lg_H;  // number of "high" bytes (64)
  private static final int X = 1;  // there is one excluded byte ('\0')
  private static final int MAX_b = (1<<BitsPerByte)-1;  // largest byte value
  private static final int L = (MAX_b+1)-X-H;  // number of "low" bytes (191)
  public static final int MAX_LENGTH = 5;  // lengths are in [1..5]

  // Note:  Previous versions of HotSpot used X=0 (not 1) and L=192 (not 191)
  //
  // Using this SA code on old versions of HotSpot, or older SA code
  // on newer versions of HotSpot, will decode compressed data
  // wrongly.  One might consider using vmStructs to communicate this
  // particular change between the SA and VM, but it is mostly futile.
  // There are a myriad of new changes in any version of HotSpot.  You
  // have to use the right SA and VM versions together.

  public interface GetByte<ARR> {
      short getByte(ARR array, int position);
  }
  public interface SetPosition<ARR> {
      void setPosition(ARR array, int position);
  }

  // UNSIGNED5::read_uint(_buffer, &_position, limit=0)
  // In C++ this is a generic algorithm, templated with "holes"
  // for array (ARR), offset (OFF), and fetch behavior (GET).
  // In addition, the position is updated by reference.
  // Let us mimic these conditions with two lambdas, both
  // on the ARR parameter.  We will hardwire the position
  // type (OFF) to int (sorry, not long), and omit the extra
  // limit feature.
  public static
  <ARR> long readUint(ARR base, int position,
                      GetByte<ARR> getByte,
                      SetPosition<ARR> setPosition) {
    int pos = position;
    int b_0 = getByte.getByte(base, pos);
    int sum = b_0 - X;
    // VM throws assert if b0<X; we just return -1 here instead
    if (sum < L) {  // common case
      setPosition.setPosition(base, pos+1);
      return Integer.toUnsignedLong(sum);
    }
    // must collect more bytes:  b[1]...b[4]
    int lg_H_i = lg_H;  // lg(H)*i == lg(H^^i)
    for (int i = 1; ; i++) {  // for i in [1..4]
      int b_i = getByte.getByte(base, pos + i);
      if (b_i < X) {  // avoid excluded bytes
        // VM throws assert here; should not happen
        setPosition.setPosition(base, pos+i);  // do not consume the bad byte
        return Integer.toUnsignedLong(sum);  // return whatever we have parsed so far
      }
      sum += (b_i - X) << lg_H_i;  // sum += (b[i]-X)*(64^^i)
      if (b_i < X+L || i == MAX_LENGTH-1) {
        setPosition.setPosition(base, pos+i+1);
        return Integer.toUnsignedLong(sum);
      }
      lg_H_i += lg_H;
    }
  }

  // 32-bit one-to-one sign encoding taken from Pack200
  // converts leading sign bits into leading zeros with trailing sign bit
  // uint32_t encode_sign(int32_t value)
  public static int encodeSign(int value) {
    return (value << 1) ^ (value >> 31);
  }

  // int32_t decode_sign(uint32_t value)
  public static int decodeSign(int value) {
    return (value >>> 1) ^ -(value & 1);
  }

  //--------------------------------------------------------------------------------
  // constructor and instance methods for convenience

  // You can read and print a stream directly from memory if you like.
  // First wrap these up, then call read or print.
  private final Address base;
  private final int limit;

  // There is no C++ instance of UNSIGNED5 but it seems useful to
  // allow this class to serve as a holder for an address and optional
  // limit, to point at a place where U5 encodings might be stored.
  // Compare with Unsigned5::Reader(ARR array, OFF limit = 0).
  public Unsigned5(Address base) {
    this(base, 0);  // limit=0 means unlimited (proceed with caution)
  }
  public Unsigned5(Address base, int limit) {
    this.base = base;
    this.limit = limit;
  }

  public Address base() { return base; }
  public short getByte(int pos) {
    return (short) base.getCIntegerAt(pos, 1, true);
  }

  // An UNSIGNED5::Reader gadget has a settable, auto-incremented
  // position field and can read through a stream of encoded values.
  // Java can model this as an inner class: var r = myU5.new Reader()
  // or var r = new Unsigned5(myaddr).new Reader()
  public class Reader {
    private int position = 0;  // this is for Unsigned5::Reader behavior
    public int position() { return position; }
    public void setPosition(int pos) { position = pos; }
    // UNSIGNED5::Reader::next_uint
    public long nextUint() {
        if (!hasNext())  return -1;
        return readUint(this, position, Reader::getByte, Reader::setPosition);
    }
    // UNSIGNED5::Reader::has_next
    public boolean hasNext() { return Unsigned5.this.hasNext(position); }
    // delegate reads to outer object:
    private short getByte(int pos) { return Unsigned5.this.getByte(pos); }
  }

  // UNSIGNED5::read_uint (no position update)
  public long readUint(int pos) {
    if (!hasNext(pos))  return -1;
    return readUint(this, pos, Unsigned5::getByte, (a,i)->{});
  }
  private boolean hasNext(int pos) {
    // 1. there must be a non-excluded byte at the read position
    // 2. the position must be less than any non-zero limit
    return ((X == 0 || getByte(pos) >= X) &&
            (limit == 0 || pos < limit));
  }

  // debug.cpp: u5decode(intptr_t addr)
  public void print() {
    printOn(System.out);
  }
  public void printOn(PrintStream tty) {
    tty.print("U5 " + readUint(0) + ", ");
  }

  // debug.cpp: u5p(intptr_t addr, intptr_t limit, int count)
  // check and decode a series of u5 values
  // return the address after the last decoded byte
  // if limit is non-zero stop before limit
  // if count is non-negative stop when count is reached
  // if count is negative stop on null (works kind of like strlen)
  public void dumpOn(PrintStream tty, int count) {
    Reader r = new Reader();
    int printed = 0;
    tty.print("U5: [");
    for (;;) {
      if (count >= 0 && printed >= count)  break;
      if (!r.hasNext()) {
        if ((r.position < limit || limit == 0) && getByte(r.position) == 0) {
          tty.print(" null");
          ++r.position;  // skip null byte
          ++printed;
          if (limit != 0)  continue;  // keep going to explicit limit
        }
        break;
      }
      int value = (int) r.nextUint();
      tty.print(" ");
      tty.print(value);
      ++printed;
    }
    tty.println(" ] (values=" + printed + "/length=" + r.position + ")");
  }
  public void dump(int count) {
    dumpOn(System.out, count);
  }
  public void dump() {
    // dump as many as possible, up to any nonzero limit
    dumpOn(System.out, -1);
  }
}
