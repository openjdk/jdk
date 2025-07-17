/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.BitSet;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.Code;
import com.sun.org.apache.bcel.internal.classfile.CodeException;
import com.sun.org.apache.bcel.internal.classfile.ConstantFieldref;
import com.sun.org.apache.bcel.internal.classfile.ConstantInterfaceMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantInvokeDynamic;
import com.sun.org.apache.bcel.internal.classfile.ConstantMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantNameAndType;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.LocalVariableTable;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Convert code into HTML file.
 */
final class CodeHTML {

    private static boolean wide;
    private final String className; // name of current class
    // private Method[] methods; // Methods to print
    private final PrintWriter printWriter; // file to write to
    private BitSet gotoSet;
    private final ConstantPool constantPool;
    private final ConstantHTML constantHtml;

    CodeHTML(final String dir, final String className, final Method[] methods, final ConstantPool constantPool, final ConstantHTML constantHtml,
        final Charset charset) throws IOException {
        this.className = className;
//        this.methods = methods;
        this.constantPool = constantPool;
        this.constantHtml = constantHtml;
        try (PrintWriter newPrintWriter = new PrintWriter(dir + className + "_code.html", charset.name())) {
            printWriter = newPrintWriter;
            printWriter.print("<HTML><head><meta charset=\"");
            printWriter.print(charset.name());
            printWriter.println("\"></head>");
            printWriter.println("<BODY BGCOLOR=\"#C0C0C0\">");
            for (int i = 0; i < methods.length; i++) {
                writeMethod(methods[i], i);
            }
            printWriter.println("</BODY></HTML>");
        }
    }

    /**
     * Disassemble a stream of byte codes and return the string representation.
     *
     * @param stream data input stream
     * @return String representation of byte code
     */
    private String codeToHTML(final ByteSequence bytes, final int methodNumber) throws IOException {
        final short opcode = (short) bytes.readUnsignedByte();
        String name;
        String signature;
        int defaultOffset = 0;
        int low;
        int high;
        int index;
        int classIndex;
        int vindex;
        int constant;
        int[] jumpTable;
        int noPadBytes = 0;
        int offset;
        final StringBuilder buf = new StringBuilder(256); // CHECKSTYLE IGNORE MagicNumber
        buf.append("<TT>").append(Const.getOpcodeName(opcode)).append("</TT></TD><TD>");
        /*
         * Special case: Skip (0-3) padding bytes, i.e., the following bytes are 4-byte-aligned
         */
        if (opcode == Const.TABLESWITCH || opcode == Const.LOOKUPSWITCH) {
            final int remainder = bytes.getIndex() % 4;
            noPadBytes = remainder == 0 ? 0 : 4 - remainder;
            for (int i = 0; i < noPadBytes; i++) {
                bytes.readByte();
            }
            // Both cases have a field default_offset in common
            defaultOffset = bytes.readInt();
        }
        switch (opcode) {
        case Const.TABLESWITCH:
            low = bytes.readInt();
            high = bytes.readInt();
            offset = bytes.getIndex() - 12 - noPadBytes - 1;
            defaultOffset += offset;
            buf.append("<TABLE BORDER=1><TR>");
            // Print switch indices in first row (and default)
            jumpTable = new int[high - low + 1];
            for (int i = 0; i < jumpTable.length; i++) {
                jumpTable[i] = offset + bytes.readInt();
                buf.append("<TH>").append(low + i).append("</TH>");
            }
            buf.append("<TH>default</TH></TR>\n<TR>");
            // Print target and default indices in second row
            for (final int element : jumpTable) {
                buf.append("<TD><A HREF=\"#code").append(methodNumber).append("@").append(element).append("\">").append(element).append("</A></TD>");
            }
            buf.append("<TD><A HREF=\"#code").append(methodNumber).append("@").append(defaultOffset).append("\">").append(defaultOffset)
                .append("</A></TD></TR>\n</TABLE>\n");
            break;
        /*
         * Lookup switch has variable length arguments.
         */
        case Const.LOOKUPSWITCH:
            final int npairs = bytes.readInt();
            offset = bytes.getIndex() - 8 - noPadBytes - 1;
            jumpTable = new int[npairs];
            defaultOffset += offset;
            buf.append("<TABLE BORDER=1><TR>");
            // Print switch indices in first row (and default)
            for (int i = 0; i < npairs; i++) {
                final int match = bytes.readInt();
                jumpTable[i] = offset + bytes.readInt();
                buf.append("<TH>").append(match).append("</TH>");
            }
            buf.append("<TH>default</TH></TR>\n<TR>");
            // Print target and default indices in second row
            for (int i = 0; i < npairs; i++) {
                buf.append("<TD><A HREF=\"#code").append(methodNumber).append("@").append(jumpTable[i]).append("\">").append(jumpTable[i])
                    .append("</A></TD>");
            }
            buf.append("<TD><A HREF=\"#code").append(methodNumber).append("@").append(defaultOffset).append("\">").append(defaultOffset)
                .append("</A></TD></TR>\n</TABLE>\n");
            break;
        /*
         * Two address bytes + offset from start of byte stream form the jump target.
         */
        case Const.GOTO:
        case Const.IFEQ:
        case Const.IFGE:
        case Const.IFGT:
        case Const.IFLE:
        case Const.IFLT:
        case Const.IFNE:
        case Const.IFNONNULL:
        case Const.IFNULL:
        case Const.IF_ACMPEQ:
        case Const.IF_ACMPNE:
        case Const.IF_ICMPEQ:
        case Const.IF_ICMPGE:
        case Const.IF_ICMPGT:
        case Const.IF_ICMPLE:
        case Const.IF_ICMPLT:
        case Const.IF_ICMPNE:
        case Const.JSR:
            index = bytes.getIndex() + bytes.readShort() - 1;
            buf.append("<A HREF=\"#code").append(methodNumber).append("@").append(index).append("\">").append(index).append("</A>");
            break;
        /*
         * Same for 32-bit wide jumps
         */
        case Const.GOTO_W:
        case Const.JSR_W:
            final int windex = bytes.getIndex() + bytes.readInt() - 1;
            buf.append("<A HREF=\"#code").append(methodNumber).append("@").append(windex).append("\">").append(windex).append("</A>");
            break;
        /*
         * Index byte references local variable (register)
         */
        case Const.ALOAD:
        case Const.ASTORE:
        case Const.DLOAD:
        case Const.DSTORE:
        case Const.FLOAD:
        case Const.FSTORE:
        case Const.ILOAD:
        case Const.ISTORE:
        case Const.LLOAD:
        case Const.LSTORE:
        case Const.RET:
            if (wide) {
                vindex = bytes.readShort();
                wide = false; // Clear flag
            } else {
                vindex = bytes.readUnsignedByte();
            }
            buf.append("%").append(vindex);
            break;
        /*
         * Remember wide byte which is used to form a 16-bit address in the following instruction. Relies on that the method is
         * called again with the following opcode.
         */
        case Const.WIDE:
            wide = true;
            buf.append("(wide)");
            break;
        /*
         * Array of basic type.
         */
        case Const.NEWARRAY:
            buf.append("<FONT COLOR=\"#00FF00\">").append(Const.getTypeName(bytes.readByte())).append("</FONT>");
            break;
        /*
         * Access object/class fields.
         */
        case Const.GETFIELD:
        case Const.GETSTATIC:
        case Const.PUTFIELD:
        case Const.PUTSTATIC:
            index = bytes.readShort();
            final ConstantFieldref c1 = constantPool.getConstant(index, Const.CONSTANT_Fieldref, ConstantFieldref.class);
            classIndex = c1.getClassIndex();
            name = constantPool.getConstantString(classIndex, Const.CONSTANT_Class);
            name = Utility.compactClassName(name, false);
            index = c1.getNameAndTypeIndex();
            final String fieldName = constantPool.constantToString(index, Const.CONSTANT_NameAndType);
            if (name.equals(className)) { // Local field
                buf.append("<A HREF=\"").append(className).append("_methods.html#field").append(fieldName).append("\" TARGET=Methods>").append(fieldName)
                    .append("</A>\n");
            } else {
                buf.append(constantHtml.referenceConstant(classIndex)).append(".").append(fieldName);
            }
            break;
        /*
         * Operands are references to classes in constant pool
         */
        case Const.CHECKCAST:
        case Const.INSTANCEOF:
        case Const.NEW:
            index = bytes.readShort();
            buf.append(constantHtml.referenceConstant(index));
            break;
        /*
         * Operands are references to methods in constant pool
         */
        case Const.INVOKESPECIAL:
        case Const.INVOKESTATIC:
        case Const.INVOKEVIRTUAL:
        case Const.INVOKEINTERFACE:
        case Const.INVOKEDYNAMIC:
            final int mIndex = bytes.readShort();
            String str;
            if (opcode == Const.INVOKEINTERFACE) { // Special treatment needed
                bytes.readUnsignedByte(); // Redundant
                bytes.readUnsignedByte(); // Reserved
//                    int nargs = bytes.readUnsignedByte(); // Redundant
//                    int reserved = bytes.readUnsignedByte(); // Reserved
                final ConstantInterfaceMethodref c = constantPool.getConstant(mIndex, Const.CONSTANT_InterfaceMethodref, ConstantInterfaceMethodref.class);
                classIndex = c.getClassIndex();
                index = c.getNameAndTypeIndex();
                name = Class2HTML.referenceClass(classIndex);
            } else if (opcode == Const.INVOKEDYNAMIC) { // Special treatment needed
                bytes.readUnsignedByte(); // Reserved
                bytes.readUnsignedByte(); // Reserved
                final ConstantInvokeDynamic c = constantPool.getConstant(mIndex, Const.CONSTANT_InvokeDynamic, ConstantInvokeDynamic.class);
                index = c.getNameAndTypeIndex();
                name = "#" + c.getBootstrapMethodAttrIndex();
            } else {
                // UNDONE: Java8 now allows INVOKESPECIAL and INVOKESTATIC to
                // reference EITHER a Methodref OR an InterfaceMethodref.
                // Not sure if that affects this code or not. (markro)
                final ConstantMethodref c = constantPool.getConstant(mIndex, Const.CONSTANT_Methodref, ConstantMethodref.class);
                classIndex = c.getClassIndex();
                index = c.getNameAndTypeIndex();
                name = Class2HTML.referenceClass(classIndex);
            }
            str = Class2HTML.toHTML(constantPool.constantToString(constantPool.getConstant(index, Const.CONSTANT_NameAndType)));
            // Get signature, i.e., types
            final ConstantNameAndType c2 = constantPool.getConstant(index, Const.CONSTANT_NameAndType, ConstantNameAndType.class);
            signature = constantPool.constantToString(c2.getSignatureIndex(), Const.CONSTANT_Utf8);
            final String[] args = Utility.methodSignatureArgumentTypes(signature, false);
            final String type = Utility.methodSignatureReturnType(signature, false);
            buf.append(name).append(".<A HREF=\"").append(className).append("_cp.html#cp").append(mIndex).append("\" TARGET=ConstantPool>").append(str)
                .append("</A>").append("(");
            // List arguments
            for (int i = 0; i < args.length; i++) {
                buf.append(Class2HTML.referenceType(args[i]));
                if (i < args.length - 1) {
                    buf.append(", ");
                }
            }
            // Attach return type
            buf.append("):").append(Class2HTML.referenceType(type));
            break;
        /*
         * Operands are references to items in constant pool
         */
        case Const.LDC_W:
        case Const.LDC2_W:
            index = bytes.readShort();
            buf.append("<A HREF=\"").append(className).append("_cp.html#cp").append(index).append("\" TARGET=\"ConstantPool\">")
                .append(Class2HTML.toHTML(constantPool.constantToString(index, constantPool.getConstant(index).getTag()))).append("</a>");
            break;
        case Const.LDC:
            index = bytes.readUnsignedByte();
            buf.append("<A HREF=\"").append(className).append("_cp.html#cp").append(index).append("\" TARGET=\"ConstantPool\">")
                .append(Class2HTML.toHTML(constantPool.constantToString(index, constantPool.getConstant(index).getTag()))).append("</a>");
            break;
        /*
         * Array of references.
         */
        case Const.ANEWARRAY:
            index = bytes.readShort();
            buf.append(constantHtml.referenceConstant(index));
            break;
        /*
         * Multidimensional array of references.
         */
        case Const.MULTIANEWARRAY:
            index = bytes.readShort();
            final int dimensions = bytes.readByte();
            buf.append(constantHtml.referenceConstant(index)).append(":").append(dimensions).append("-dimensional");
            break;
        /*
         * Increment local variable.
         */
        case Const.IINC:
            if (wide) {
                vindex = bytes.readShort();
                constant = bytes.readShort();
                wide = false;
            } else {
                vindex = bytes.readUnsignedByte();
                constant = bytes.readByte();
            }
            buf.append("%").append(vindex).append(" ").append(constant);
            break;
        default:
            if (Const.getNoOfOperands(opcode) > 0) {
                for (int i = 0; i < Const.getOperandTypeCount(opcode); i++) {
                    switch (Const.getOperandType(opcode, i)) {
                    case Const.T_BYTE:
                        buf.append(bytes.readUnsignedByte());
                        break;
                    case Const.T_SHORT: // Either branch or index
                        buf.append(bytes.readShort());
                        break;
                    case Const.T_INT:
                        buf.append(bytes.readInt());
                        break;
                    default: // Never reached
                        throw new IllegalStateException("Unreachable default case reached! " + Const.getOperandType(opcode, i));
                    }
                    buf.append("&nbsp;");
                }
            }
        }
        buf.append("</TD>");
        return buf.toString();
    }

    /**
     * Find all target addresses in code, so that they can be marked with &lt;A NAME = ...&gt;. Target addresses are kept in
     * an BitSet object.
     */
    private void findGotos(final ByteSequence bytes, final Code code) throws IOException {
        int index;
        gotoSet = new BitSet(bytes.available());
        int opcode;
        /*
         * First get Code attribute from method and the exceptions handled (try .. catch) in this method. We only need the line
         * number here.
         */
        if (code != null) {
            final CodeException[] ce = code.getExceptionTable();
            for (final CodeException cex : ce) {
                gotoSet.set(cex.getStartPC());
                gotoSet.set(cex.getEndPC());
                gotoSet.set(cex.getHandlerPC());
            }
            // Look for local variables and their range
            final Attribute[] attributes = code.getAttributes();
            for (final Attribute attribute : attributes) {
                if (attribute.getTag() == Const.ATTR_LOCAL_VARIABLE_TABLE) {
                    ((LocalVariableTable) attribute).forEach(var -> {
                        final int start = var.getStartPC();
                        gotoSet.set(start);
                        gotoSet.set(start + var.getLength());
                    });
                    break;
                }
            }
        }
        // Get target addresses from GOTO, JSR, TABLESWITCH, etc.
        while (bytes.available() > 0) {
            opcode = bytes.readUnsignedByte();
            // System.out.println(getOpcodeName(opcode));
            switch (opcode) {
            case Const.TABLESWITCH:
            case Const.LOOKUPSWITCH:
                // bytes.readByte(); // Skip already read byte
                final int remainder = bytes.getIndex() % 4;
                final int noPadBytes = remainder == 0 ? 0 : 4 - remainder;
                int defaultOffset;
                int offset;
                for (int j = 0; j < noPadBytes; j++) {
                    bytes.readByte();
                }
                // Both cases have a field default_offset in common
                defaultOffset = bytes.readInt();
                if (opcode == Const.TABLESWITCH) {
                    final int low = bytes.readInt();
                    final int high = bytes.readInt();
                    offset = bytes.getIndex() - 12 - noPadBytes - 1;
                    defaultOffset += offset;
                    gotoSet.set(defaultOffset);
                    for (int j = 0; j < high - low + 1; j++) {
                        index = offset + bytes.readInt();
                        gotoSet.set(index);
                    }
                } else { // LOOKUPSWITCH
                    final int npairs = bytes.readInt();
                    offset = bytes.getIndex() - 8 - noPadBytes - 1;
                    defaultOffset += offset;
                    gotoSet.set(defaultOffset);
                    for (int j = 0; j < npairs; j++) {
//                            int match = bytes.readInt();
                        bytes.readInt();
                        index = offset + bytes.readInt();
                        gotoSet.set(index);
                    }
                }
                break;
            case Const.GOTO:
            case Const.IFEQ:
            case Const.IFGE:
            case Const.IFGT:
            case Const.IFLE:
            case Const.IFLT:
            case Const.IFNE:
            case Const.IFNONNULL:
            case Const.IFNULL:
            case Const.IF_ACMPEQ:
            case Const.IF_ACMPNE:
            case Const.IF_ICMPEQ:
            case Const.IF_ICMPGE:
            case Const.IF_ICMPGT:
            case Const.IF_ICMPLE:
            case Const.IF_ICMPLT:
            case Const.IF_ICMPNE:
            case Const.JSR:
                // bytes.readByte(); // Skip already read byte
                index = bytes.getIndex() + bytes.readShort() - 1;
                gotoSet.set(index);
                break;
            case Const.GOTO_W:
            case Const.JSR_W:
                // bytes.readByte(); // Skip already read byte
                index = bytes.getIndex() + bytes.readInt() - 1;
                gotoSet.set(index);
                break;
            default:
                bytes.unreadByte();
                codeToHTML(bytes, 0); // Ignore output
            }
        }
    }

    /**
     * Write a single method with the byte code associated with it.
     */
    private void writeMethod(final Method method, final int methodNumber) throws IOException {
        // Get raw signature
        final String signature = method.getSignature();
        // Get array of strings containing the argument types
        final String[] args = Utility.methodSignatureArgumentTypes(signature, false);
        // Get return type string
        final String type = Utility.methodSignatureReturnType(signature, false);
        // Get method name
        final String name = method.getName();
        final String htmlName = Class2HTML.toHTML(name);
        // Get method's access flags
        String access = Utility.accessToString(method.getAccessFlags());
        access = Utility.replace(access, " ", "&nbsp;");
        // Get the method's attributes, the Code Attribute in particular
        final Attribute[] attributes = method.getAttributes();
        printWriter.print("<P><B><FONT COLOR=\"#FF0000\">" + access + "</FONT>&nbsp;" + "<A NAME=method" + methodNumber + ">" + Class2HTML.referenceType(type)
            + "</A>&nbsp<A HREF=\"" + className + "_methods.html#method" + methodNumber + "\" TARGET=Methods>" + htmlName + "</A>(");
        for (int i = 0; i < args.length; i++) {
            printWriter.print(Class2HTML.referenceType(args[i]));
            if (i < args.length - 1) {
                printWriter.print(",&nbsp;");
            }
        }
        printWriter.println(")</B></P>");
        Code c = null;
        byte[] code = null;
        if (attributes.length > 0) {
            printWriter.print("<H4>Attributes</H4><UL>\n");
            for (int i = 0; i < attributes.length; i++) {
                byte tag = attributes[i].getTag();
                if (tag != Const.ATTR_UNKNOWN) {
                    printWriter.print("<LI><A HREF=\"" + className + "_attributes.html#method" + methodNumber + "@" + i + "\" TARGET=Attributes>"
                        + Const.getAttributeName(tag) + "</A></LI>\n");
                } else {
                    printWriter.print("<LI>" + attributes[i] + "</LI>");
                }
                if (tag == Const.ATTR_CODE) {
                    c = (Code) attributes[i];
                    final Attribute[] attributes2 = c.getAttributes();
                    code = c.getCode();
                    printWriter.print("<UL>");
                    for (int j = 0; j < attributes2.length; j++) {
                        tag = attributes2[j].getTag();
                        printWriter.print("<LI><A HREF=\"" + className + "_attributes.html#" + "method" + methodNumber + "@" + i + "@" + j
                            + "\" TARGET=Attributes>" + Const.getAttributeName(tag) + "</A></LI>\n");
                    }
                    printWriter.print("</UL>");
                }
            }
            printWriter.println("</UL>");
        }
        if (code != null) { // No code, an abstract method, e.g.
            // System.out.println(name + "\n" + Utility.codeToString(code, constantPool, 0, -1));
            // Print the byte code
            try (ByteSequence stream = new ByteSequence(code)) {
                stream.mark(stream.available());
                findGotos(stream, c);
                stream.reset();
                printWriter.println("<TABLE BORDER=0><TR><TH ALIGN=LEFT>Byte<BR>offset</TH>" + "<TH ALIGN=LEFT>Instruction</TH><TH ALIGN=LEFT>Argument</TH>");
                while (stream.available() > 0) {
                    final int offset = stream.getIndex();
                    final String str = codeToHTML(stream, methodNumber);
                    String anchor = "";
                    /*
                     * Set an anchor mark if this line is targetted by a goto, jsr, etc. Defining an anchor for every line is very
                     * inefficient!
                     */
                    if (gotoSet.get(offset)) {
                        anchor = "<A NAME=code" + methodNumber + "@" + offset + "></A>";
                    }
                    String anchor2;
                    if (stream.getIndex() == code.length) {
                        anchor2 = "<A NAME=code" + methodNumber + "@" + code.length + ">" + offset + "</A>";
                    } else {
                        anchor2 = "" + offset;
                    }
                    printWriter.println("<TR VALIGN=TOP><TD>" + anchor2 + "</TD><TD>" + anchor + str + "</TR>");
                }
            }
            // Mark last line, may be targetted from Attributes window
            printWriter.println("<TR><TD> </A></TD></TR>");
            printWriter.println("</TABLE>");
        }
    }
}
