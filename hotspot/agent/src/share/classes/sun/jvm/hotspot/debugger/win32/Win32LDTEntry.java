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

package sun.jvm.hotspot.debugger.win32;

import java.io.Serializable;

/** Describes an LDT entry. (Some of the descriptions are taken
    directly from Microsoft's documentation and are copyrighted by
    Microsoft.) */

class Win32LDTEntry implements Serializable {
  private short limitLow;
  private short baseLow;
  private byte  baseMid;
  private byte  flags1;
  private byte  flags2;
  private byte  baseHi;

  private Win32LDTEntry() {}

  public Win32LDTEntry(short limitLow,
                       short baseLow,
                       byte  baseMid,
                       byte  flags1,
                       byte  flags2,
                       byte  baseHi) {
    this.limitLow = limitLow;
    this.baseLow  = baseLow;
    this.baseMid  = baseMid;
    this.flags1   = flags1;
    this.flags2   = flags2;
    this.baseHi   = baseHi;
  }

  /** Returns base address of segment */
  public long  getBase()     { return ( (baseLow & 0xFFFF)       |
                                       ((baseMid & 0xFF) << 16)  |
                                       ((baseHi  & 0xFF) << 24)) & 0xFFFFFFFF; }

  public short getLimitLow() { return limitLow; }
  public short getBaseLow()  { return baseLow; }
  public byte  getBaseMid()  { return baseMid; }
  public byte  getBaseHi()   { return baseHi; }

  // FIXME: must verify mask and shift are correct
  /** Describes type of segment. See TYPE_ portion of {@link
      sun.jvm.hotspot.debugger.win32.Win32LDTEntryConstants}. */
  public int   getType()     { return (flags1 & 0x1F); }

  // FIXME: verify mask and shift are correct
  /** Privilege level of descriptor: 0 = most privileged, 3 = least privileged */
  public int   getPrivilegeLevel() { return ((flags1 & 0x60) >> 5); }

  // FIXME: verify mask is correct
  /** Is segment present in physical memory? */
  public boolean isSegmentPhysical() { return ((flags1 & 0x70) != 0); }

  // FIXME: verify mask and shift are correct
  /** High bits (16-19) of the address of the last byte of the segment */
  public int getLimitHi() { return (flags2 & 0x0F); }

  // FIXME: verify mask is correct
  /** <P> Size of segment. If the segment is a data segment, this
      member contains 1 if the segment is larger than 64 kilobytes (K)
      or 0 if the segment is smaller than or equal to 64K. </P>

      <P> If the segment is a code segment, this member contains 1 if
      the segment is a code segment and runs with the default (native
      mode) instruction set. This member contains 0 if the code
      segment is an 80286 code segment and runs with 16-bit offsets
      and the 80286-compatible instruction set. </P> */
  public boolean isDefaultBig() { return ((flags2 & 0x40) != 0); }

  // FIXME: verify mask is correct
  /** Returns true if segment is page granular, false if byte
      granular. */
  public boolean isPageGranular() { return ((flags2 & 0x80) != 0); }
}
