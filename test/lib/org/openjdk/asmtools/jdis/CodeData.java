/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.asmtools.jdis;

import org.openjdk.asmtools.jasm.Tables;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import static org.openjdk.asmtools.jasm.OpcodeTables.Opcode;
import static org.openjdk.asmtools.jasm.OpcodeTables.opcode;
import static org.openjdk.asmtools.jasm.Tables.*;
import static org.openjdk.asmtools.jasm.Tables.AttrTag.ATT_RuntimeInvisibleTypeAnnotations;
import static org.openjdk.asmtools.jasm.Tables.AttrTag.ATT_RuntimeVisibleTypeAnnotations;
import static org.openjdk.asmtools.jdis.Utils.commentString;

/**
 * Code data for a code attribute in method members in a class of the Java Disassembler
 */
public class CodeData extends Indenter {

    /**
     * Raw byte array for the byte codes
     */
    protected byte[] code;
    /**
     * Limit for the stack size
     */
    protected int max_stack;

    /* CodeData Fields */
    /**
     * Limit for the number of local vars
     */
    protected int max_locals;
    /**
     * The remaining attributes of this class
     */
    protected ArrayList<AttrData> attrs = new ArrayList<>(0);        // AttrData

    // internal references
    protected ClassData cls;
    protected MethodData meth;
    /**
     * (parsed) Trap table, describes exceptions caught
     */
    private ArrayList<TrapData> trap_table = new ArrayList<>(0);   // TrapData
    /**
     * (parsed) Line Number table, describes source lines associated with ByteCode indexes
     */
    private ArrayList<LineNumData> lin_num_tb = new ArrayList<>(0);   // LineNumData
    /**
     * (parsed) Local Variable table, describes variable scopes associated with ByteCode
     * indexes
     */
    private ArrayList<LocVarData> loc_var_tb = new ArrayList<>(0);   // LocVarData
    /**
     * (parsed) stack map table, describes compiler hints for stack rep, associated with
     * ByteCode indexes
     */
    private ArrayList<StackMapData> stack_map = null;
    /**
     * The visible type annotations for this method
     */
    private ArrayList<TypeAnnotationData> visibleTypeAnnotations;
    /**
     * The invisible type annotations for this method
     */
    private ArrayList<TypeAnnotationData> invisibleTypeAnnotations;

    /**
     * (parsed) reversed bytecode index hash, associates labels with ByteCode indexes
     */
    private HashMap<Integer, iAtt> iattrs = new HashMap<>();
    private PrintWriter out;
    public CodeData(MethodData meth) {
        this.meth = meth;
        this.cls = meth.cls;
        this.out = cls.out;
    }

    private static int align(int n) {
        return (n + 3) & ~3;
    }
    /*-------------------------------------------------------- */

    private int getbyte(int pc) {
        return code[pc];
    }

    private int getUbyte(int pc) {
        return code[pc] & 0xFF;
    }

    private int getShort(int pc) {
        return (code[pc] << 8) | (code[pc + 1] & 0xFF);
    }

    private int getUShort(int pc) {
        return ((code[pc] << 8) | (code[pc + 1] & 0xFF)) & 0xFFFF;
    }

    private int getInt(int pc) {
        return (getShort(pc) << 16) | (getShort(pc + 2) & 0xFFFF);
    }

    protected iAtt get_iAtt(int pc) {
        Integer PC = pc;
        iAtt res = iattrs.get(PC);
        if (res == null) {
            res = new iAtt(this);
            iattrs.put(PC, res);
        }
        return res;
    }

    /*========================================================*/
    /* Read Methods */
    private void readLineNumTable(DataInputStream in) throws IOException {
        int len = in.readInt(); // attr_length
        int numlines = in.readUnsignedShort();
        lin_num_tb = new ArrayList<>(numlines);
        TraceUtils.traceln(3,  "CodeAttr:  LineNumTable[" + numlines + "] len=" + len);
        for (int l = 0; l < numlines; l++) {
            lin_num_tb.add(new LineNumData(in));
        }
    }

    private void readLocVarTable(DataInputStream in) throws IOException {
        int len = in.readInt(); // attr_length
        int numlines = in.readUnsignedShort();
        loc_var_tb = new ArrayList<>(numlines);
        TraceUtils.traceln(3,  "CodeAttr:  LocalVariableTable[" + numlines + "] len=" + len);
        for (int l = 0; l < numlines; l++) {
            loc_var_tb.add(new LocVarData(in));
        }
    }

    private void readTrapTable(DataInputStream in) throws IOException {
        int trap_table_len = in.readUnsignedShort();
        TraceUtils.traceln(3,  "CodeAttr:  TrapTable[" + trap_table_len + "]");
        trap_table = new ArrayList<>(trap_table_len);
        for (int l = 0; l < trap_table_len; l++) {
            trap_table.add(new TrapData(in, l));
        }
    }

    private void readStackMap(DataInputStream in) throws IOException {
        int len = in.readInt(); // attr_length
        int stack_map_len = in.readUnsignedShort();
        TraceUtils.traceln(3,  "CodeAttr:  Stack_Map: attrlen=" + len + " num=" + stack_map_len);
        stack_map = new ArrayList<>(stack_map_len);
        StackMapData.prevFramePC = 0;
        for (int k = 0; k < stack_map_len; k++) {
            stack_map.add(new StackMapData(this, in));
        }
    }

    private void readStackMapTable(DataInputStream in) throws IOException {
        int len = in.readInt(); // attr_length
        int stack_map_len = in.readUnsignedShort();
        TraceUtils.traceln(3,  "CodeAttr:  Stack_Map_Table: attrlen=" + len + " num=" + stack_map_len);
        stack_map = new ArrayList<>(stack_map_len);
        StackMapData.prevFramePC = 0;
        for (int k = 0; k < stack_map_len; k++) {
            stack_map.add(new StackMapData(this, in, true));
        }
    }

    private void readTypeAnnotations(DataInputStream in, boolean isInvisible) throws IOException  {
        int attrLength = in.readInt();
        // Read Type Annotations Attr
        int count = in.readShort();
        ArrayList<TypeAnnotationData> tannots = new ArrayList<>(count);
        TraceUtils.traceln(3,  "CodeAttr:   Runtime" +
                (isInvisible ? "Inv" : "V") +
                "isibleTypeAnnotation: attrlen=" +
                attrLength + " num=" + count);
        for (int index = 0; index < count; index++) {
            TraceUtils.traceln("\t\t\t[" + index +"]:");
            TypeAnnotationData tannot = new TypeAnnotationData(isInvisible, cls);
            tannot.read(in);
            tannots.add(tannot);
        }
        if (isInvisible) {
            invisibleTypeAnnotations = tannots;
        } else {
            visibleTypeAnnotations = tannots;
        }
    }

    /**
     * read
     * <p>
     * read and resolve the code attribute data called from MethodData. precondition:
     * NumFields has already been read from the stream.
     */
    public void read(DataInputStream in, int codeattrlen) throws IOException {

        // Read the code in the Code Attribute
        max_stack = in.readUnsignedShort();
        max_locals = in.readUnsignedShort();
        int codelen = in.readInt();
        TraceUtils.traceln(3,  "CodeAttr:  Codelen=" + codelen +
                " fulllen=" + codeattrlen +
                " max_stack=" + max_stack +
                " max_locals=" + max_locals);

        // read the raw code bytes
        code = new byte[codelen];
        in.read(code, 0, codelen);

        //read the trap table
        readTrapTable(in);

        // Read any attributes of the Code Attribute
        int nattr = in.readUnsignedShort();
        TraceUtils.traceln(3,  "CodeAttr: add.attr:" + nattr);
        for (int k = 0; k < nattr; k++) {
            int name_cpx = in.readUnsignedShort();
            // verify the Attrs name
            ConstantPool.Constant name_const = cls.pool.getConst(name_cpx);
            if (name_const != null && name_const.tag == ConstantPool.TAG.CONSTANT_UTF8) {
                String attrname = cls.pool.getString(name_cpx);
                TraceUtils.traceln(3,  "CodeAttr:  attr: " + attrname);
                // process the attr
                AttrTag attrtag = attrtag(attrname);
                switch (attrtag) {
                    case ATT_LineNumberTable:
                        readLineNumTable(in);
                        break;
                    case ATT_LocalVariableTable:
                        readLocVarTable(in);
                        break;
                    case ATT_StackMap:
                        readStackMap(in);
                        break;
                    case ATT_StackMapTable:
                        readStackMapTable(in);
                        break;
                    case ATT_RuntimeVisibleTypeAnnotations:
                    case ATT_RuntimeInvisibleTypeAnnotations:
                        readTypeAnnotations(in, attrtag == ATT_RuntimeInvisibleTypeAnnotations);
                        break;
                    default:
                        AttrData attr = new AttrData(cls);
                        int attrlen = in.readInt(); // attr_length
                        attr.read(name_cpx, attrlen, in);
                        attrs.add(attr);
                        break;
                }
            }
        }
    }

    /*========================================================*/
    /* Code Resolution Methods */
    private int checkForLabelRef(int pc) {
        //         throws IOException {
        int opc = getUbyte(pc);
        Opcode opcode = opcode(opc);
        switch (opcode) {
            case opc_tableswitch: {
                int tb = align(pc + 1);
                int default_skip = getInt(tb); /* default skip pamount */

                int low = getInt(tb + 4);
                int high = getInt(tb + 8);
                int count = high - low;
                for (int i = 0; i <= count; i++) {
                    get_iAtt(pc + getInt(tb + 12 + 4 * i)).referred = true;
                }
                get_iAtt(default_skip + pc).referred = true;
                return tb - pc + 16 + count * 4;
            }
            case opc_lookupswitch: {
                int tb = align(pc + 1);
                int default_skip = getInt(tb); /* default skip pamount */

                int npairs = getInt(tb + 4);
                for (int i = 1; i <= npairs; i++) {
                    get_iAtt(pc + getInt(tb + 4 + i * 8)).referred = true;
                }
                get_iAtt(default_skip + pc).referred = true;
                return tb - pc + (npairs + 1) * 8;
            }
            case opc_jsr:
            case opc_goto:
            case opc_ifeq:
            case opc_ifge:
            case opc_ifgt:
            case opc_ifle:
            case opc_iflt:
            case opc_ifne:
            case opc_if_icmpeq:
            case opc_if_icmpne:
            case opc_if_icmpge:
            case opc_if_icmpgt:
            case opc_if_icmple:
            case opc_if_icmplt:
            case opc_if_acmpeq:
            case opc_if_acmpne:
            case opc_ifnull:
            case opc_ifnonnull:
                get_iAtt(pc + getShort(pc + 1)).referred = true;
                return 3;
            case opc_jsr_w:
            case opc_goto_w:
                get_iAtt(pc + getInt(pc + 1)).referred = true;
                return 5;
            case opc_wide:
            case opc_nonpriv:
            case opc_priv:
                int opc2 = (opcode.value() << 8) + getUbyte(pc + 1);
                opcode = opcode(opc2);
        }
        try {
            int opclen = opcode.length();
            return opclen == 0 ? 1 : opclen;  // bugfix for 4614404
        } catch (ArrayIndexOutOfBoundsException e) {
            return 1;
        }
    } // end checkForLabelRef

    private void loadLabelTable() {
        for (int pc = 0; pc < code.length; ) {
            pc = pc + checkForLabelRef(pc);
        }
    }

    private void loadLineNumTable() {
        for (LineNumData entry : lin_num_tb) {
            get_iAtt(entry.start_pc).lnum = entry.line_number;
        }
    }

    private void loadStackMap() {
        for (StackMapData entry : stack_map) {
            get_iAtt(entry.start_pc).stackMapEntry = entry;
        }
    }

    private void loadLocVarTable() {
        for (LocVarData entry : loc_var_tb) {
            get_iAtt(entry.start_pc).add_var(entry);
            get_iAtt(entry.start_pc + entry.length).add_endvar(entry);
        }
    }

    private void loadTrapTable() {
        for (TrapData entry : trap_table) {
            get_iAtt(entry.start_pc).add_trap(entry);
            get_iAtt(entry.end_pc).add_endtrap(entry);
            get_iAtt(entry.handler_pc).add_handler(entry);
        }
    }

    /*========================================================*/
    /* Print Methods */
    private void PrintConstant(int cpx) {
        out.print("\t");
        cls.pool.PrintConstant(out, cpx);
    }

    private void PrintCommentedConstant(int cpx) {
        out.print(commentString(cls.pool.ConstantStrValue(cpx)));
    }

    private int printInstr(int pc) {
        boolean pr_cpx = meth.options.contains(Options.PR.CPX);
        int opc = getUbyte(pc);
        int opc2;
        Opcode opcode = opcode(opc);
        Opcode opcode2;
        String mnem;
        switch (opcode) {
            case opc_nonpriv:
            case opc_priv:
                opc2 = getUbyte(pc + 1);
                int finalopc = (opc << 8) + opc2;
                opcode2 = opcode(finalopc);
                if (opcode2 == null) {
// assume all (even nonexistent) priv and nonpriv instructions
// are 2 bytes long
                    mnem = opcode.parsekey() + " " + opc2;
                } else {
                    mnem = opcode2.parsekey();
                }
                out.print(mnem);
                return 2;
            case opc_wide: {
                opc2 = getUbyte(pc + 1);
                int finalopcwide = (opc << 8) + opc2;
                opcode2 = opcode(finalopcwide);
                if (opcode2 == null) {
// nonexistent opcode - but we have to print something
                    out.print("bytecode " + opcode);
                    return 1;
                } else {
                    mnem = opcode2.parsekey();
                }
                out.print(mnem + " " + getUShort(pc + 2));
                if (opcode2 == Opcode.opc_iinc_w) {
                    out.print(", " + getShort(pc + 4));
                    return 6;
                }
                return 4;
            }
        }
        mnem = opcode.parsekey();
        if (mnem == null) {
// nonexistent opcode - but we have to print something
            out.print("bytecode " + opcode);
            return 1;
        }
        if (opcode.value() >= Opcode.opc_bytecode.value()) {
// pseudo opcodes should be printed as bytecodes
            out.print("bytecode " + opcode);
            return 1;
        }
        out.print(opcode.parsekey());
// TraceUtils.traceln("****** [CodeData.printInstr]: got an '" + opcode.parseKey() + "' [" + opc + "] instruction ****** ");
        switch (opcode) {
            case opc_aload:
            case opc_astore:
            case opc_fload:
            case opc_fstore:
            case opc_iload:
            case opc_istore:
            case opc_lload:
            case opc_lstore:
            case opc_dload:
            case opc_dstore:
            case opc_ret:
                out.print("\t" + getUbyte(pc + 1));
                return 2;
            case opc_iinc:
                out.print("\t" + getUbyte(pc + 1) + ", " + getbyte(pc + 2));
                return 3;
            case opc_tableswitch: {
                int tb = align(pc + 1);
                int default_skip = getInt(tb); /* default skip pamount */

                int low = getInt(tb + 4);
                int high = getInt(tb + 8);
                int count = high - low;
                out.print("{ //" + low + " to " + high);
                for (int i = 0; i <= count; i++) {
                    out.print("\n\t\t" + (i + low) + ": " + meth.lP + (pc + getInt(tb + 12 + 4 * i)) + ";");
                }
                out.print("\n\t\tdefault: " + meth.lP + (default_skip + pc) + " }");
                return tb - pc + 16 + count * 4;
            }
            case opc_lookupswitch: {
                int tb = align(pc + 1);
                int default_skip = getInt(tb);
                int npairs = getInt(tb + 4);
                out.print("{ //" + npairs);
                for (int i = 1; i <= npairs; i++) {
                    out.print("\n\t\t" + getInt(tb + i * 8) + ": " + meth.lP + (pc + getInt(tb + 4 + i * 8)) + ";");
                }
                out.print("\n\t\tdefault: " + meth.lP + (default_skip + pc) + " }");
                return tb - pc + (npairs + 1) * 8;
            }
            case opc_newarray:
                int tp = getUbyte(pc + 1);
                BasicType type = basictype(tp);
                switch (type) {
                    case T_BOOLEAN:
                        out.print(" boolean");
                        break;
                    case T_BYTE:
                        out.print(" byte");
                        break;
                    case T_CHAR:
                        out.print(" char");
                        break;
                    case T_SHORT:
                        out.print(" short");
                        break;
                    case T_INT:
                        out.print(" int");
                        break;
                    case T_LONG:
                        out.print(" long");
                        break;
                    case T_FLOAT:
                        out.print(" float");
                        break;
                    case T_DOUBLE:
                        out.print(" double");
                        break;
                    case T_CLASS:
                        out.print(" class");
                        break;
                    default:
                        out.print(" BOGUS TYPE:" + type);
                }
                return 2;
            case opc_ldc_w:
            case opc_ldc2_w: {
                // added printing of the tag: Method/Interface to clarify
                // interpreting CONSTANT_MethodHandle_info:reference_kind
                // Example: ldc_w Dynamic REF_invokeStatic:Method CondyIndy.condy_bsm
                cls.pool.setPrintTAG(true);
                int index = getUShort(pc + 1);
                if (pr_cpx) {
                    out.print("\t#" + index + "; //");
                }
                PrintConstant(index);
                cls.pool.setPrintTAG(false);
                return 3;
            }
            case opc_anewarray:
            case opc_instanceof:
            case opc_checkcast:
            case opc_new:
            case opc_putstatic:
            case opc_getstatic:
            case opc_putfield:
            case opc_getfield:
            case opc_invokevirtual:
            case opc_invokespecial:
            case opc_invokestatic: {
                int index = getUShort(pc + 1);
                if (pr_cpx) {
                    out.print("\t#" + index + "; //");
                }
                PrintConstant(index);
                return 3;
            }
            case opc_sipush:
                out.print("\t" + getShort(pc + 1));
                return 3;
            case opc_bipush:
                out.print("\t" + getbyte(pc + 1));
                return 2;
            case opc_ldc: {
                // added printing of the tag: Method/Interface to clarify
                // interpreting CONSTANT_MethodHandle_info:reference_kind
                // Example: ldc Dynamic REF_invokeStatic:Method CondyIndy.condy_bsm
                cls.pool.setPrintTAG(true);
                int index = getUbyte(pc + 1);
                if (pr_cpx) {
                    out.print("\t#" + index + "; //");
                }
                PrintConstant(index);
                cls.pool.setPrintTAG(false);
                return 2;
            }
            case opc_invokeinterface: {
                int index = getUShort(pc + 1), nargs = getUbyte(pc + 3);
                if (pr_cpx) {
                    out.print("\t#" + index + ",  " + nargs + "; //");
                    PrintConstant(index);
                } else {
                    PrintConstant(index);
                    out.print(",  " + nargs); // args count
                }
                return 5;
            }
            case opc_invokedynamic: { // JSR-292
                cls.pool.setPrintTAG(true);
                int index = getUShort(pc + 1);
                // getUbyte(pc + 3); // reserved byte
                // getUbyte(pc + 4); // reserved byte
                if (pr_cpx) {
                    out.print("\t#" + index + ";\t");
                    PrintCommentedConstant(index);
                } else {
                    PrintConstant(index);
                }
                cls.pool.setPrintTAG(false);
                return 5;
            }
            case opc_multianewarray: {
                int index = getUShort(pc + 1), dimensions = getUbyte(pc + 3);
                if (pr_cpx) {
                    out.print("\t#" + index + ",  " + dimensions + "; //");
                    PrintConstant(index);
                } else {
                    PrintConstant(index);
                    out.print(",  " + dimensions); // dimensions count
                }
                return 4;
            }
            case opc_jsr:
            case opc_goto:
            case opc_ifeq:
            case opc_ifge:
            case opc_ifgt:
            case opc_ifle:
            case opc_iflt:
            case opc_ifne:
            case opc_if_icmpeq:
            case opc_if_icmpne:
            case opc_if_icmpge:
            case opc_if_icmpgt:
            case opc_if_icmple:
            case opc_if_icmplt:
            case opc_if_acmpeq:
            case opc_if_acmpne:
            case opc_ifnull:
            case opc_ifnonnull:
                out.print("\t" + meth.lP + (pc + getShort(pc + 1)));
                return 3;
            case opc_jsr_w:
            case opc_goto_w:
                out.print("\t" + meth.lP + (pc + getInt(pc + 1)));
                return 5;
            default:
                return 1;
        }
    } // end printInstr

    /**
     * print
     * <p>
     * prints the code data to the current output stream. called from MethodData.
     */
    public void print() throws IOException {
        if (!lin_num_tb.isEmpty()) {
            loadLineNumTable();
        }
        if (stack_map != null) {
            loadStackMap();
        }
        if (!meth.options.contains(Options.PR.PC)) {
            loadLabelTable();
        }
        loadTrapTable();
        if (!loc_var_tb.isEmpty()) {
            loadLocVarTable();
        }

        out.println();
        out.println("\tstack " + max_stack + " locals " + max_locals);

        // Need to print ParamAnnotations here.
        meth.printPAnnotations();

        out.println(getIndentString() + "{");

        iAtt iatt = iattrs.get(0);
        for (int pc = 0; pc < code.length; ) {
            if (iatt != null) {
                iatt.printBegins(); // equ. print("\t");
            } else {
                out.print("\t");
            }
            if (meth.options.contains(Options.PR.PC)) {
                out.print(pc + ":\t");
            } else if ((iatt != null) && iatt.referred) {
                out.print(meth.lP + pc + ":\t");
            } else {
                out.print("\t");
            }
            if (iatt != null) {
                iatt.printStackMap();
            }
            pc = pc + printInstr(pc);
            out.println(";");
            iatt = iattrs.get(pc);
            if (iatt != null) {
                iatt.printEnds();
            }
        }
        // the right brace can be labelled:
        if (iatt != null) {
            iatt.printBegins(); // equ. print("\t");
            if (iatt.referred) {
                out.print(meth.lP + code.length + ":\t");
            }
            iatt.printStackMap();
            out.println();
        }
        // print TypeAnnotations
        if (visibleTypeAnnotations != null) {
            out.println();
            for (TypeAnnotationData visad : visibleTypeAnnotations) {
                visad.print(out, getIndentString());
                out.println();
            }
        }
        if (invisibleTypeAnnotations != null) {
            for (TypeAnnotationData invisad : invisibleTypeAnnotations) {
                invisad.print(out, getIndentString());
                out.println();
            }
        }
        // end of code
        out.println(getIndentString() + "}");
    }


    public static class LocVarData {

        short start_pc, length, name_cpx, sig_cpx, slot;

        public LocVarData(DataInputStream in) throws IOException {
            start_pc = in.readShort();
            length = in.readShort();
            name_cpx = in.readShort();
            sig_cpx = in.readShort();
            slot = in.readShort();
        }
    }

    /* Code Data inner classes */
    class LineNumData {

        short start_pc, line_number;

        public LineNumData(DataInputStream in) throws IOException {
            start_pc = in.readShort();
            line_number = in.readShort();
        }
    }

}
