/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Arrays;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * A parsed bytecode instruction.
 * Provides accessors to various relevant bits.
 * @author John Rose
 */
class Instruction  {
    protected byte[] bytes;  // bytecodes
    protected int pc;        // location of this instruction
    protected int bc;        // opcode of this instruction
    protected int w;         // 0 if normal, 1 if a _wide prefix at pc
    protected int length;    // bytes in this instruction

    protected boolean special;

    protected Instruction(byte[] bytes, int pc, int bc, int w, int length) {
        reset(bytes, pc, bc, w, length);
    }
    private void reset(byte[] bytes, int pc, int bc, int w, int length) {
        this.bytes = bytes;
        this.pc = pc;
        this.bc = bc;
        this.w = w;
        this.length = length;
    }

    public int getBC() {
        return bc;
    }
    public boolean isWide() {
        return w != 0;
    }
    public byte[] getBytes() {
        return bytes;
    }
    public int getPC() {
        return pc;
    }
    public int getLength() {
        return length;
    }
    public int getNextPC() {
        return pc + length;
    }

    public Instruction next() {
        int npc = pc + length;
        if (npc == bytes.length)
            return null;
        else
            return Instruction.at(bytes, npc, this);
    }

    public boolean isNonstandard() {
        return isNonstandard(bc);
    }

    public void setNonstandardLength(int length) {
        assert(isNonstandard());
        this.length = length;
    }


    /** A fake instruction at this pc whose next() will be at nextpc. */
    public Instruction forceNextPC(int nextpc) {
        int llength = nextpc - pc;
        return new Instruction(bytes, pc, -1, -1, llength);
    }

    public static Instruction at(byte[] bytes, int pc) {
        return Instruction.at(bytes, pc, null);
    }

    public static Instruction at(byte[] bytes, int pc, Instruction reuse) {
        int bc = getByte(bytes, pc);
        int prefix = -1;
        int w = 0;
        int length = BC_LENGTH[w][bc];
        if (length == 0) {
            // Hard cases:
            switch (bc) {
            case _wide:
                bc = getByte(bytes, pc+1);
                w = 1;
                length = BC_LENGTH[w][bc];
                if (length == 0) {
                    // unknown instruction; treat as one byte
                    length = 1;
                }
                break;
            case _tableswitch:
                return new TableSwitch(bytes, pc);
            case _lookupswitch:
                return new LookupSwitch(bytes, pc);
            default:
                // unknown instruction; treat as one byte
                length = 1;
                break;
            }
        }
        assert(length > 0);
        assert(pc+length <= bytes.length);
        // Speed hack:  Instruction.next reuses self if possible.
        if (reuse != null && !reuse.special) {
            reuse.reset(bytes, pc, bc, w, length);
            return reuse;
        }
        return new Instruction(bytes, pc, bc, w, length);
    }

    // Return the constant pool reference type, or 0 if none.
    public byte getCPTag() {
        return BC_TAG[w][bc];
    }

    // Return the constant pool index, or -1 if none.
    public int getCPIndex() {
        int indexLoc = BC_INDEX[w][bc];
        if (indexLoc == 0)  return -1;
        assert(w == 0);
        if (length == 2)
            return getByte(bytes, pc+indexLoc);  // _ldc opcode only
        else
            return getShort(bytes, pc+indexLoc);
    }

    public void setCPIndex(int cpi) {
        int indexLoc = BC_INDEX[w][bc];
        assert(indexLoc != 0);
        if (length == 2)
            setByte(bytes, pc+indexLoc, cpi);  // _ldc opcode only
        else
            setShort(bytes, pc+indexLoc, cpi);
        assert(getCPIndex() == cpi);
    }

    public ConstantPool.Entry getCPRef(ConstantPool.Entry[] cpMap) {
        int index = getCPIndex();
        return (index < 0) ? null : cpMap[index];
    }

    // Return the slot of the affected local, or -1 if none.
    public int getLocalSlot() {
        int slotLoc = BC_SLOT[w][bc];
        if (slotLoc == 0)  return -1;
        if (w == 0)
            return getByte(bytes, pc+slotLoc);
        else
            return getShort(bytes, pc+slotLoc);
    }

    // Return the target of the branch, or -1 if none.
    public int getBranchLabel() {
        int branchLoc = BC_BRANCH[w][bc];
        if (branchLoc == 0)  return -1;
        assert(w == 0);
        assert(length == 3 || length == 5);
        int offset;
        if (length == 3)
            offset = (short)getShort(bytes, pc+branchLoc);
        else
            offset = getInt(bytes, pc+branchLoc);
        assert(offset+pc >= 0);
        assert(offset+pc <= bytes.length);
        return offset+pc;
    }

    public void setBranchLabel(int targetPC) {
        int branchLoc = BC_BRANCH[w][bc];
        assert(branchLoc != 0);
        if (length == 3)
            setShort(bytes, pc+branchLoc, targetPC-pc);
        else
            setInt(bytes, pc+branchLoc, targetPC-pc);
        assert(targetPC == getBranchLabel());
    }

    // Return the trailing constant in the instruction (as a signed value).
    // Return 0 if there is none.
    public int getConstant() {
        int conLoc = BC_CON[w][bc];
        if (conLoc == 0)  return 0;
        switch (length - conLoc) {
        case 1: return (byte) getByte(bytes, pc+conLoc);
        case 2: return (short) getShort(bytes, pc+conLoc);
        }
        assert(false);
        return 0;
    }

    public void setConstant(int con) {
        int conLoc = BC_CON[w][bc];
        assert(conLoc != 0);
        switch (length - conLoc) {
        case 1: setByte(bytes, pc+conLoc, con); break;
        case 2: setShort(bytes, pc+conLoc, con); break;
        }
        assert(con == getConstant());
    }

    public abstract static class Switch extends Instruction {
        // Each case is a (value, label) pair, indexed 0 <= n < caseCount
        public abstract int  getCaseCount();
        public abstract int  getCaseValue(int n);
        public abstract int  getCaseLabel(int n);
        public abstract void setCaseCount(int caseCount);
        public abstract void setCaseValue(int n, int value);
        public abstract void setCaseLabel(int n, int targetPC);
        protected abstract int getLength(int caseCount);

        public int  getDefaultLabel()             { return intAt(0)+pc; }
        public void setDefaultLabel(int targetPC) { setIntAt(0, targetPC-pc); }

        protected int apc;        // aligned pc (table base)
        protected int intAt(int n) { return getInt(bytes, apc + n*4); }
        protected void setIntAt(int n, int x) { setInt(bytes, apc + n*4, x); }
        protected Switch(byte[] bytes, int pc, int bc) {
            super(bytes, pc, bc, /*w*/0, /*length*/0);
            this.apc = alignPC(pc+1);
            this.special = true;
            length = getLength(getCaseCount());
        }
        public int getAlignedPC() { return apc; }
        public String toString() {
            String s = super.toString();
            s += " Default:"+labstr(getDefaultLabel());
            int caseCount = getCaseCount();
            for (int i = 0; i < caseCount; i++) {
                s += "\n\tCase "+getCaseValue(i)+":"+labstr(getCaseLabel(i));
            }
            return s;
        }
        public static int alignPC(int apc) {
            while (apc % 4 != 0)  ++apc;
            return apc;
        }
    }

    public static class TableSwitch extends Switch {
        // apc:  (df, lo, hi, (hi-lo+1)*(label))
        public int getLowCase()        { return intAt(1); }
        public int getHighCase()       { return intAt(2); }
        public int getCaseCount()      { return intAt(2)-intAt(1)+1; }
        public int getCaseValue(int n) { return getLowCase()+n; }
        public int getCaseLabel(int n) { return intAt(3+n)+pc; }

        public void setLowCase(int val)  { setIntAt(1, val); }
        public void setHighCase(int val) { setIntAt(2, val); }
        public void setCaseLabel(int n, int tpc) { setIntAt(3+n, tpc-pc); }
        public void setCaseCount(int caseCount) {
            setHighCase(getLowCase() + caseCount - 1);
            length = getLength(caseCount);
        }
        public void setCaseValue(int n, int val) {
            if (n != 0)  throw new UnsupportedOperationException();
            int caseCount = getCaseCount();
            setLowCase(val);
            setCaseCount(caseCount);  // keep invariant
        }

        TableSwitch(byte[] bytes, int pc) {
            super(bytes, pc, _tableswitch);
        }
        protected int getLength(int caseCount) {
            return (apc-pc) + (3 + caseCount) * 4;
        }
    }

    public static class LookupSwitch extends Switch {
        // apc:  (df, nc, nc*(case, label))
        public int getCaseCount()      { return intAt(1); }
        public int getCaseValue(int n) { return intAt(2+n*2+0); }
        public int getCaseLabel(int n) { return intAt(2+n*2+1)+pc; }

        public void setCaseCount(int caseCount)  {
            setIntAt(1, caseCount);
            length = getLength(caseCount);
        }
        public void setCaseValue(int n, int val) { setIntAt(2+n*2+0, val); }
        public void setCaseLabel(int n, int tpc) { setIntAt(2+n*2+1, tpc-pc); }

        LookupSwitch(byte[] bytes, int pc) {
            super(bytes, pc, _lookupswitch);
        }
        protected int getLength(int caseCount) {
            return (apc-pc) + (2 + caseCount*2) * 4;
        }
    }

    /** Two instructions are equal if they have the same bytes. */
    public boolean equals(Object o) {
        return (o != null) && (o.getClass() == Instruction.class)
                && equals((Instruction) o);
    }

    public int hashCode() {
        int hash = 3;
        hash = 11 * hash + Arrays.hashCode(this.bytes);
        hash = 11 * hash + this.pc;
        hash = 11 * hash + this.bc;
        hash = 11 * hash + this.w;
        hash = 11 * hash + this.length;
        return hash;
    }

    public boolean equals(Instruction that) {
        if (this.pc != that.pc)            return false;
        if (this.bc != that.bc)            return false;
        if (this.w  != that.w)             return false;
        if (this.length  != that.length)   return false;
        for (int i = 1; i < length; i++) {
            if (this.bytes[this.pc+i] != that.bytes[that.pc+i])
                return false;
        }
        return true;
    }

    static String labstr(int pc) {
        if (pc >= 0 && pc < 100000)
            return ((100000+pc)+"").substring(1);
        return pc+"";
    }
    public String toString() {
        return toString(null);
    }
    public String toString(ConstantPool.Entry[] cpMap) {
        String s = labstr(pc) + ": ";
        if (bc >= _bytecode_limit) {
            s += Integer.toHexString(bc);
            return s;
        }
        if (w == 1)  s += "wide ";
        String bcname = (bc < BC_NAME.length)? BC_NAME[bc]: null;
        if (bcname == null) {
            return s+"opcode#"+bc;
        }
        s += bcname;
        int tag = getCPTag();
        if (tag != 0)  s += " "+ConstantPool.tagName(tag)+":";
        int idx = getCPIndex();
        if (idx >= 0)  s += (cpMap == null) ? ""+idx : "="+cpMap[idx].stringValue();
        int slt = getLocalSlot();
        if (slt >= 0)  s += " Local:"+slt;
        int lab = getBranchLabel();
        if (lab >= 0)  s += " To:"+labstr(lab);
        int con = getConstant();
        if (con != 0)  s += " Con:"+con;
        return s;
    }


    //public static byte constantPoolTagFor(int bc) { return BC_TAG[0][bc]; }

    /// Fetching values from byte arrays:

    public int getIntAt(int off) {
        return getInt(bytes, pc+off);
    }
    public int getShortAt(int off) {
        return getShort(bytes, pc+off);
    }
    public int getByteAt(int off) {
        return getByte(bytes, pc+off);
    }


    public static int getInt(byte[] bytes, int pc) {
        return (getShort(bytes, pc+0) << 16) + (getShort(bytes, pc+2) << 0);
    }
    public static int getShort(byte[] bytes, int pc) {
        return (getByte(bytes, pc+0) << 8) + (getByte(bytes, pc+1) << 0);
    }
    public static int getByte(byte[] bytes, int pc) {
        return bytes[pc] & 0xFF;
    }


    public static void setInt(byte[] bytes, int pc, int x) {
        setShort(bytes, pc+0, x >> 16);
        setShort(bytes, pc+2, x >> 0);
    }
    public static void setShort(byte[] bytes, int pc, int x) {
        setByte(bytes, pc+0, x >> 8);
        setByte(bytes, pc+1, x >> 0);
    }
    public static void setByte(byte[] bytes, int pc, int x) {
        bytes[pc] = (byte)x;
    }

    // some bytecode classifiers


    public static boolean isNonstandard(int bc) {
        return BC_LENGTH[0][bc] < 0;
    }

    public static int opLength(int bc) {
        int l = BC_LENGTH[0][bc];
        assert(l > 0);
        return l;
    }
    public static int opWideLength(int bc) {
        int l = BC_LENGTH[1][bc];
        assert(l > 0);
        return l;
    }

    public static boolean isLocalSlotOp(int bc) {
        return (bc < BC_SLOT[0].length && BC_SLOT[0][bc] > 0);
    }

    public static boolean isBranchOp(int bc) {
        return (bc < BC_BRANCH[0].length && BC_BRANCH[0][bc] > 0);
    }

    public static boolean isCPRefOp(int bc) {
        if (bc < BC_INDEX[0].length && BC_INDEX[0][bc] > 0)  return true;
        if (bc >= _xldc_op && bc < _xldc_limit)  return true;
        if (bc == _invokespecial_int || bc == _invokestatic_int) return true;
        return false;
    }

    public static byte getCPRefOpTag(int bc) {
        if (bc < BC_INDEX[0].length && BC_INDEX[0][bc] > 0)  return BC_TAG[0][bc];
        if (bc >= _xldc_op && bc < _xldc_limit)  return CONSTANT_LoadableValue;
        if (bc == _invokestatic_int || bc == _invokespecial_int) return CONSTANT_InterfaceMethodref;
        return CONSTANT_None;
    }

    public static boolean isFieldOp(int bc) {
        return (bc >= _getstatic && bc <= _putfield);
    }

    public static boolean isInvokeInitOp(int bc) {
        return (bc >= _invokeinit_op && bc < _invokeinit_limit);
    }

    public static boolean isSelfLinkerOp(int bc) {
        return (bc >= _self_linker_op && bc < _self_linker_limit);
    }

    /// Format definitions.

    static private final byte[][] BC_LENGTH  = new byte[2][0x100];
    static private final byte[][] BC_INDEX   = new byte[2][0x100];
    static private final byte[][] BC_TAG     = new byte[2][0x100];
    static private final byte[][] BC_BRANCH  = new byte[2][0x100];
    static private final byte[][] BC_SLOT    = new byte[2][0x100];
    static private final byte[][] BC_CON     = new byte[2][0x100];
    static private final String[] BC_NAME    = new String[0x100]; // debug only
    static private final String[][] BC_FORMAT  = new String[2][_bytecode_limit]; // debug only
    static {
        for (int i = 0; i < _bytecode_limit; i++) {
            BC_LENGTH[0][i] = -1;
            BC_LENGTH[1][i] = -1;
        }
        def("b", _nop, _dconst_1);
        def("bx", _bipush);
        def("bxx", _sipush);
        def("bk", _ldc);                                // do not pack
        def("bkk", _ldc_w, _ldc2_w);            // do not pack
        def("blwbll", _iload, _aload);
        def("b", _iload_0, _saload);
        def("blwbll", _istore, _astore);
        def("b", _istore_0, _lxor);
        def("blxwbllxx", _iinc);
        def("b", _i2l, _dcmpg);
        def("boo", _ifeq, _jsr);                        // pack oo
        def("blwbll", _ret);
        def("", _tableswitch, _lookupswitch);   // pack all ints, omit padding
        def("b", _ireturn, _return);
        def("bkf", _getstatic, _putfield);              // pack kf (base=Field)
        def("bkm", _invokevirtual, _invokestatic);      // pack kn (base=Method)
        def("bkixx", _invokeinterface);         // pack ki (base=IMethod), omit xx
        def("bkyxx", _invokedynamic);           // pack ky (base=Any), omit xx
        def("bkc", _new);                               // pack kc
        def("bx", _newarray);
        def("bkc", _anewarray);                 // pack kc
        def("b", _arraylength, _athrow);
        def("bkc", _checkcast, _instanceof);    // pack kc
        def("b", _monitorenter, _monitorexit);
        def("", _wide);
        def("bkcx", _multianewarray);           // pack kc
        def("boo", _ifnull, _ifnonnull);                // pack oo
        def("boooo", _goto_w, _jsr_w);          // pack oooo
        for (int i = 0; i < _bytecode_limit; i++) {
            //System.out.println(i+": l="+BC_LENGTH[0][i]+" i="+BC_INDEX[0][i]);
            //assert(BC_LENGTH[0][i] != -1);
            if (BC_LENGTH[0][i] == -1) {
                continue;  // unknown opcode
            }

            // Have a complete mapping, to support spurious _wide prefixes.
            if (BC_LENGTH[1][i] == -1)
                BC_LENGTH[1][i] = (byte)(1+BC_LENGTH[0][i]);
        }

        String names =
  "nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3 iconst_4 "+
  "iconst_5 lconst_0 lconst_1 fconst_0 fconst_1 fconst_2 dconst_0 dconst_1 "+
  "bipush sipush ldc ldc_w ldc2_w iload lload fload dload aload iload_0 "+
  "iload_1 iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 fload_0 fload_1 "+
  "fload_2 fload_3 dload_0 dload_1 dload_2 dload_3 aload_0 aload_1 aload_2 "+
  "aload_3 iaload laload faload daload aaload baload caload saload istore "+
  "lstore fstore dstore astore istore_0 istore_1 istore_2 istore_3 lstore_0 "+
  "lstore_1 lstore_2 lstore_3 fstore_0 fstore_1 fstore_2 fstore_3 dstore_0 "+
  "dstore_1 dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore "+
  "lastore fastore dastore aastore bastore castore sastore pop pop2 dup "+
  "dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap iadd ladd fadd dadd isub lsub "+
  "fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem lrem frem drem "+
  "ineg lneg fneg dneg ishl lshl ishr lshr iushr lushr iand land ior lor "+
  "ixor lxor iinc i2l i2f i2d l2i l2f l2d f2i f2l f2d d2i d2l d2f i2b i2c "+
  "i2s lcmp fcmpl fcmpg dcmpl dcmpg ifeq ifne iflt ifge ifgt ifle if_icmpeq "+
  "if_icmpne if_icmplt if_icmpge if_icmpgt if_icmple if_acmpeq if_acmpne "+
  "goto jsr ret tableswitch lookupswitch ireturn lreturn freturn dreturn "+
  "areturn return getstatic putstatic getfield putfield invokevirtual "+
  "invokespecial invokestatic invokeinterface invokedynamic new newarray "+
  "anewarray arraylength athrow checkcast instanceof monitorenter "+
  "monitorexit wide multianewarray ifnull ifnonnull goto_w jsr_w ";
        for (int bc = 0; names.length() > 0; bc++) {
            int sp = names.indexOf(' ');
            BC_NAME[bc] = names.substring(0, sp);
            names = names.substring(sp+1);
        }
    }
    public static String byteName(int bc) {
        String iname;
        if (bc < BC_NAME.length && BC_NAME[bc] != null) {
            iname = BC_NAME[bc];
        } else if (isSelfLinkerOp(bc)) {
            int idx = (bc - _self_linker_op);
            boolean isSuper = (idx >= _self_linker_super_flag);
            if (isSuper)  idx -= _self_linker_super_flag;
            boolean isAload = (idx >= _self_linker_aload_flag);
            if (isAload)  idx -= _self_linker_aload_flag;
            int origBC = _first_linker_op + idx;
            assert(origBC >= _first_linker_op && origBC <= _last_linker_op);
            iname = BC_NAME[origBC];
            iname += (isSuper ? "_super" : "_this");
            if (isAload)  iname = "aload_0&" + iname;
            iname = "*"+iname;
        } else if (isInvokeInitOp(bc)) {
            int idx = (bc - _invokeinit_op);
            switch (idx) {
            case _invokeinit_self_option:
                iname = "*invokespecial_init_this"; break;
            case _invokeinit_super_option:
                iname = "*invokespecial_init_super"; break;
            default:
                assert(idx == _invokeinit_new_option);
                iname = "*invokespecial_init_new"; break;
            }
        } else {
            switch (bc) {
            case _ildc:  iname = "*ildc"; break;
            case _fldc:  iname = "*fldc"; break;
            case _ildc_w:  iname = "*ildc_w"; break;
            case _fldc_w:  iname = "*fldc_w"; break;
            case _dldc2_w:  iname = "*dldc2_w"; break;
            case _cldc:  iname = "*cldc"; break;
            case _cldc_w:  iname = "*cldc_w"; break;
            case _qldc:  iname = "*qldc"; break;
            case _qldc_w:  iname = "*qldc_w"; break;
            case _byte_escape:  iname = "*byte_escape"; break;
            case _ref_escape:  iname = "*ref_escape"; break;
            case _end_marker:  iname = "*end"; break;
            default:  iname = "*bc#"+bc; break;
            }
        }
        return iname;
    }
    private static int BW = 4;  // width of classification field
    private static void def(String fmt, int bc) {
        def(fmt, bc, bc);
    }
    private static void def(String fmt, int from_bc, int to_bc) {
        String[] fmts = { fmt, null };
        if (fmt.indexOf('w') > 0) {
            fmts[1] = fmt.substring(fmt.indexOf('w'));
            fmts[0] = fmt.substring(0, fmt.indexOf('w'));
        }
        for (int w = 0; w <= 1; w++) {
            fmt = fmts[w];
            if (fmt == null)  continue;
            int length = fmt.length();
            int index  = Math.max(0, fmt.indexOf('k'));
            int tag    = CONSTANT_None;
            int branch = Math.max(0, fmt.indexOf('o'));
            int slot   = Math.max(0, fmt.indexOf('l'));
            int con    = Math.max(0, fmt.indexOf('x'));
            if (index > 0 && index+1 < length) {
                switch (fmt.charAt(index+1)) {
                    case 'c': tag = CONSTANT_Class; break;
                    case 'k': tag = CONSTANT_LoadableValue; break;
                    case 'f': tag = CONSTANT_Fieldref; break;
                    case 'm': tag = CONSTANT_Methodref; break;
                    case 'i': tag = CONSTANT_InterfaceMethodref; break;
                    case 'y': tag = CONSTANT_InvokeDynamic; break;
                }
                assert(tag != CONSTANT_None);
            } else if (index > 0 && length == 2) {
                assert(from_bc == _ldc);
                tag = CONSTANT_LoadableValue;  // _ldc opcode only
            }
            for (int bc = from_bc; bc <= to_bc; bc++) {
                BC_FORMAT[w][bc] = fmt;
                assert(BC_LENGTH[w][bc] == -1);
                BC_LENGTH[w][bc] = (byte) length;
                BC_INDEX[w][bc]  = (byte) index;
                BC_TAG[w][bc]    = (byte) tag;
                assert(!(index == 0 && tag != CONSTANT_None));
                BC_BRANCH[w][bc] = (byte) branch;
                BC_SLOT[w][bc]   = (byte) slot;
                assert(branch == 0 || slot == 0);   // not both branch & local
                assert(branch == 0 || index == 0);  // not both branch & cp
                assert(slot == 0   || index == 0);  // not both local & cp
                BC_CON[w][bc]    = (byte) con;
            }
        }
    }

    public static void opcodeChecker(byte[] code, ConstantPool.Entry[] cpMap,
            Package.Version clsVersion) throws FormatException {
        Instruction i = at(code, 0);
        while (i != null) {
            int opcode = i.getBC();
            if (opcode < _nop || opcode > _jsr_w) {
                String message = "illegal opcode: " + opcode + " " + i;
                throw new FormatException(message);
            }
            ConstantPool.Entry e = i.getCPRef(cpMap);
            if (e != null) {
                byte tag = i.getCPTag();
                boolean match = e.tagMatches(tag);
                if (!match &&
                        (i.bc == _invokespecial || i.bc == _invokestatic) &&
                        e.tagMatches(CONSTANT_InterfaceMethodref) &&
                        clsVersion.greaterThan(Constants.JAVA7_MAX_CLASS_VERSION)) {
                    match = true;
                }
                if (!match) {
                    String message = "illegal reference, expected type="
                            + ConstantPool.tagName(tag) + ": "
                            + i.toString(cpMap);
                    throw new FormatException(message);
                }
            }
            i = i.next();
        }
    }
    static class FormatException extends IOException {
        private static final long serialVersionUID = 3175572275651367015L;

        FormatException(String message) {
            super(message);
        }
    }
}
