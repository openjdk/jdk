/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.java.util.jar.pack;

import com.sun.java.util.jar.pack.ConstantPool.Entry;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Collection of relocatable constant pool references.
 * It operates with respect to a particular byte array,
 * and stores some of its state in the bytes themselves.
 * <p>
 * As a Collection, it can be iterated over, but it is not a List,
 * since it does not natively support indexed access.
 * <p>
 *
 * @author John Rose
 */
final class Fixups extends AbstractCollection {
    byte[] bytes;    // the subject of the relocations
    int head;        // desc locating first reloc
    int tail;        // desc locating last reloc
    int size;        // number of relocations
    Entry[] entries; // [0..size-1] relocations
    int[] bigDescs;  // descs which cannot be stored in the bytes

    // A "desc" (descriptor) is a bit-encoded pair of a location
    // and format.  Every fixup occurs at a "desc".  Until final
    // patching, bytes addressed by descs may also be used to
    // link this data structure together.  If the bytes are missing,
    // or if the "desc" is too large to encode in the bytes,
    // it is kept in the bigDescs array.

    Fixups(byte[] bytes) {
        this.bytes = bytes;
        entries = new Entry[3];
        bigDescs = noBigDescs;
    }
    Fixups() {
        // If there are no bytes, all descs are kept in bigDescs.
        this((byte[])null);
    }
    Fixups(byte[] bytes, Collection fixups) {
        this(bytes);
        addAll(fixups);
    }
    Fixups(Collection fixups) {
        this((byte[])null);
        addAll(fixups);
    }

    private static final int MINBIGSIZE = 1;
    // cleverly share empty bigDescs:
    private static int[] noBigDescs = {MINBIGSIZE};

    public int size() {
        return size;
    }

    public void trimToSize() {
        if (size != entries.length) {
            Entry[] oldEntries = entries;
            entries = new Entry[size];
            System.arraycopy(oldEntries, 0, entries, 0, size);
        }
        int bigSize = bigDescs[BIGSIZE];
        if (bigSize == MINBIGSIZE) {
            bigDescs = noBigDescs;
        } else if (bigSize != bigDescs.length) {
            int[] oldBigDescs = bigDescs;
            bigDescs = new int[bigSize];
            System.arraycopy(oldBigDescs, 0, bigDescs, 0, bigSize);
        }
    }

    public void visitRefs(Collection<Entry> refs) {
        for (int i = 0; i < size; i++) {
            refs.add(entries[i]);
        }
    }

    public void clear() {
        if (bytes != null) {
            // Clean the bytes:
            for (Iterator i = iterator(); i.hasNext(); ) {
                Fixup fx = (Fixup) i.next();
                //System.out.println("clean "+fx);
                storeIndex(fx.location(), fx.format(), 0);
            }
        }
        size = 0;
        if (bigDescs != noBigDescs)
            bigDescs[BIGSIZE] = MINBIGSIZE;
        // do not trim to size, however
    }

    public byte[] getBytes() {
        return bytes;
    }

    @SuppressWarnings("unchecked")
    public void setBytes(byte[] newBytes) {
        if (bytes == newBytes)  return;
        ArrayList old = null;
        assert((old = new ArrayList(this)) != null);
        if (bytes == null || newBytes == null) {
            // One or the other representations is deficient.
            // Construct a checkpoint.
            ArrayList save = new ArrayList(this);
            clear();
            bytes = newBytes;
            addAll(save);
        } else {
            // assume newBytes is some sort of bitwise copy of the old bytes
            bytes = newBytes;
        }
        assert(old.equals(new ArrayList(this)));
    }

    static final int LOC_SHIFT = 1;
    static final int FMT_MASK = 0x1;
    static final byte UNUSED_BYTE = 0;
    static final byte OVERFLOW_BYTE = -1;
    // fill pointer of bigDescs array is in element [0]
    static final int BIGSIZE = 0;

    // Format values:
    public static final int U2_FORMAT = 0;
    public static final int U1_FORMAT = 1;

    // Special values for the static methods.
    private static final int SPECIAL_LOC = 0;
    private static final int SPECIAL_FMT = U2_FORMAT;

    static int fmtLen(int fmt) { return 1+(fmt-U1_FORMAT)/(U2_FORMAT-U1_FORMAT); }
    static int descLoc(int desc) { return desc >>> LOC_SHIFT; }
    static int descFmt(int desc) { return desc  &  FMT_MASK; }
    static int descEnd(int desc) { return descLoc(desc) + fmtLen(descFmt(desc)); }
    static int makeDesc(int loc, int fmt) {
        int desc = (loc << LOC_SHIFT) | fmt;
        assert(descLoc(desc) == loc);
        assert(descFmt(desc) == fmt);
        return desc;
    }
    int fetchDesc(int loc, int fmt) {
        byte b1 = bytes[loc];
        assert(b1 != OVERFLOW_BYTE);
        int value;
        if (fmt == U2_FORMAT) {
            byte b2 = bytes[loc+1];
            value = ((b1 & 0xFF) << 8) + (b2 & 0xFF);
        } else {
            value = (b1 & 0xFF);
        }
        // Stored loc field is difference between its own loc and next loc.
        return value + (loc << LOC_SHIFT);
    }
    boolean storeDesc(int loc, int fmt, int desc) {
        if (bytes == null)
            return false;
        int value = desc - (loc << LOC_SHIFT);
        byte b1, b2;
        switch (fmt) {
        case U2_FORMAT:
            assert(bytes[loc+0] == UNUSED_BYTE);
            assert(bytes[loc+1] == UNUSED_BYTE);
            b1 = (byte)(value >> 8);
            b2 = (byte)(value >> 0);
            if (value == (value & 0xFFFF) && b1 != OVERFLOW_BYTE) {
                bytes[loc+0] = b1;
                bytes[loc+1] = b2;
                assert(fetchDesc(loc, fmt) == desc);
                return true;
            }
            break;
        case U1_FORMAT:
            assert(bytes[loc] == UNUSED_BYTE);
            b1 = (byte)value;
            if (value == (value & 0xFF) && b1 != OVERFLOW_BYTE) {
                bytes[loc] = b1;
                assert(fetchDesc(loc, fmt) == desc);
                return true;
            }
            break;
        default: assert(false);
        }
        // Failure.  Caller must allocate a bigDesc.
        bytes[loc] = OVERFLOW_BYTE;
        assert(fmt==U1_FORMAT || (bytes[loc+1]=(byte)bigDescs[BIGSIZE])!=999);
        return false;
    }
    void storeIndex(int loc, int fmt, int value) {
        storeIndex(bytes, loc, fmt, value);
    }
    static
    void storeIndex(byte[] bytes, int loc, int fmt, int value) {
        switch (fmt) {
        case U2_FORMAT:
            assert(value == (value & 0xFFFF)) : (value);
            bytes[loc+0] = (byte)(value >> 8);
            bytes[loc+1] = (byte)(value >> 0);
            break;
        case U1_FORMAT:
            assert(value == (value & 0xFF)) : (value);
            bytes[loc] = (byte)value;
            break;
        default: assert(false);
        }
    }

    /** Simple and necessary tuple to present each fixup. */
    public static
    class Fixup implements Comparable {
        int desc;         // location and format of reloc
        Entry entry;      // which entry to plug into the bytes
        Fixup(int desc, Entry entry) {
            this.desc = desc;
            this.entry = entry;
        }
        public Fixup(int loc, int fmt, Entry entry) {
            this.desc = makeDesc(loc, fmt);
            this.entry = entry;
        }
        public int location() { return descLoc(desc); }
        public int format() { return descFmt(desc); }
        public Entry entry() { return entry; }
        public int compareTo(Fixup that) {
            // Ordering depends only on location.
            return this.location() - that.location();
        }
        public int compareTo(Object that) {
            return compareTo((Fixup)that);
        }
        public boolean equals(Object x) {
            if (!(x instanceof Fixup))  return false;
            Fixup that = (Fixup) x;
            return this.desc == that.desc && this.entry == that.entry;
        }
        public String toString() {
            return "@"+location()+(format()==U1_FORMAT?".1":"")+"="+entry;
        }
    }

    private
    class Itr implements Iterator {
        int index = 0;               // index into entries
        int bigIndex = BIGSIZE+1;    // index into bigDescs
        int next = head;             // desc pointing to next fixup
        public boolean hasNext() { return index < size; }
        public void remove() { throw new UnsupportedOperationException(); }
        public Object next() {
            int thisIndex = index;
            return new Fixup(nextDesc(), entries[thisIndex]);
        }
        int nextDesc() {
            index++;
            int thisDesc = next;
            if (index < size) {
                // Fetch next desc eagerly, in case this fixup gets finalized.
                int loc = descLoc(thisDesc);
                int fmt = descFmt(thisDesc);
                if (bytes != null && bytes[loc] != OVERFLOW_BYTE) {
                    next = fetchDesc(loc, fmt);
                } else {
                    // The unused extra byte is "asserted" to be equal to BI.
                    // This helps keep the overflow descs in sync.
                    assert(fmt==U1_FORMAT || bytes == null || bytes[loc+1]==(byte)bigIndex);
                    next = bigDescs[bigIndex++];
                }
            }
            return thisDesc;
        }
    }

    public Iterator iterator() {
        return new Itr();
    }
    public void add(int location, int format, Entry entry) {
        addDesc(makeDesc(location, format), entry);
    }
    public boolean add(Fixup f) {
        addDesc(f.desc, f.entry);
        return true;
    }
    public boolean add(Object fixup) {
        return add((Fixup) fixup);
    }
    @SuppressWarnings("unchecked")
    public boolean addAll(Collection c) {
        if (c instanceof Fixups) {
            // Use knowledge of Itr structure to avoid building little structs.
            Fixups that = (Fixups) c;
            if (that.size == 0)  return false;
            if (this.size == 0 && entries.length < that.size)
                growEntries(that.size);  // presize exactly
            Entry[] thatEntries = that.entries;
            for (Itr i = that.new Itr(); i.hasNext(); ) {
                int ni = i.index;
                addDesc(i.nextDesc(), thatEntries[ni]);
            }
            return true;
        } else {
            return super.addAll(c);
        }
    }
    // Here is how things get added:
    private void addDesc(int thisDesc, Entry entry) {
        if (entries.length == size)
            growEntries(size * 2);
        entries[size] = entry;
        if (size == 0) {
            head = tail = thisDesc;
        } else {
            int prevDesc = tail;
            // Store new desc in previous tail.
            int prevLoc = descLoc(prevDesc);
            int prevFmt = descFmt(prevDesc);
            int prevLen = fmtLen(prevFmt);
            int thisLoc = descLoc(thisDesc);
            // The collection must go in ascending order, and not overlap.
            if (thisLoc < prevLoc + prevLen)
                badOverlap(thisLoc);
            tail = thisDesc;
            if (!storeDesc(prevLoc, prevFmt, thisDesc)) {
                // overflow
                int bigSize = bigDescs[BIGSIZE];
                if (bigDescs.length == bigSize)
                    growBigDescs();
                //System.out.println("bigDescs["+bigSize+"] = "+thisDesc);
                bigDescs[bigSize++] = thisDesc;
                bigDescs[BIGSIZE] = bigSize;
            }
        }
        size += 1;
    }
    private void badOverlap(int thisLoc) {
        throw new IllegalArgumentException("locs must be ascending and must not overlap:  "+thisLoc+" >> "+this);
    }

    private void growEntries(int newSize) {
        Entry[] oldEntries = entries;
        entries = new Entry[Math.max(3, newSize)];
        System.arraycopy(oldEntries, 0, entries, 0, oldEntries.length);
    }
    private void growBigDescs() {
        int[] oldBigDescs = bigDescs;
        bigDescs = new int[oldBigDescs.length * 2];
        System.arraycopy(oldBigDescs, 0, bigDescs, 0, oldBigDescs.length);
    }

    /// Static methods that optimize the use of this class.
    public static
    Object add(Object prevFixups,
               byte[] bytes, int loc, int fmt,
               Entry e) {
        Fixups f;
        if (prevFixups == null) {
            if (loc == SPECIAL_LOC && fmt == SPECIAL_FMT) {
                // Special convention:  If the attribute has a
                // U2 relocation at position zero, store the Entry
                // rather than building a Fixups structure.
                return e;
            }
            f = new Fixups(bytes);
        } else if (!(prevFixups instanceof Fixups)) {
            // Recognize the special convention:
            Entry firstEntry = (Entry) prevFixups;
            f = new Fixups(bytes);
            f.add(SPECIAL_LOC, SPECIAL_FMT, firstEntry);
        } else {
            f = (Fixups) prevFixups;
            assert(f.bytes == bytes);
        }
        f.add(loc, fmt, e);
        return f;
    }

    public static
    void setBytes(Object fixups, byte[] bytes) {
        if (fixups instanceof Fixups) {
            Fixups f = (Fixups) fixups;
            f.setBytes(bytes);
        }
    }

    public static
    Object trimToSize(Object fixups) {
        if (fixups instanceof Fixups) {
            Fixups f = (Fixups) fixups;
            f.trimToSize();
            if (f.size() == 0)
                fixups = null;
        }
        return fixups;
    }

    // Iterate over all the references in this set of fixups.
    public static
    void visitRefs(Object fixups, Collection<Entry> refs) {
        if (fixups == null) {
        } else if (!(fixups instanceof Fixups)) {
            // Special convention; see above.
            refs.add((Entry) fixups);
        } else {
            Fixups f = (Fixups) fixups;
            f.visitRefs(refs);
        }
    }

    // Clear out this set of fixups by replacing each reference
    // by a hardcoded coding of its reference, drawn from ix.
    public static
    void finishRefs(Object fixups, byte[] bytes, ConstantPool.Index ix) {
        if (fixups == null)
            return;
        if (!(fixups instanceof Fixups)) {
            // Special convention; see above.
            int index = ix.indexOf((Entry) fixups);
            storeIndex(bytes, SPECIAL_LOC, SPECIAL_FMT, index);
            return;
        }
        Fixups f = (Fixups) fixups;
        assert(f.bytes == bytes);
        f.finishRefs(ix);
    }

    void finishRefs(ConstantPool.Index ix) {
        if (isEmpty())
            return;
        for (Iterator i = iterator(); i.hasNext(); ) {
            Fixup fx = (Fixup) i.next();
            int index = ix.indexOf(fx.entry);
            //System.out.println("finish "+fx+" = "+index);
            // Note that the iterator has already fetched the
            // bytes we are about to overwrite.
            storeIndex(fx.location(), fx.format(), index);
        }
        // Further iterations should do nothing:
        bytes = null;  // do not clean them
        clear();
    }

/*
    /// Testing.
    public static void main(String[] av) {
        byte[] bytes = new byte[1 << 20];
        ConstantPool cp = new ConstantPool();
        Fixups f = new Fixups(bytes);
        boolean isU1 = false;
        int span = 3;
        int nextLoc = 0;
        int[] locs = new int[100];
        final int[] indexes = new int[100];
        int iptr = 1;
        for (int loc = 0; loc < bytes.length; loc++) {
            if (loc == nextLoc && loc+1 < bytes.length) {
                int fmt = (isU1 ? U1_FORMAT : U2_FORMAT);
                Entry e = ConstantPool.getUtf8Entry("L"+loc);
                f.add(loc, fmt, e);
                isU1 ^= true;
                if (iptr < 10) {
                    // Make it close in.
                    nextLoc += fmtLen(fmt) + (iptr < 5 ? 0 : 1);
                } else {
                    nextLoc += span;
                    span = (int)(span * 1.77);
                }
                // Here are the bytes that would have gone here:
                locs[iptr] = loc;
                if (fmt == U1_FORMAT) {
                    indexes[iptr++] = (loc & 0xFF);
                } else {
                    indexes[iptr++] = ((loc & 0xFF) << 8) | ((loc+1) & 0xFF);
                    ++loc;  // skip a byte
                }
                continue;
            }
            bytes[loc] = (byte)loc;
        }
        System.out.println("size="+f.size()
                           +" overflow="+(f.bigDescs[BIGSIZE]-1));
        System.out.println("Fixups: "+f);
        // Test collection contents.
        assert(iptr == 1+f.size());
        List l = new ArrayList(f);
        Collections.sort(l);  // should not change the order
        if (!l.equals(new ArrayList(f)))  System.out.println("** disordered");
        f.setBytes(null);
        if (!l.equals(new ArrayList(f)))  System.out.println("** bad set 1");
        f.setBytes(bytes);
        if (!l.equals(new ArrayList(f)))  System.out.println("** bad set 2");
        Fixups f3 = new Fixups(f);
        if (!l.equals(new ArrayList(f3))) System.out.println("** bad set 3");
        Iterator fi = f.iterator();
        for (int i = 1; i < iptr; i++) {
            Fixup fx = (Fixup) fi.next();
            if (fx.location() != locs[i]) {
                System.out.println("** "+fx+" != "+locs[i]);
            }
            if (fx.format() == U1_FORMAT)
                System.out.println(fx+" -> "+bytes[locs[i]]);
            else
                System.out.println(fx+" -> "+bytes[locs[i]]+" "+bytes[locs[i]+1]);
        }
        assert(!fi.hasNext());
        indexes[0] = 1;  // like iptr
        Index ix = new Index("ix") {
            public int indexOf(Entry e) {
                return indexes[indexes[0]++];
            }
        };
        f.finishRefs(ix);
        for (int loc = 0; loc < bytes.length; loc++) {
            if (bytes[loc] != (byte)loc) {
                System.out.println("** ["+loc+"] = "+bytes[loc]+" != "+(byte)loc);
            }
        }
    }
//*/
}
