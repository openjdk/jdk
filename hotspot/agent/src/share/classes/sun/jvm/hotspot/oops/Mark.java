/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

/** Mark is the analogue of the VM's markOop. In this system it does
    not subclass Oop but VMObject. For a mark on the stack, the mark's
    address will be an Address; for a mark in the header of an object,
    it will be an OopHandle. It is assumed in a couple of places in
    this code that the mark is the first word in an object. */

public class Mark extends VMObject {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type  = db.lookupType("oopDesc");
    markField  = type.getCIntegerField("_mark");

    ageBits             = db.lookupLongConstant("markOopDesc::age_bits").longValue();
    lockBits            = db.lookupLongConstant("markOopDesc::lock_bits").longValue();
    biasedLockBits      = db.lookupLongConstant("markOopDesc::biased_lock_bits").longValue();
    maxHashBits         = db.lookupLongConstant("markOopDesc::max_hash_bits").longValue();
    hashBits            = db.lookupLongConstant("markOopDesc::hash_bits").longValue();
    lockShift           = db.lookupLongConstant("markOopDesc::lock_shift").longValue();
    biasedLockShift     = db.lookupLongConstant("markOopDesc::biased_lock_shift").longValue();
    ageShift            = db.lookupLongConstant("markOopDesc::age_shift").longValue();
    hashShift           = db.lookupLongConstant("markOopDesc::hash_shift").longValue();
    lockMask            = db.lookupLongConstant("markOopDesc::lock_mask").longValue();
    lockMaskInPlace     = db.lookupLongConstant("markOopDesc::lock_mask_in_place").longValue();
    biasedLockMask      = db.lookupLongConstant("markOopDesc::biased_lock_mask").longValue();
    biasedLockMaskInPlace  = db.lookupLongConstant("markOopDesc::biased_lock_mask_in_place").longValue();
    biasedLockBitInPlace  = db.lookupLongConstant("markOopDesc::biased_lock_bit_in_place").longValue();
    ageMask             = db.lookupLongConstant("markOopDesc::age_mask").longValue();
    ageMaskInPlace      = db.lookupLongConstant("markOopDesc::age_mask_in_place").longValue();
    hashMask            = db.lookupLongConstant("markOopDesc::hash_mask").longValue();
    hashMaskInPlace     = db.lookupLongConstant("markOopDesc::hash_mask_in_place").longValue();
    biasedLockAlignment  = db.lookupLongConstant("markOopDesc::biased_lock_alignment").longValue();
    lockedValue         = db.lookupLongConstant("markOopDesc::locked_value").longValue();
    unlockedValue       = db.lookupLongConstant("markOopDesc::unlocked_value").longValue();
    monitorValue        = db.lookupLongConstant("markOopDesc::monitor_value").longValue();
    markedValue         = db.lookupLongConstant("markOopDesc::marked_value").longValue();
    biasedLockPattern = db.lookupLongConstant("markOopDesc::biased_lock_pattern").longValue();
    noHash              = db.lookupLongConstant("markOopDesc::no_hash").longValue();
    noHashInPlace       = db.lookupLongConstant("markOopDesc::no_hash_in_place").longValue();
    noLockInPlace       = db.lookupLongConstant("markOopDesc::no_lock_in_place").longValue();
    maxAge              = db.lookupLongConstant("markOopDesc::max_age").longValue();

    /* Constants in markOop used by CMS. */
    cmsShift            = db.lookupLongConstant("markOopDesc::cms_shift").longValue();
    cmsMask             = db.lookupLongConstant("markOopDesc::cms_mask").longValue();
    sizeShift           = db.lookupLongConstant("markOopDesc::size_shift").longValue();
  }

  // Field accessors
  private static CIntegerField markField;

  // Constants -- read from VM
  private static long ageBits;
  private static long lockBits;
  private static long biasedLockBits;
  private static long maxHashBits;
  private static long hashBits;

  private static long lockShift;
  private static long biasedLockShift;
  private static long ageShift;
  private static long hashShift;

  private static long lockMask;
  private static long lockMaskInPlace;
  private static long biasedLockMask;
  private static long biasedLockMaskInPlace;
  private static long biasedLockBitInPlace;
  private static long ageMask;
  private static long ageMaskInPlace;
  private static long hashMask;
  private static long hashMaskInPlace;
  private static long biasedLockAlignment;

  private static long lockedValue;
  private static long unlockedValue;
  private static long monitorValue;
  private static long markedValue;
  private static long biasedLockPattern;

  private static long noHash;

  private static long noHashInPlace;
  private static long noLockInPlace;

  private static long maxAge;

  /* Constants in markOop used by CMS. */
  private static long cmsShift;
  private static long cmsMask;
  private static long sizeShift;

  public Mark(Address addr) {
    super(addr);
  }

  public long value() {
    return markField.getValue(addr);
  }

  public Address valueAsAddress() {
    return addr.getAddressAt(markField.getOffset());
  }

  // Biased locking accessors
  // These must be checked by all code which calls into the
  // ObjectSynchoronizer and other code. The biasing is not understood
  // by the lower-level CAS-based locking code, although the runtime
  // fixes up biased locks to be compatible with it when a bias is
  // revoked.
  public boolean hasBiasPattern() {
    return (Bits.maskBitsLong(value(), biasedLockMaskInPlace) == biasedLockPattern);
  }

  public JavaThread biasedLocker() {
    Threads threads = VM.getVM().getThreads();
    Address addr = valueAsAddress().andWithMask(~(biasedLockMaskInPlace & ageMaskInPlace));
    return threads.createJavaThreadWrapper(addr);
  }

  // Indicates that the mark gas the bias bit set but that it has not
  // yet been biased toward a particular thread
  public boolean isBiasedAnonymously() {
    return hasBiasPattern() && (biasedLocker() == null);
  }

  // lock accessors (note that these assume lock_shift == 0)
  public boolean isLocked() {
    return (Bits.maskBitsLong(value(), lockMaskInPlace) != unlockedValue);
  }
  public boolean isUnlocked() {
    return (Bits.maskBitsLong(value(), biasedLockMaskInPlace) == unlockedValue);
  }
  public boolean isMarked() {
    return (Bits.maskBitsLong(value(), lockMaskInPlace) == markedValue);
  }

  // Special temporary state of the markOop while being inflated.
  // Code that looks at mark outside a lock need to take this into account.
  public boolean isBeingInflated() {
    return (value() == 0);
  }

  // Should this header be preserved during GC?
  public boolean mustBePreserved() {
     return (!isUnlocked() || !hasNoHash());
  }

  // WARNING: The following routines are used EXCLUSIVELY by
  // synchronization functions. They are not really gc safe.
  // They must get updated if markOop layout get changed.

  // FIXME
  //  markOop set_unlocked() const {
  //    return markOop(value() | unlocked_value);
  //  }
  public boolean hasLocker() {
    return ((value() & lockMaskInPlace) == lockedValue);
  }
  public BasicLock locker() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasLocker(), "check");
    }
    return new BasicLock(valueAsAddress());
  }
  public boolean hasMonitor() {
    return ((value() & monitorValue) != 0);
  }
  public ObjectMonitor monitor() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasMonitor(), "check");
    }
    // Use xor instead of &~ to provide one extra tag-bit check.
    Address monAddr = valueAsAddress().xorWithMask(monitorValue);
    return new ObjectMonitor(monAddr);
  }
  public boolean hasDisplacedMarkHelper() {
    return ((value() & unlockedValue) == 0);
  }
  public Mark displacedMarkHelper() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasDisplacedMarkHelper(), "check");
    }
    Address addr = valueAsAddress().andWithMask(~monitorValue);
    return new Mark(addr.getAddressAt(0));
  }
  // FIXME
  //  void set_displaced_mark_helper(markOop m) const {
  //    assert(has_displaced_mark_helper(), "check");
  //    intptr_t ptr = (value() & ~monitor_value);
  //    *(markOop*)ptr = m;
  //  }
  //  markOop copy_set_hash(intptr_t hash) const {
  //    intptr_t tmp = value() & (~hash_mask_in_place);
  //    tmp |= ((hash & hash_mask) << hash_shift);
  //    return (markOop)tmp;
  //  }
  // it is only used to be stored into BasicLock as the
  // indicator that the lock is using heavyweight monitor
  //  static markOop unused_mark() {
  //    return (markOop) marked_value;
  //  }
  //  // the following two functions create the markOop to be
  //  // stored into object header, it encodes monitor info
  //  static markOop encode(BasicLock* lock) {
  //    return (markOop) lock;
  //  }
  //  static markOop encode(ObjectMonitor* monitor) {
  //    intptr_t tmp = (intptr_t) monitor;
  //    return (markOop) (tmp | monitor_value);
  //  }
  // used for alignment-based marking to reuse the busy state to encode pointers
  // (see markOop_alignment.hpp)
  //  markOop clear_lock_bits() { return markOop(value() & ~lock_mask_in_place); }
  //
  //  // age operations
  //  markOop set_marked()   { return markOop((value() & ~lock_mask_in_place) | marked_value); }
  //
  public int age() { return (int) Bits.maskBitsLong(value() >> ageShift, ageMask); }
  //  markOop set_age(int v) const {
  //    assert((v & ~age_mask) == 0, "shouldn't overflow age field");
  //    return markOop((value() & ~age_mask_in_place) | (((intptr_t)v & age_mask) << age_shift));
  //  }
  //  markOop incr_age()          const { return age() == max_age ? markOop(this) : set_age(age() + 1); }

  // hash operations
  public long hash() {
    return Bits.maskBitsLong(value() >> hashShift, hashMask);
  }

  public boolean hasNoHash() {
    return hash() == noHash;
  }

  // FIXME
  // Prototype mark for initialization
  //  static markOop prototype() {
  //    return markOop( no_hash_in_place | no_lock_in_place );
  //  }

  // Debugging
  public void printOn(PrintStream tty) {
    if (isLocked()) {
      tty.print("locked(0x" +
                Long.toHexString(value()) + ")->");
      displacedMarkHelper().printOn(tty);
    } else {
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(isUnlocked(), "just checking");
      }
      tty.print("mark(");
      tty.print("hash " + Long.toHexString(hash()) + ",");
      tty.print("age " + age() + ")");
    }
  }

  // FIXME
  //  // Prepare address of oop for placement into mark
  //  inline static markOop encode_pointer_as_mark(void* p) { return markOop(p)->set_marked(); }
  //
  //  // Recover address of oop from encoded form used in mark
  //  inline void* decode_pointer() { return clear_lock_bits(); }

  // Copy markOop methods for CMS here.
  public boolean isCmsFreeChunk() {
    return isUnlocked() &&
           (Bits.maskBitsLong(value() >> cmsShift, cmsMask) & 0x1L) == 0x1L;
  }
  public long getSize() { return (long)(value() >> sizeShift); }
}
