/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.tools.jcore;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class ClassWriter implements /* imports */ ClassConstants
{
    public static final boolean DEBUG = false;

    protected void debugMessage(String message) {
        System.out.println(message);
    }

    protected InstanceKlass     klass;
    protected DataOutputStream  dos;
    protected ConstantPool      cpool;

    // Map between class name to index of type CONSTANT_Class
    protected Map<String, Short> classToIndex = new HashMap<String, Short>();

    // Map between any modified UTF-8 and it's constant pool index.
    protected Map<String, Short> utf8ToIndex = new HashMap<String, Short>();

    // constant pool index for attribute names.

    protected short  _sourceFileIndex;
    protected short  _innerClassesIndex;
    protected short  _nestHostIndex;
    protected short  _nestMembersIndex;
    protected short  _syntheticIndex;
    protected short  _deprecatedIndex;
    protected short  _constantValueIndex;
    protected short  _codeIndex;
    protected short  _exceptionsIndex;
    protected short  _stackMapTableIndex;
    protected short  _lineNumberTableIndex;
    protected short  _localVariableTableIndex;
    protected short  _signatureIndex;
    protected short  _bootstrapMethodsIndex;

    protected static int extractHighShortFromInt(int val) {
        // must stay in sync with ConstantPool::name_and_type_at_put, method_at_put, etc.
        return (val >> 16) & 0xFFFF;
    }

    protected static int extractLowShortFromInt(int val) {
        // must stay in sync with ConstantPool::name_and_type_at_put, method_at_put, etc.
        return val & 0xFFFF;
    }

    public ClassWriter(InstanceKlass kls, OutputStream os) {
        klass = kls;
        dos = new DataOutputStream(os);
        cpool = klass.getConstants();
    }

    public void write() throws IOException {
        if (DEBUG) debugMessage("class name = " + klass.getName().asString());

        // write magic
        dos.writeInt(0xCAFEBABE);

        writeVersion();
        writeConstantPool();
        writeClassAccessFlags();
        writeThisClass();
        writeSuperClass();
        writeInterfaces();
        writeFields();
        writeMethods();
        writeClassAttributes();

        // flush output
        dos.flush();
    }

    protected void writeVersion() throws IOException {
        dos.writeShort((short)klass.minorVersion());
        dos.writeShort((short)klass.majorVersion());
    }

    protected void writeIndex(int index) throws IOException {
        if (index == 0) throw new InternalError();
        dos.writeShort(index);
    }

    protected void writeConstantPool() throws IOException {
        final U1Array tags = cpool.getTags();
        final long len = tags.length();
        dos.writeShort((short) len);

        if (DEBUG) debugMessage("constant pool length = " + len);

        int ci = 0; // constant pool index

        // collect all modified UTF-8 Strings from Constant Pool

        for (ci = 1; ci < len; ci++) {
            int cpConstType = tags.at(ci);
            if(cpConstType == JVM_CONSTANT_Utf8) {
                Symbol sym = cpool.getSymbolAt(ci);
                utf8ToIndex.put(sym.asString(), (short) ci);
            }
            else if(cpConstType == JVM_CONSTANT_Long ||
                      cpConstType == JVM_CONSTANT_Double) {
                ci++;
            }
        }

        // remember index of attribute name modified UTF-8 strings

        // class attributes
        Short sourceFileIndex = utf8ToIndex.get("SourceFile");
        _sourceFileIndex = (sourceFileIndex != null)? sourceFileIndex.shortValue() : 0;
        if (DEBUG) debugMessage("SourceFile index = " + _sourceFileIndex);

        Short innerClassesIndex = utf8ToIndex.get("InnerClasses");
        _innerClassesIndex = (innerClassesIndex != null)? innerClassesIndex.shortValue() : 0;
        if (DEBUG) debugMessage("InnerClasses index = " + _innerClassesIndex);

        Short nestHostIndex = utf8ToIndex.get("NestHost");
        _nestHostIndex = (nestHostIndex != null)? nestHostIndex.shortValue() : 0;
        if (DEBUG) debugMessage("NestHost index = " + _nestHostIndex);

        Short nestMembersIndex = utf8ToIndex.get("NestMembers");
        _nestMembersIndex = (nestMembersIndex != null)? nestMembersIndex.shortValue() : 0;
        if (DEBUG) debugMessage("NestMembers index = " + _nestMembersIndex);

        Short bootstrapMethodsIndex = utf8ToIndex.get("BootstrapMethods");
        _bootstrapMethodsIndex = (bootstrapMethodsIndex != null) ? bootstrapMethodsIndex.shortValue() : 0;
        // field attributes
        Short constantValueIndex = utf8ToIndex.get("ConstantValue");
        _constantValueIndex = (constantValueIndex != null)?
                                          constantValueIndex.shortValue() : 0;
        if (DEBUG) debugMessage("ConstantValue index = " + _constantValueIndex);

        Short syntheticIndex = utf8ToIndex.get("Synthetic");
        _syntheticIndex = (syntheticIndex != null)? syntheticIndex.shortValue() : 0;
        if (DEBUG) debugMessage("Synthetic index = " + _syntheticIndex);

        Short deprecatedIndex = utf8ToIndex.get("Deprecated");
        _deprecatedIndex = (deprecatedIndex != null)? deprecatedIndex.shortValue() : 0;
        if (DEBUG) debugMessage("Deprecated index = " + _deprecatedIndex);

        // method attributes
        Short codeIndex = utf8ToIndex.get("Code");
        _codeIndex = (codeIndex != null)? codeIndex.shortValue() : 0;
        if (DEBUG) debugMessage("Code index = " + _codeIndex);

        Short exceptionsIndex = utf8ToIndex.get("Exceptions");
        _exceptionsIndex = (exceptionsIndex != null)? exceptionsIndex.shortValue() : 0;
        if (DEBUG) debugMessage("Exceptions index = " + _exceptionsIndex);

        // Short syntheticIndex = (Short) utf8ToIndex.get("Synthetic");
        // Short deprecatedIndex = (Short) utf8ToIndex.get("Deprecated");

        // Code attributes
        Short stackMapTableIndex = utf8ToIndex.get("StackMapTable");
        _stackMapTableIndex = (stackMapTableIndex != null) ?
                              stackMapTableIndex.shortValue() : 0;
        if (DEBUG) debugMessage("StackMapTable index = " + _stackMapTableIndex);

        Short lineNumberTableIndex = utf8ToIndex.get("LineNumberTable");
        _lineNumberTableIndex = (lineNumberTableIndex != null)?
                                       lineNumberTableIndex.shortValue() : 0;
        if (DEBUG) debugMessage("LineNumberTable index = " + _lineNumberTableIndex);

        Short localVariableTableIndex = utf8ToIndex.get("LocalVariableTable");
        _localVariableTableIndex = (localVariableTableIndex != null)?
                                       localVariableTableIndex.shortValue() : 0;
        if (DEBUG) debugMessage("LocalVariableTable index = " + _localVariableTableIndex);

        Short signatureIdx = utf8ToIndex.get("Signature");
        _signatureIndex = (signatureIdx != null)? signatureIdx.shortValue() : 0;
        if (DEBUG) debugMessage("Signature index = " + _signatureIndex);

        for(ci = 1; ci < len; ci++) {
            int cpConstType = tags.at(ci);
            // write cp_info
            // write constant type
            switch(cpConstType) {
                case JVM_CONSTANT_Utf8: {
                     dos.writeByte(cpConstType);
                     Symbol sym = cpool.getSymbolAt(ci);
                     dos.writeShort((short)sym.getLength());
                     dos.write(sym.asByteArray());
                     if (DEBUG) debugMessage("CP[" + ci + "] = modified UTF-8 " + sym.asString());
                     break;
                }

                case JVM_CONSTANT_Unicode:
                     throw new IllegalArgumentException("Unicode constant!");

                case JVM_CONSTANT_Integer:
                     dos.writeByte(cpConstType);
                     dos.writeInt(cpool.getIntAt(ci));
                     if (DEBUG) debugMessage("CP[" + ci + "] = int " + cpool.getIntAt(ci));
                     break;

                case JVM_CONSTANT_Float:
                     dos.writeByte(cpConstType);
                     dos.writeFloat(cpool.getFloatAt(ci));
                     if (DEBUG) debugMessage("CP[" + ci + "] = float " + cpool.getFloatAt(ci));
                     break;

                case JVM_CONSTANT_Long: {
                     dos.writeByte(cpConstType);
                     long l = cpool.getLongAt(ci);
                     // long entries occupy two pool entries
                     ci++;
                     dos.writeLong(l);
                     break;
                }

                case JVM_CONSTANT_Double:
                     dos.writeByte(cpConstType);
                     dos.writeDouble(cpool.getDoubleAt(ci));
                     // double entries occupy two pool entries
                     ci++;
                     break;

                case JVM_CONSTANT_Class:
                case JVM_CONSTANT_UnresolvedClass:
                case JVM_CONSTANT_UnresolvedClassInError: {
                     dos.writeByte(JVM_CONSTANT_Class);
                     String klassName = cpool.getKlassNameAt(ci).asString();
                     Short s = utf8ToIndex.get(klassName);
                     classToIndex.put(klassName, (short) ci);
                     dos.writeShort(s.shortValue());
                     if (DEBUG) debugMessage("CP[" + ci  + "] = class " + s);
                     break;
                }

                case JVM_CONSTANT_String: {
                     dos.writeByte(cpConstType);
                     String str = cpool.getUnresolvedStringAt(ci).asString();
                     Short s = utf8ToIndex.get(str);
                     dos.writeShort(s.shortValue());
                     if (DEBUG) debugMessage("CP[" + ci + "] = string " + s);
                     break;
                }

                // all external, internal method/field references
                case JVM_CONSTANT_Fieldref:
                case JVM_CONSTANT_Methodref:
                case JVM_CONSTANT_InterfaceMethodref: {
                     dos.writeByte(cpConstType);
                     int value = cpool.getIntAt(ci);
                     short klassIndex = (short) extractLowShortFromInt(value);
                     short nameAndTypeIndex = (short) extractHighShortFromInt(value);
                     dos.writeShort(klassIndex);
                     dos.writeShort(nameAndTypeIndex);
                     if (DEBUG) debugMessage("CP[" + ci + "] = ref klass = " +
                           klassIndex + ", N&T = " + nameAndTypeIndex);
                     break;
                }

                case JVM_CONSTANT_NameAndType: {
                     dos.writeByte(cpConstType);
                     int value = cpool.getIntAt(ci);
                     short nameIndex = (short) extractLowShortFromInt(value);
                     short signatureIndex = (short) extractHighShortFromInt(value);
                     dos.writeShort(nameIndex);
                     dos.writeShort(signatureIndex);
                     if (DEBUG) debugMessage("CP[" + ci + "] = N&T name = " + nameIndex
                                        + ", type = " + signatureIndex);
                     break;
                }

                case JVM_CONSTANT_MethodHandle: {
                     dos.writeByte(cpConstType);
                     int value = cpool.getIntAt(ci);
                     byte refKind = (byte) extractLowShortFromInt(value);
                     short memberIndex = (short) extractHighShortFromInt(value);
                     dos.writeByte(refKind);
                     dos.writeShort(memberIndex);
                     if (DEBUG) debugMessage("CP[" + ci + "] = MH kind = " +
                           refKind + ", mem = " + memberIndex);
                     break;
                }

                case JVM_CONSTANT_MethodType: {
                     dos.writeByte(cpConstType);
                     int value = cpool.getIntAt(ci);
                     short refIndex = (short) value;
                     dos.writeShort(refIndex);
                     if (DEBUG) debugMessage("CP[" + ci + "] = MT index = " + refIndex);
                     break;
                }

                case JVM_CONSTANT_Dynamic: {
                    dos.writeByte(cpConstType);
                    int value = cpool.getIntAt(ci);
                    short bsmIndex = (short) extractLowShortFromInt(value);
                    short nameAndTypeIndex = (short) extractHighShortFromInt(value);
                    dos.writeShort(bsmIndex);
                    dos.writeShort(nameAndTypeIndex);
                    if (DEBUG) debugMessage("CP[" + ci + "] = CONDY bsm = " +
                                            bsmIndex + ", N&T = " + nameAndTypeIndex);
                    break;
                }

                case JVM_CONSTANT_InvokeDynamic: {
                     dos.writeByte(cpConstType);
                     int value = cpool.getIntAt(ci);
                     short bsmIndex = (short) extractLowShortFromInt(value);
                     short nameAndTypeIndex = (short) extractHighShortFromInt(value);
                     dos.writeShort(bsmIndex);
                     dos.writeShort(nameAndTypeIndex);
                     if (DEBUG) debugMessage("CP[" + ci + "] = INDY bsm = " +
                           bsmIndex + ", N&T = " + nameAndTypeIndex);
                     break;
                }

                default:
                  throw new InternalError("Unknown tag: " + cpConstType);
            } // switch
        }
    }

    protected void writeClassAccessFlags() throws IOException {
        int flags = (int)(klass.getAccessFlags() & JVM_RECOGNIZED_CLASS_MODIFIERS);
        dos.writeShort((short)flags);
    }

    protected void writeThisClass() throws IOException {
        String klassName = klass.getName().asString();
        Short index = classToIndex.get(klassName);
        dos.writeShort(index.shortValue());
        if (DEBUG) debugMessage("this class = " + index);
    }

    protected void writeSuperClass() throws IOException {
        Klass superKlass = klass.getSuper();
        if (superKlass != null) { // is not java.lang.Object
            String superName = superKlass.getName().asString();
            Short index = classToIndex.get(superName);
            if (DEBUG) debugMessage("super class = " + index);
            dos.writeShort(index.shortValue());
        } else {
            dos.writeShort(0); // no super class
        }
    }
    protected void writeInterfaces() throws IOException {
        KlassArray interfaces = klass.getLocalInterfaces();
        final int len = interfaces.length();

        if (DEBUG) debugMessage("number of interfaces = " + len);

        // write interfaces count
        dos.writeShort((short) len);
        for (int i = 0; i < len; i++) {
           Klass k = interfaces.getAt(i);
           Short index = classToIndex.get(k.getName().asString());
           dos.writeShort(index.shortValue());
           if (DEBUG) debugMessage("\t" + index);
        }
    }

    protected void writeFields() throws IOException {
        final int javaFieldsCount = klass.getJavaFieldsCount();

        // write number of fields
        dos.writeShort((short) javaFieldsCount);

        if (DEBUG) debugMessage("number of fields = " + javaFieldsCount);

        for (int index = 0; index < javaFieldsCount; index++) {
            short accessFlags    = klass.getFieldAccessFlags(index);
            dos.writeShort(accessFlags & (short) JVM_RECOGNIZED_FIELD_MODIFIERS);

            int nameIndex = klass.getFieldNameIndex(index);
            dos.writeShort(nameIndex);

            int signatureIndex = klass.getFieldSignatureIndex(index);
            dos.writeShort(signatureIndex);
            if (DEBUG) debugMessage("\tfield name = " + nameIndex + ", signature = " + signatureIndex);

            short fieldAttributeCount = 0;
            boolean hasSyn = hasSyntheticAttribute(accessFlags);
            if (hasSyn)
                fieldAttributeCount++;

            int initvalIndex = klass.getFieldInitialValueIndex(index);
            if (initvalIndex != 0)
                fieldAttributeCount++;

            int genSigIndex = klass.getFieldGenericSignatureIndex(index);
            if (genSigIndex != 0)
                fieldAttributeCount++;

            U1Array fieldAnnotations = klass.getFieldAnnotations(index);
            if (fieldAnnotations != null) {
                fieldAttributeCount++;
            }

            U1Array fieldTypeAnnotations = klass.getFieldTypeAnnotations(index);
            if (fieldTypeAnnotations != null) {
                fieldAttributeCount++;
            }

            dos.writeShort(fieldAttributeCount);

            // write synthetic, if applicable
            if (hasSyn)
                writeSynthetic();

            if (initvalIndex != 0) {
                writeIndex(_constantValueIndex);
                dos.writeInt(2);
                dos.writeShort(initvalIndex);
                if (DEBUG) debugMessage("\tfield init value = " + initvalIndex);
            }

            if (genSigIndex != 0) {
                writeIndex(_signatureIndex);
                dos.writeInt(2);
                dos.writeShort(genSigIndex);
                if (DEBUG) debugMessage("\tfield generic signature index " + genSigIndex);
            }

            if (fieldAnnotations != null) {
                writeAnnotationAttribute("RuntimeVisibleAnnotations", fieldAnnotations);
            }

            if (fieldTypeAnnotations != null) {
                writeAnnotationAttribute("RuntimeVisibleTypeAnnotations", fieldTypeAnnotations);
            }
        }
    }

    protected boolean isSynthetic(short accessFlags) {
        return (accessFlags & (short) JVM_ACC_SYNTHETIC) != 0;
    }

    protected boolean hasSyntheticAttribute(short accessFlags) {
        // Check if flags have the attribute and if the constant pool contains an entry for it.
        return isSynthetic(accessFlags) && _syntheticIndex != 0;
    }

    protected void writeSynthetic() throws IOException {
        writeIndex(_syntheticIndex);
        dos.writeInt(0);
    }

    protected void writeMethods() throws IOException {
        MethodArray methods = klass.getMethods();
        ArrayList<Method> valid_methods = new ArrayList<Method>();
        for (int i = 0; i < methods.length(); i++) {
            Method m = methods.at(i);
            long accessFlags = m.getAccessFlags() & JVM_RECOGNIZED_METHOD_MODIFIERS;
            // skip overpass methods
            if (accessFlags == (JVM_ACC_PUBLIC | JVM_ACC_SYNTHETIC | JVM_ACC_BRIDGE)) {
                continue;
            }
            valid_methods.add(m);
        }
        final int len = valid_methods.size();
        // write number of methods
        dos.writeShort((short) len);
        if (DEBUG) debugMessage("number of methods = " + len);
        for (int m = 0; m < len; m++) {
            writeMethod(valid_methods.get(m));
        }
    }

    protected void writeMethod(Method m) throws IOException {
        long accessFlags = m.getAccessFlags();
        dos.writeShort((short) (accessFlags & JVM_RECOGNIZED_METHOD_MODIFIERS));
        dos.writeShort((short) m.getNameIndex());
        dos.writeShort((short) m.getSignatureIndex());
        if (DEBUG) debugMessage("\tmethod name = " + m.getNameIndex() + ", signature = "
                        + m.getSignatureIndex());

        final boolean isNative = ((accessFlags & JVM_ACC_NATIVE) != 0);
        final boolean isAbstract = ((accessFlags & JVM_ACC_ABSTRACT) != 0);

        short methodAttributeCount = 0;

        final boolean hasSyn = hasSyntheticAttribute((short)accessFlags);
        if (hasSyn)
            methodAttributeCount++;

        final boolean hasCheckedExceptions = m.hasCheckedExceptions();
        if (hasCheckedExceptions)
            methodAttributeCount++;

        final boolean isCodeAvailable = (!isNative) && (!isAbstract);
        if (isCodeAvailable)
            methodAttributeCount++;

        final boolean isGeneric = (m.getGenericSignature() != null);
        if (isGeneric)
            methodAttributeCount++;

        final U1Array annotations = m.getAnnotations();
        if (annotations != null) {
            methodAttributeCount++;
        }

        final U1Array parameterAnnotations = m.getParameterAnnotations();
        if (parameterAnnotations != null) {
            methodAttributeCount++;
        }

        final U1Array typeAnnotations = m.getTypeAnnotations();
        if (typeAnnotations != null) {
            methodAttributeCount++;
        }

        final U1Array annotationDefault = m.getAnnotationDefault();
        if (annotationDefault != null) {
            methodAttributeCount++;
        }

        dos.writeShort(methodAttributeCount);
        if (DEBUG) debugMessage("\tmethod attribute count = " + methodAttributeCount);

        if (hasSyn) {
            if (DEBUG) debugMessage("\tmethod is synthetic");
            writeSynthetic();
        }

        if (isCodeAvailable) {
            byte[] code = m.getByteCode();
            short codeAttrCount = 0;
            int codeSize  = 2           /* max_stack   */ +
                            2           /* max_locals  */ +
                            4           /* code_length */ +
                            code.length /* code        */ +
                            2           /* exp. table len.  */ +
                            2           /* code attr. count */;

            boolean hasExceptionTable = m.hasExceptionTable();
            ExceptionTableElement[] exceptionTable = null;
            int exceptionTableLen = 0;
            if (hasExceptionTable) {
                exceptionTable = m.getExceptionTable();
                exceptionTableLen = exceptionTable.length;
                if (DEBUG) debugMessage("\tmethod has exception table");
                codeSize += exceptionTableLen /* exception table is 4-tuple array */
                                         * (2 /* start_pc     */ +
                                            2 /* end_pc       */ +
                                            2 /* handler_pc   */ +
                                            2 /* catch_type   */);
            }

            boolean hasStackMapTable = m.hasStackMapTable();
            U1Array stackMapData = null;
            int stackMapAttrLen = 0;

            if (hasStackMapTable) {
                if (DEBUG) debugMessage("\tmethod has stack map table");
                stackMapData = m.getStackMapData();
                if (DEBUG) debugMessage("\t\tstack map table length = " + stackMapData.length());

                stackMapAttrLen = stackMapData.length();

                codeSize += 2 /* stack map table attr index */ +
                            4 /* stack map table attr length */ +
                            stackMapAttrLen;

                if (DEBUG) debugMessage("\t\tstack map table attr size = " +
                                        stackMapAttrLen);

                codeAttrCount++;
            }

            boolean hasLineNumberTable = m.hasLineNumberTable();
            LineNumberTableElement[] lineNumberTable = null;
            int lineNumberAttrLen = 0;

            if (hasLineNumberTable) {
                if (DEBUG) debugMessage("\tmethod has line number table");
                lineNumberTable = m.getLineNumberTable();
                if (DEBUG) debugMessage("\t\tline table length = " + lineNumberTable.length);

                lineNumberAttrLen = 2 /* line number table length         */ +
                           lineNumberTable.length * (2 /* start_pc */ + 2 /* line_number */);

                codeSize += 2 /* line number table attr index     */ +
                            4 /* line number table attr length    */ +
                            lineNumberAttrLen;

                if (DEBUG) debugMessage("\t\tline number table attr size = " +
                                              lineNumberAttrLen);

                codeAttrCount++;
            }

            boolean hasLocalVariableTable = m.hasLocalVariableTable();
            LocalVariableTableElement[] localVariableTable = null;
            int localVarAttrLen = 0;

            if (hasLocalVariableTable) {
                if (DEBUG) debugMessage("\tmethod has local variable table");
                localVariableTable = m.getLocalVariableTable();
                if (DEBUG) debugMessage("\t\tlocal variable table length = "
                              + localVariableTable.length);
                localVarAttrLen =
                               2 /* local variable table length      */ +
                               localVariableTable.length * ( 2 /* start_pc          */ +
                                                          2 /* length            */ +
                                                          2 /* name_index        */ +
                                                          2 /* signature_index   */ +
                                                          2 /* variable index    */ );

                if (DEBUG) debugMessage("\t\tlocal variable attr size = " +
                                              localVarAttrLen);

                codeSize += 2 /* local variable table attr index  */ +
                            4 /* local variable table attr length */ +
                            localVarAttrLen;

                codeAttrCount++;
            }

            // fix ConstantPoolCache indices to ConstantPool indices.
            rewriteByteCode(m, code);

            // start writing Code

            writeIndex(_codeIndex);

            dos.writeInt(codeSize);
            if (DEBUG) debugMessage("\tcode attribute length = " + codeSize);

            dos.writeShort((short) m.getMaxStack());
            if (DEBUG) debugMessage("\tmax stack = " + m.getMaxStack());

            dos.writeShort((short) m.getMaxLocals());
            if (DEBUG) debugMessage("\tmax locals = " + m.getMaxLocals());

            dos.writeInt(code.length);
            if (DEBUG) debugMessage("\tcode size = " + code.length);

            dos.write(code);

            // write exception table size
            dos.writeShort((short) exceptionTableLen);
            if (DEBUG) debugMessage("\texception table length = " + exceptionTableLen);

            if (exceptionTableLen != 0) {
                for (int e = 0; e < exceptionTableLen; e++) {
                     dos.writeShort((short) exceptionTable[e].getStartPC());
                     dos.writeShort((short) exceptionTable[e].getEndPC());
                     dos.writeShort((short) exceptionTable[e].getHandlerPC());
                     dos.writeShort((short) exceptionTable[e].getCatchTypeIndex());
                }
            }

            dos.writeShort(codeAttrCount);
            if (DEBUG) debugMessage("\tcode attribute count = " + codeAttrCount);

            // write StackMapTable, if available
            if (hasStackMapTable) {
                writeIndex(_stackMapTableIndex);
                dos.writeInt(stackMapAttrLen);
                // We write bytes directly as stackMapData is
                // raw data (#entries + entries)
                for (int i = 0; i < stackMapData.length(); i++) {
                    dos.writeByte(stackMapData.at(i));
                }
            }

            // write LineNumberTable, if available.
            if (hasLineNumberTable) {
                writeIndex(_lineNumberTableIndex);
                dos.writeInt(lineNumberAttrLen);
                dos.writeShort((short) lineNumberTable.length);
                for (int l = 0; l < lineNumberTable.length; l++) {
                     dos.writeShort((short) lineNumberTable[l].getStartBCI());
                     dos.writeShort((short) lineNumberTable[l].getLineNumber());
                }
            }

            // write LocalVariableTable, if available.
            if (hasLocalVariableTable) {
                writeIndex(_localVariableTableIndex);
                dos.writeInt(localVarAttrLen);
                dos.writeShort((short) localVariableTable.length);
                for (int l = 0; l < localVariableTable.length; l++) {
                     dos.writeShort((short) localVariableTable[l].getStartBCI());
                     dos.writeShort((short) localVariableTable[l].getLength());
                     dos.writeShort((short) localVariableTable[l].getNameCPIndex());
                     dos.writeShort((short) localVariableTable[l].getDescriptorCPIndex());
                     dos.writeShort((short) localVariableTable[l].getSlot());
                }
            }
        }

        if (hasCheckedExceptions) {
            CheckedExceptionElement[] exceptions = m.getCheckedExceptions();
            writeIndex(_exceptionsIndex);

            int attrSize = 2 /* number_of_exceptions */ +
                           exceptions.length * 2 /* exception_index */;
            dos.writeInt(attrSize);
            dos.writeShort(exceptions.length);
            if (DEBUG) debugMessage("\tmethod has " + exceptions.length
                                        +  " checked exception(s)");
            for (int e = 0; e < exceptions.length; e++) {
                 short cpIndex = (short) exceptions[e].getClassCPIndex();
                 dos.writeShort(cpIndex);
            }
        }

        if (isGeneric) {
           writeGenericSignature(m.getGenericSignature().asString());
        }

        if (annotationDefault != null) {
           writeAnnotationAttribute("AnnotationDefault", annotationDefault);
        }

        if (annotations != null) {
           writeAnnotationAttribute("RuntimeVisibleAnnotations", annotations);
        }

        if (parameterAnnotations != null) {
           writeAnnotationAttribute("RuntimeVisibleParameterAnnotations", parameterAnnotations);
        }

        if (typeAnnotations != null) {
           writeAnnotationAttribute("RuntimeVisibleTypeAnnotations", typeAnnotations);
        }
    }

    protected void rewriteByteCode(Method m, byte[] code) {
        ByteCodeRewriter r = new ByteCodeRewriter(m, cpool, code);
        r.rewrite();
    }

    protected void writeGenericSignature(String signature) throws IOException {
        writeIndex(_signatureIndex);
        if (DEBUG) debugMessage("signature attribute = " + _signatureIndex);
        dos.writeInt(2);
        Short index = utf8ToIndex.get(signature);
        dos.writeShort(index.shortValue());
        if (DEBUG) debugMessage("generic signature = " + index);
    }

    protected void writeClassAttributes() throws IOException {
        final long flags = klass.getAccessFlags();
        final boolean hasSyn = hasSyntheticAttribute((short) flags);

        // check for source file
        short classAttributeCount = 0;

        if (hasSyn)
            classAttributeCount++;

        Symbol sourceFileName = klass.getSourceFileName();
        if (sourceFileName != null)
            classAttributeCount++;

        Symbol genericSignature = klass.getGenericSignature();
        if (genericSignature != null)
            classAttributeCount++;

        U2Array innerClasses = klass.getInnerClasses();
        final int numInnerClasses = innerClasses.length() / 4;
        if (numInnerClasses != 0)
            classAttributeCount++;

        short nestHost = klass.getNestHostIndex();
        if (nestHost != 0) {
            classAttributeCount++;
        }

        U2Array nestMembers = klass.getNestMembers();
        final int numNestMembers = nestMembers.length();
        if (numNestMembers != 0) {
            classAttributeCount++;
        }

        int bsmCount = klass.getConstants().getBootstrapMethodsCount();
        if (bsmCount != 0) {
            classAttributeCount++;
        }

        U1Array classAnnotations = klass.getClassAnnotations();
        if (classAnnotations != null) {
            classAttributeCount++;
        }

        U1Array classTypeAnnotations = klass.getClassTypeAnnotations();
        if (classTypeAnnotations != null) {
            classAttributeCount++;
        }

        dos.writeShort(classAttributeCount);
        if (DEBUG) debugMessage("class attribute count = " + classAttributeCount);

        if (hasSyn)
            writeSynthetic();

        // write SourceFile, if any
        if (sourceFileName != null) {
            writeIndex(_sourceFileIndex);
            if (DEBUG) debugMessage("source file attribute = " + _sourceFileIndex);
            dos.writeInt(2);
            Short index = utf8ToIndex.get(sourceFileName.asString());
            dos.writeShort(index.shortValue());
            if (DEBUG) debugMessage("source file name = " + index);
        }

        // write Signature, if any
        if (genericSignature != null) {
            writeGenericSignature(genericSignature.asString());
        }

        // write inner classes, if any
        if (numInnerClasses != 0) {
            writeIndex(_innerClassesIndex);
            final int innerAttrLen = 2 /* number_of_inner_classes */ +
                                     numInnerClasses * (
                                                 2 /* inner_class_info_index */ +
                                                 2 /* outer_class_info_index */ +
                                                 2 /* inner_class_name_index */ +
                                                 2 /* inner_class_access_flags */);
            dos.writeInt(innerAttrLen);

            dos.writeShort(numInnerClasses);
            if (DEBUG) debugMessage("class has " + numInnerClasses + " inner class entries");

            for (int index = 0; index < numInnerClasses * 4; index++) {
                dos.writeShort(innerClasses.at(index));
            }
        }

        if (nestHost != 0) {
            writeIndex(_nestHostIndex);
            final int nestHostAttrLen = 2;
            dos.writeInt(nestHostAttrLen);
            dos.writeShort(nestHost);
        }

        if (numNestMembers != 0) {
           writeIndex(_nestMembersIndex);
           final int nestMembersAttrLen = 2 + numNestMembers * 2;
           dos.writeInt(nestMembersAttrLen);
           dos.writeShort(numNestMembers);
           for (int index = 0; index < numNestMembers; index++) {
               dos.writeShort(nestMembers.at(index));
           }
        }

        // write bootstrap method attribute, if any
        if (bsmCount != 0) {
            ConstantPool cpool = klass.getConstants();
            writeIndex(_bootstrapMethodsIndex);
            if (DEBUG) debugMessage("bootstrap methods attribute = " + _bootstrapMethodsIndex);
            int attrLen = 2; // num_bootstrap_methods
            for (int index = 0; index < bsmCount; index++) {
                int bsmArgsCount = cpool.getBootstrapMethodArgsCount(index);
                attrLen += 2 // bootstrap_method_ref
                           + 2 // num_bootstrap_arguments
                           + bsmArgsCount * 2;
            }
            dos.writeInt(attrLen);
            dos.writeShort(bsmCount);
            for (int index = 0; index < bsmCount; index++) {
                short value[] = cpool.getBootstrapMethodAt(index);
                for (int i = 0; i < value.length; i++) {
                    dos.writeShort(value[i]);
                }
            }
        }

        if (classAnnotations != null) {
           writeAnnotationAttribute("RuntimeVisibleAnnotations", classAnnotations);
        }

        if (classTypeAnnotations != null) {
           writeAnnotationAttribute("RuntimeVisibleTypeAnnotations", classTypeAnnotations);
        }
    }

    protected void writeAnnotationAttribute(String annotationName, U1Array annotation) throws IOException {
      int length = annotation.length();
      Short annotationNameIndex = utf8ToIndex.get(annotationName);
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(annotationNameIndex != null, "should not be null");
      }
      writeIndex(annotationNameIndex.shortValue());
      dos.writeInt(length);
      for (int index = 0; index < length; index++) {
        dos.writeByte(annotation.at(index));
      }
    }
}
