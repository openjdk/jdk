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

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Constant;
import com.sun.org.apache.bcel.internal.classfile.ConstantClass;
import com.sun.org.apache.bcel.internal.classfile.ConstantFieldref;
import com.sun.org.apache.bcel.internal.classfile.ConstantInterfaceMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantNameAndType;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.ConstantString;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Convert constant pool into HTML file.
 */
final class ConstantHTML {

    private final String className; // name of current class
    private final String classPackage; // name of package
    private final ConstantPool constantPool; // reference to constant pool
    private final PrintWriter printWriter; // file to write to
    private final String[] constantRef; // String to return for cp[i]
    private final Constant[] constants; // The constants in the cp
    private final Method[] methods;

    ConstantHTML(final String dir, final String className, final String classPackage, final Method[] methods, final ConstantPool constantPool,
        final Charset charset) throws FileNotFoundException, UnsupportedEncodingException {
        this.className = className;
        this.classPackage = classPackage;
        this.constantPool = constantPool;
        this.methods = methods;
        this.constants = constantPool.getConstantPool();
        try (PrintWriter newPrintWriter = new PrintWriter(dir + className + "_cp.html", charset.name())) {
            printWriter = newPrintWriter;
            constantRef = new String[constants.length];
            constantRef[0] = "&lt;unknown&gt;";
            printWriter.print("<HTML><head><meta charset=\"");
            printWriter.print(charset.name());
            printWriter.println("\"></head>");
            printWriter.println("<BODY BGCOLOR=\"#C0C0C0\"><TABLE BORDER=0>");
            // Loop through constants, constants[0] is reserved
            for (int i = 1; i < constants.length; i++) {
                if (i % 2 == 0) {
                    printWriter.print("<TR BGCOLOR=\"#C0C0C0\"><TD>");
                } else {
                    printWriter.print("<TR BGCOLOR=\"#A0A0A0\"><TD>");
                }
                if (constants[i] != null) {
                    writeConstant(i);
                }
                printWriter.print("</TD></TR>\n");
            }
            printWriter.println("</TABLE></BODY></HTML>");
        }
    }

    private int getMethodNumber(final String str) {
        for (int i = 0; i < methods.length; i++) {
            final String cmp = methods[i].getName() + methods[i].getSignature();
            if (cmp.equals(str)) {
                return i;
            }
        }
        return -1;
    }

    String referenceConstant(final int index) {
        return constantRef[index];
    }

    private void writeConstant(final int index) {
        final byte tag = constants[index].getTag();
        int classIndex;
        int nameIndex;
        String ref;
        // The header is always the same
        printWriter.println("<H4> <A NAME=cp" + index + ">" + index + "</A> " + Const.getConstantName(tag) + "</H4>");
        /*
         * For every constant type get the needed parameters and print them appropriately
         */
        switch (tag) {
        case Const.CONSTANT_InterfaceMethodref:
        case Const.CONSTANT_Methodref:
            // Get class_index and name_and_type_index, depending on type
            if (tag == Const.CONSTANT_Methodref) {
                final ConstantMethodref c = constantPool.getConstant(index, Const.CONSTANT_Methodref, ConstantMethodref.class);
                classIndex = c.getClassIndex();
                nameIndex = c.getNameAndTypeIndex();
            } else {
                final ConstantInterfaceMethodref c1 = constantPool.getConstant(index, Const.CONSTANT_InterfaceMethodref, ConstantInterfaceMethodref.class);
                classIndex = c1.getClassIndex();
                nameIndex = c1.getNameAndTypeIndex();
            }
            // Get method name and its class
            final String methodName = constantPool.constantToString(nameIndex, Const.CONSTANT_NameAndType);
            final String htmlMethodName = Class2HTML.toHTML(methodName);
            // Partially compacted class name, i.e., / -> .
            final String methodClass = constantPool.constantToString(classIndex, Const.CONSTANT_Class);
            String shortMethodClass = Utility.compactClassName(methodClass); // I.e., remove java.lang.
            shortMethodClass = Utility.compactClassName(shortMethodClass, classPackage + ".", true); // Remove class package prefix
            // Get method signature
            final ConstantNameAndType c2 = constantPool.getConstant(nameIndex, Const.CONSTANT_NameAndType, ConstantNameAndType.class);
            final String signature = constantPool.constantToString(c2.getSignatureIndex(), Const.CONSTANT_Utf8);
            // Get array of strings containing the argument types
            final String[] args = Utility.methodSignatureArgumentTypes(signature, false);
            // Get return type string
            final String type = Utility.methodSignatureReturnType(signature, false);
            final String retType = Class2HTML.referenceType(type);
            final StringBuilder buf = new StringBuilder("(");
            for (int i = 0; i < args.length; i++) {
                buf.append(Class2HTML.referenceType(args[i]));
                if (i < args.length - 1) {
                    buf.append(",&nbsp;");
                }
            }
            buf.append(")");
            final String argTypes = buf.toString();
            if (methodClass.equals(className)) {
                ref = "<A HREF=\"" + className + "_code.html#method" + getMethodNumber(methodName + signature) + "\" TARGET=Code>" + htmlMethodName + "</A>";
            } else {
                ref = "<A HREF=\"" + methodClass + ".html" + "\" TARGET=_top>" + shortMethodClass + "</A>." + htmlMethodName;
            }
            constantRef[index] = retType + "&nbsp;<A HREF=\"" + className + "_cp.html#cp" + classIndex + "\" TARGET=Constants>" + shortMethodClass
                + "</A>.<A HREF=\"" + className + "_cp.html#cp" + index + "\" TARGET=ConstantPool>" + htmlMethodName + "</A>&nbsp;" + argTypes;
            printWriter.println("<P><TT>" + retType + "&nbsp;" + ref + argTypes + "&nbsp;</TT>\n<UL>" + "<LI><A HREF=\"#cp" + classIndex + "\">Class index("
                + classIndex + ")</A>\n" + "<LI><A HREF=\"#cp" + nameIndex + "\">NameAndType index(" + nameIndex + ")</A></UL>");
            break;
        case Const.CONSTANT_Fieldref:
            // Get class_index and name_and_type_index
            final ConstantFieldref c3 = constantPool.getConstant(index, Const.CONSTANT_Fieldref, ConstantFieldref.class);
            classIndex = c3.getClassIndex();
            nameIndex = c3.getNameAndTypeIndex();
            // Get method name and its class (compacted)
            final String fieldClass = constantPool.constantToString(classIndex, Const.CONSTANT_Class);
            String shortFieldClass = Utility.compactClassName(fieldClass); // I.e., remove java.lang.
            shortFieldClass = Utility.compactClassName(shortFieldClass, classPackage + ".", true); // Remove class package prefix
            final String fieldName = constantPool.constantToString(nameIndex, Const.CONSTANT_NameAndType);
            if (fieldClass.equals(className)) {
                ref = "<A HREF=\"" + fieldClass + "_methods.html#field" + fieldName + "\" TARGET=Methods>" + fieldName + "</A>";
            } else {
                ref = "<A HREF=\"" + fieldClass + ".html\" TARGET=_top>" + shortFieldClass + "</A>." + fieldName + "\n";
            }
            constantRef[index] = "<A HREF=\"" + className + "_cp.html#cp" + classIndex + "\" TARGET=Constants>" + shortFieldClass + "</A>.<A HREF=\""
                + className + "_cp.html#cp" + index + "\" TARGET=ConstantPool>" + fieldName + "</A>";
            printWriter.println("<P><TT>" + ref + "</TT><BR>\n" + "<UL>" + "<LI><A HREF=\"#cp" + classIndex + "\">Class(" + classIndex + ")</A><BR>\n"
                + "<LI><A HREF=\"#cp" + nameIndex + "\">NameAndType(" + nameIndex + ")</A></UL>");
            break;
        case Const.CONSTANT_Class:
            final ConstantClass c4 = constantPool.getConstant(index, Const.CONSTANT_Class, ConstantClass.class);
            nameIndex = c4.getNameIndex();
            final String className2 = constantPool.constantToString(index, tag); // / -> .
            String shortClassName = Utility.compactClassName(className2); // I.e., remove java.lang.
            shortClassName = Utility.compactClassName(shortClassName, classPackage + ".", true); // Remove class package prefix
            ref = "<A HREF=\"" + className2 + ".html\" TARGET=_top>" + shortClassName + "</A>";
            constantRef[index] = "<A HREF=\"" + className + "_cp.html#cp" + index + "\" TARGET=ConstantPool>" + shortClassName + "</A>";
            printWriter.println("<P><TT>" + ref + "</TT><UL>" + "<LI><A HREF=\"#cp" + nameIndex + "\">Name index(" + nameIndex + ")</A></UL>\n");
            break;
        case Const.CONSTANT_String:
            final ConstantString c5 = constantPool.getConstant(index, Const.CONSTANT_String, ConstantString.class);
            nameIndex = c5.getStringIndex();
            final String str = Class2HTML.toHTML(constantPool.constantToString(index, tag));
            printWriter.println("<P><TT>" + str + "</TT><UL>" + "<LI><A HREF=\"#cp" + nameIndex + "\">Name index(" + nameIndex + ")</A></UL>\n");
            break;
        case Const.CONSTANT_NameAndType:
            final ConstantNameAndType c6 = constantPool.getConstant(index, Const.CONSTANT_NameAndType, ConstantNameAndType.class);
            nameIndex = c6.getNameIndex();
            final int signatureIndex = c6.getSignatureIndex();
            printWriter.println("<P><TT>" + Class2HTML.toHTML(constantPool.constantToString(index, tag)) + "</TT><UL>" + "<LI><A HREF=\"#cp" + nameIndex
                + "\">Name index(" + nameIndex + ")</A>\n" + "<LI><A HREF=\"#cp" + signatureIndex + "\">Signature index(" + signatureIndex + ")</A></UL>\n");
            break;
        default:
            printWriter.println("<P><TT>" + Class2HTML.toHTML(constantPool.constantToString(index, tag)) + "</TT>\n");
        } // switch
    }
}
