/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javap;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Method;

import static com.sun.tools.classfile.OpCodes.*;

/*
 *  Write the contents of a Code attribute.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class CodeWriter extends BasicWriter {
    static CodeWriter instance(Context context) {
        CodeWriter instance = context.get(CodeWriter.class);
        if (instance == null)
            instance = new CodeWriter(context);
        return instance;
    }

    protected CodeWriter(Context context) {
        super(context);
        context.put(CodeWriter.class, this);
        attrWriter = AttributeWriter.instance(context);
        classWriter = ClassWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
    }

    void write(Code_attribute attr, ConstantPool constant_pool) {
        println("  Code:");
        writeVerboseHeader(attr, constant_pool);
        writeInstrs(attr);
        writeExceptionTable(attr);
        attrWriter.write(attr, attr.attributes, constant_pool);
    }

    public void writeVerboseHeader(Code_attribute attr, ConstantPool constant_pool) {
        Method method = classWriter.getMethod();
        String argCount;
        try {
            int n = method.descriptor.getParameterCount(constant_pool);
            if (!method.access_flags.is(AccessFlags.ACC_STATIC))
                ++n;  // for 'this'
            argCount = Integer.toString(n);
        } catch (ConstantPoolException e) {
            argCount = report(e);
        } catch (DescriptorException e) {
            argCount = report(e);
        }

        println("   Stack=" + attr.max_stack +
                ", Locals=" + attr.max_locals +
                ", Args_size=" + argCount);

    }

    public void writeInstrs(Code_attribute attr) {
        try {
            for (int pc = 0; pc < attr.code_length;) {
                print("   " + pc + ":\t");
                pc += writeInstr(attr, pc);
                println();
            }
        } catch (Code_attribute.InvalidIndex e) {
            println(report(e));
        }
    }

    public int writeInstr(Code_attribute attr, int pc)
            throws Code_attribute.InvalidIndex {
        String lP = "";
        int opcode = attr.getUnsignedByte(pc);
        int opcode2;
        String mnem;
        switch (opcode) {
            case opc_nonpriv:
            case opc_priv: {
                opcode2 = attr.getUnsignedByte(pc + 1);
                mnem = opcName((opcode << 8) + opcode2);
                if (mnem == null) {
                    mnem = opcName(opcode) + " " + opcode2;
                }
                print(mnem);
                return 2;
            }
            case opc_wide: {
                opcode2 = attr.getUnsignedByte(pc + 1);
                mnem = opcName((opcode << 8) + opcode2);
                if (mnem == null) {
                    print("bytecode " + opcode);
                    return 1;
                }
                print(mnem + " " + attr.getUnsignedShort(pc + 2));
                if (opcode2 == opc_iinc) {
                    print(", " + attr.getShort(pc + 4));
                    return 6;
                }
                return 4;
            }
        }
        mnem = opcName(opcode);
        if (mnem == null) {
            print("bytecode " + opcode);
            return 1;
        }
        if (opcode > opc_jsr_w) {
            print("bytecode " + opcode);
            return 1;
        }
        print(opcName(opcode));
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
                print("\t" + attr.getUnsignedByte(pc + 1));
                return 2;
            case opc_iinc:
                print("\t" + attr.getUnsignedByte(pc + 1) + ", " + attr.getByte(pc + 2));
                return 3;
            case opc_tableswitch:
                {
                    int tb = align(pc + 1);
                    int default_skip = attr.getInt(tb);
                    int low = attr.getInt(tb + 4);
                    int high = attr.getInt(tb + 8);
                    int count = high - low;
                    print("{ //" + low + " to " + high);
                    for (int i = 0; i <= count; i++) {
                        print("\n\t\t" + (i + low) + ": " + lP + (pc + attr.getInt(tb + 12 + 4 * i)) + ";");
                    }
                    print("\n\t\tdefault: " + lP + (default_skip + pc) + " }");
                    return tb - pc + 16 + count * 4;
                }
            case opc_lookupswitch:
                {
                    int tb = align(pc + 1);
                    int default_skip = attr.getInt(tb);
                    int npairs = attr.getInt(tb + 4);
                    print("{ //" + npairs);
                    for (int i = 1; i <= npairs; i++) {
                        print("\n\t\t" + attr.getInt(tb + i * 8) + ": " + lP + (pc + attr.getInt(tb + 4 + i * 8)) + ";");
                    }
                    print("\n\t\tdefault: " + lP + (default_skip + pc) + " }");
                    return tb - pc + (npairs + 1) * 8;
                }
            case opc_newarray:
                int type = attr.getUnsignedByte(pc + 1);
                switch (type) {
                    case T_BOOLEAN:
                        print(" boolean");
                        break;
                    case T_BYTE:
                        print(" byte");
                        break;
                    case T_CHAR:
                        print(" char");
                        break;
                    case T_SHORT:
                        print(" short");
                        break;
                    case T_INT:
                        print(" int");
                        break;
                    case T_LONG:
                        print(" long");
                        break;
                    case T_FLOAT:
                        print(" float");
                        break;
                    case T_DOUBLE:
                        print(" double");
                        break;
                    case T_CLASS:
                        print(" class");
                        break;
                    default:
                        print(" BOGUS TYPE:" + type);
                }
                return 2;
            case opc_anewarray:
                {
                    int index = attr.getUnsignedShort(pc + 1);
                    print("\t#" + index + "; //");
                    printConstant(index);
                    return 3;
                }
            case opc_sipush:
                print("\t" + attr.getShort(pc + 1));
                return 3;
            case opc_bipush:
                print("\t" + attr.getByte(pc + 1));
                return 2;
            case opc_ldc:
                {
                    int index = attr.getUnsignedByte(pc + 1);
                    print("\t#" + index + "; //");
                    printConstant(index);
                    return 2;
                }
            case opc_ldc_w:
            case opc_ldc2_w:
            case opc_instanceof:
            case opc_checkcast:
            case opc_new:
            case opc_putstatic:
            case opc_getstatic:
            case opc_putfield:
            case opc_getfield:
            case opc_invokevirtual:
            case opc_invokespecial:
            case opc_invokestatic:
                {
                    int index = attr.getUnsignedShort(pc + 1);
                    print("\t#" + index + "; //");
                    printConstant(index);
                    return 3;
                }
            case opc_invokeinterface:
                {
                    int index = attr.getUnsignedShort(pc + 1);
                    int nargs = attr.getUnsignedByte(pc + 3);
                    print("\t#" + index + ",  " + nargs + "; //");
                    printConstant(index);
                    return 5;
                }
            case opc_multianewarray:
                {
                    int index = attr.getUnsignedShort(pc + 1);
                    int dimensions = attr.getUnsignedByte(pc + 3);
                    print("\t#" + index + ",  " + dimensions + "; //");
                    printConstant(index);
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
                print("\t" + lP + (pc + attr.getShort(pc + 1)));
                return 3;
            case opc_jsr_w:
            case opc_goto_w:
                print("\t" + lP + (pc + attr.getInt(pc + 1)));
                return 5;
            default:
                return 1;
        }
    }

    public void writeExceptionTable(Code_attribute attr) {
        if (attr.exception_table_langth > 0) {
            println("  Exception table:");
            println("   from   to  target type");
            for (int i = 0; i < attr.exception_table.length; i++) {
                Code_attribute.Exception_data handler = attr.exception_table[i];
                printFixedWidthInt(handler.start_pc, 6);
                printFixedWidthInt(handler.end_pc, 6);
                printFixedWidthInt(handler.handler_pc, 6);
                print("   ");
                int catch_type = handler.catch_type;
                if (catch_type == 0) {
                    println("any");
                } else {
                    print("Class ");
                    println(constantWriter.stringValue(catch_type));
                    println("");
                }
            }
        }

    }

    private void printConstant(int index) {
        constantWriter.write(index);
    }

    private void printFixedWidthInt(int n, int width) {
        String s = String.valueOf(n);
        for (int i = s.length(); i < width; i++)
            print(" ");
        print(s);
    }

    private static int align(int n) {
        return (n + 3) & ~3;
    }

    private AttributeWriter attrWriter;
    private ClassWriter classWriter;
    private ConstantWriter constantWriter;
}
