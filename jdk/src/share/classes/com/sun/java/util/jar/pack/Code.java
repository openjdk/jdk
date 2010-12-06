/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.java.util.jar.pack.Package.Class;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;

/**
 * Represents a chunk of bytecodes.
 * @author John Rose
 */
class Code extends Attribute.Holder implements Constants {
    Class.Method m;

    public Code(Class.Method m) {
        this.m = m;
    }

    public Class.Method getMethod() {
        return m;
    }
    public Class thisClass() {
        return m.thisClass();
    }
    public Package getPackage() {
        return m.thisClass().getPackage();
    }

    public ConstantPool.Entry[] getCPMap() {
        return m.getCPMap();
    }

    static private final ConstantPool.Entry[] noRefs = ConstantPool.noRefs;

    // The following fields are used directly by the ClassReader, etc.
    int max_stack;
    int max_locals;

    ConstantPool.Entry handler_class[] = noRefs;
    int handler_start[] = noInts;
    int handler_end[] = noInts;
    int handler_catch[] = noInts;

    byte[] bytes;
    Fixups fixups;  // reference relocations, if any are required
    Object insnMap; // array of instruction boundaries

    int getLength() { return bytes.length; }

    int getMaxStack() {
        return max_stack;
    }
    void setMaxStack(int ms) {
        max_stack = ms;
    }

    int getMaxNALocals() {
        int argsize = m.getArgumentSize();
        return max_locals - argsize;
    }
    void setMaxNALocals(int ml) {
        int argsize = m.getArgumentSize();
        max_locals = argsize + ml;
    }

    int getHandlerCount() {
        assert(handler_class.length == handler_start.length);
        assert(handler_class.length == handler_end.length);
        assert(handler_class.length == handler_catch.length);
        return handler_class.length;
    }
    void setHandlerCount(int h) {
        if (h > 0) {
            handler_class = new ConstantPool.Entry[h];
            handler_start = new int[h];
            handler_end   = new int[h];
            handler_catch = new int[h];
            // caller must fill these in ASAP
        }
    }

    void setBytes(byte[] bytes) {
        this.bytes = bytes;
        if (fixups != null)
            fixups.setBytes(bytes);
    }

    void setInstructionMap(int[] insnMap, int mapLen) {
        //int[] oldMap = null;
        //assert((oldMap = getInstructionMap()) != null);
        this.insnMap = allocateInstructionMap(insnMap, mapLen);
        //assert(Arrays.equals(oldMap, getInstructionMap()));
    }
    void setInstructionMap(int[] insnMap) {
        setInstructionMap(insnMap, insnMap.length);
    }

    int[] getInstructionMap() {
        return expandInstructionMap(getInsnMap());
    }

    void addFixups(Collection moreFixups) {
        if (fixups == null) {
            fixups = new Fixups(bytes);
        }
        assert(fixups.getBytes() == bytes);
        fixups.addAll(moreFixups);
    }

    public void trimToSize() {
        if (fixups != null) {
            fixups.trimToSize();
            if (fixups.size() == 0)
                fixups = null;
        }
        super.trimToSize();
    }

    protected void visitRefs(int mode, Collection refs) {
        int verbose = getPackage().verbose;
        if (verbose > 2)
            System.out.println("Reference scan "+this);
        Class cls = thisClass();
        Package pkg = cls.getPackage();
        for (int i = 0; i < handler_class.length; i++) {
            refs.add(handler_class[i]);
        }
        if (fixups != null) {
            fixups.visitRefs(refs);
        } else {
            // References (to a local cpMap) are embedded in the bytes.
            ConstantPool.Entry[] cpMap = getCPMap();
            for (Instruction i = instructionAt(0); i != null; i = i.next()) {
                if (verbose > 4)
                    System.out.println(i);
                int cpref = i.getCPIndex();
                if (cpref >= 0) {
                    refs.add(cpMap[cpref]);
                }
            }
        }
        // Handle attribute list:
        super.visitRefs(mode, refs);
    }

    // Since bytecodes are the single largest contributor to
    // package size, it's worth a little bit of trouble
    // to reduce the per-bytecode memory footprint.
    // In the current scheme, half of the bulk of these arrays
    // due to bytes, and half to shorts.  (Ints are insignificant.)
    // Given an average of 1.8 bytes per instruction, this means
    // instruction boundary arrays are about a 75% overhead--tolerable.
    // (By using bytes, we get 33% savings over just shorts and ints.
    // Using both bytes and shorts gives 66% savings over just ints.)
    static final boolean shrinkMaps = true;

    private Object allocateInstructionMap(int[] insnMap, int mapLen) {
        int PClimit = getLength();
        if (shrinkMaps && PClimit <= Byte.MAX_VALUE - Byte.MIN_VALUE) {
            byte[] map = new byte[mapLen+1];
            for (int i = 0; i < mapLen; i++) {
                map[i] = (byte)(insnMap[i] + Byte.MIN_VALUE);
            }
            map[mapLen] = (byte)(PClimit + Byte.MIN_VALUE);
            return map;
        } else if (shrinkMaps && PClimit < Short.MAX_VALUE - Short.MIN_VALUE) {
            short[] map = new short[mapLen+1];
            for (int i = 0; i < mapLen; i++) {
                map[i] = (short)(insnMap[i] + Short.MIN_VALUE);
            }
            map[mapLen] = (short)(PClimit + Short.MIN_VALUE);
            return map;
        } else {
            int[] map = new int[mapLen+1];
            for (int i = 0; i < mapLen; i++) {
                map[i] = (int) insnMap[i];
            }
            map[mapLen] = (int) PClimit;
            return map;
        }
    }
    private int[] expandInstructionMap(Object map0) {
        int[] imap;
        if (map0 instanceof byte[]) {
            byte[] map = (byte[]) map0;
            imap = new int[map.length-1];
            for (int i = 0; i < imap.length; i++) {
                imap[i] = map[i] - Byte.MIN_VALUE;
            }
        } else if (map0 instanceof short[]) {
            short[] map = (short[]) map0;
            imap = new int[map.length-1];
            for (int i = 0; i < imap.length; i++) {
                imap[i] = map[i] - Byte.MIN_VALUE;
            }
        } else {
            int[] map = (int[]) map0;
            imap = new int[map.length-1];
            for (int i = 0; i < imap.length; i++) {
                imap[i] = map[i];
            }
        }
        return imap;
    }

    Object getInsnMap() {
        // Build a map of instruction boundaries.
        if (insnMap != null) {
            return insnMap;
        }
        int[] map = new int[getLength()];
        int fillp = 0;
        for (Instruction i = instructionAt(0); i != null; i = i.next()) {
            map[fillp++] = i.getPC();
        }
        // Make it byte[], short[], or int[] according to the max BCI.
        insnMap = allocateInstructionMap(map, fillp);
        //assert(assertBCICodingsOK());
        return insnMap;
    }

    /** Encode the given BCI as an instruction boundary number.
     *  For completeness, irregular (non-boundary) BCIs are
     *  encoded compactly immediately after the boundary numbers.
     *  This encoding is the identity mapping outside 0..length,
     *  and it is 1-1 everywhere.  All by itself this technique
     *  improved zipped rt.jar compression by 2.6%.
     */
    public int encodeBCI(int bci) {
        if (bci <= 0 || bci > getLength())  return bci;
        Object map0 = getInsnMap();
        int i, len;
        if (shrinkMaps && map0 instanceof byte[]) {
            byte[] map = (byte[]) map0;
            len = map.length;
            i = Arrays.binarySearch(map, (byte)(bci + Byte.MIN_VALUE));
        } else if (shrinkMaps && map0 instanceof short[]) {
            short[] map = (short[]) map0;
            len = map.length;
            i = Arrays.binarySearch(map, (short)(bci + Short.MIN_VALUE));
        } else {
            int[] map = (int[]) map0;
            len = map.length;
            i = Arrays.binarySearch(map, (int)bci);
        }
        assert(i != -1);
        assert(i != 0);
        assert(i != len);
        assert(i != -len-1);
        return (i >= 0) ? i : len + bci - (-i-1);
    }
    public int decodeBCI(int bciCode) {
        if (bciCode <= 0 || bciCode > getLength())  return bciCode;
        Object map0 = getInsnMap();
        int i, len;
        // len == map.length
        // If bciCode < len, result is map[bciCode], the common and fast case.
        // Otherwise, let map[i] be the smallest map[*] larger than bci.
        // Then, required by the return statement of encodeBCI:
        //   bciCode == len + bci - i
        // Thus:
        //   bci-i == bciCode-len
        //   map[i]-adj-i == bciCode-len ; adj in (0..map[i]-map[i-1])
        // We can solve this by searching for adjacent entries
        // map[i-1], map[i] such that:
        //   map[i-1]-(i-1) <= bciCode-len < map[i]-i
        // This can be approximated by searching map[i] for bciCode and then
        // linear searching backward.  Given the right i, we then have:
        //   bci == bciCode-len + i
        // This linear search is at its worst case for indexes in the beginning
        // of a large method, but it's not clear that this is a problem in
        // practice, since BCIs are usually on instruction boundaries.
        if (shrinkMaps && map0 instanceof byte[]) {
            byte[] map = (byte[]) map0;
            len = map.length;
            if (bciCode < len)
                return map[bciCode] - Byte.MIN_VALUE;
            i = Arrays.binarySearch(map, (byte)(bciCode + Byte.MIN_VALUE));
            if (i < 0)  i = -i-1;
            int key = bciCode-len + Byte.MIN_VALUE;
            for (;; i--) {
                if (map[i-1]-(i-1) <= key)  break;
            }
        } else if (shrinkMaps && map0 instanceof short[]) {
            short[] map = (short[]) map0;
            len = map.length;
            if (bciCode < len)
                return map[bciCode] - Short.MIN_VALUE;
            i = Arrays.binarySearch(map, (short)(bciCode + Short.MIN_VALUE));
            if (i < 0)  i = -i-1;
            int key = bciCode-len + Short.MIN_VALUE;
            for (;; i--) {
                if (map[i-1]-(i-1) <= key)  break;
            }
        } else {
            int[] map = (int[]) map0;
            len = map.length;
            if (bciCode < len)
                return map[bciCode];
            i = Arrays.binarySearch(map, (int)bciCode);
            if (i < 0)  i = -i-1;
            int key = bciCode-len;
            for (;; i--) {
                if (map[i-1]-(i-1) <= key)  break;
            }
        }
        return bciCode-len + i;
    }

    public void finishRefs(ConstantPool.Index ix) {
        if (fixups != null) {
            fixups.finishRefs(ix);
            fixups = null;
        }
        // Code attributes are finished in ClassWriter.writeAttributes.
    }

    Instruction instructionAt(int pc) {
        return Instruction.at(bytes, pc);
    }

    static boolean flagsRequireCode(int flags) {
        // A method's flags force it to have a Code attribute,
        // if the flags are neither native nor abstract.
        return (flags & (Modifier.NATIVE | Modifier.ABSTRACT)) == 0;
    }

    public String toString() {
        return m+".Code";
    }

    /// Fetching values from my own array.
    public int getInt(int pc)    { return Instruction.getInt(bytes, pc); }
    public int getShort(int pc)  { return Instruction.getShort(bytes, pc); }
    public int getByte(int pc)   { return Instruction.getByte(bytes, pc); }
    void setInt(int pc, int x)   { Instruction.setInt(bytes, pc, x); }
    void setShort(int pc, int x) { Instruction.setShort(bytes, pc, x); }
    void setByte(int pc, int x)  { Instruction.setByte(bytes, pc, x); }

/* TEST CODE ONLY
    private boolean assertBCICodingsOK() {
        boolean ok = true;
        int len = java.lang.reflect.Array.getLength(insnMap);
        int base = 0;
        if (insnMap.getClass().getComponentType() == Byte.TYPE)
            base = Byte.MIN_VALUE;
        if (insnMap.getClass().getComponentType() == Short.TYPE)
            base = Short.MIN_VALUE;
        for (int i = -1, imax = getLength()+1; i <= imax; i++) {
            int bci = i;
            int enc = Math.min(-999, bci-1);
            int dec = enc;
            try {
                enc = encodeBCI(bci);
                dec = decodeBCI(enc);
            } catch (RuntimeException ee) {
                ee.printStackTrace();
            }
            if (dec == bci) {
                //System.out.println("BCI="+bci+(enc<len?"":"   ")+" enc="+enc);
                continue;
            }
            if (ok) {
                for (int q = 0; q <= 1; q++) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("bci "+(q==0?"map":"del")+"["+len+"] = {");
                    for (int j = 0; j < len; j++) {
                        int mapi = ((Number)java.lang.reflect.Array.get(insnMap, j)).intValue() - base;
                        mapi -= j*q;
                        sb.append(" "+mapi);
                    }
                    sb.append(" }");
                    System.out.println("*** "+sb);
                }
            }
            System.out.println("*** BCI="+bci+" enc="+enc+" dec="+dec);
            ok = false;
        }
        return ok;
    }
//*/
}
