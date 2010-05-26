/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package ilib;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.CharArrayWriter;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

public class ClassDump implements RuntimeConstants {

  public static void dump(Options opt,
                                       ClassLoader loader,
                                       String className,
                                       byte[] classfileBuffer) {
    ClassReaderWriter c = new ClassReaderWriter(classfileBuffer);
    (new ClassDump(className, c)).doit();
  }

    static boolean verbose = true;

    final String className;
    final ClassReaderWriter c;
    private final PrintStream output;

    int constantPoolCount;
    int methodsCount;

    ClassDump(String className, ClassReaderWriter c) {
        this.className = className;
        this.c = c;
        this.output = System.err;
    }

    void doit() {
        int i;
        c.copy(4 + 2 + 2); // magic min/maj version
        constantPoolCount = c.copyU2();
        // copy old constant pool
        c.copyConstantPool(constantPoolCount);

        traceln("ConstantPool size: " + constantPoolCount);

        c.copy(2 + 2 + 2);  // access, this, super
        int interfaceCount = c.copyU2();
        traceln("interfaceCount: " + interfaceCount);
        c.copy(interfaceCount * 2);
        copyFields(); // fields
        copyMethods(); // methods
        int attrCount = c.copyU2();
        traceln("class attrCount: " + attrCount);
        // copy the class attributes
        copyAttrs(attrCount);
    }


    void copyFields() {
        int count = c.copyU2();
        if (verbose) {
            System.out.println("fields count: " + count);
        }
        for (int i = 0; i < count; ++i) {
            c.copy(6); // access, name, descriptor
            int attrCount = c.copyU2();
            if (verbose) {
                System.out.println("field attr count: " + attrCount);
            }
            copyAttrs(attrCount);
        }
    }

    void copyMethods() {
        methodsCount = c.copyU2();
        if (verbose) {
            System.out.println("methods count: " + methodsCount);
        }
        for (int i = 0; i < methodsCount; ++i) {
            copyMethod();
        }
    }

    void copyMethod() {
        int accessFlags = c.copyU2();// access flags
        int nameIndex = c.copyU2();  // name
        checkIndex(nameIndex, "Method name");
        String methodName = c.constantPoolString(nameIndex);
        traceln("method: " + methodName);
        int descriptorIndex = c.copyU2();                  // descriptor
        checkIndex(descriptorIndex, "Method descriptor");
        int attrCount = c.copyU2();  // attribute count
        if (verbose) {
            System.out.println("method attr count: " + attrCount);
        }
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForMethod(methodName, accessFlags);
        }
    }

    void copyAttrs(int attrCount) {
        for (int i = 0; i < attrCount; ++i) {
            copyAttr();
        }
    }

    void copyAttr() {
        c.copy(2);             // name
        int len = c.copyU4();  // attr len
        if (verbose) {
            System.out.println("attr len: " + len);
        }
        c.copy(len);           // attribute info
    }

    void copyAttrForMethod(String methodName, int accessFlags) {
        int nameIndex = c.copyU2();   // name
        // check for Code attr
        checkIndex(nameIndex, "Method attr name");
        if (nameIndex == c.codeAttributeIndex) {
            try {
                copyCodeAttr(methodName);
            } catch (IOException exc) {
                System.err.println("Code Exception - " + exc);
                System.exit(1);
            }
        } else {
            int len = c.copyU4();     // attr len
            traceln("method attr len: " + len);
            c.copy(len);              // attribute info
        }
    }

    void copyAttrForCode() throws IOException {
        int nameIndex = c.copyU2();   // name

        checkIndex(nameIndex, "Code attr name");
        int len = c.copyU4();     // attr len
        traceln("code attr len: " + len);
        c.copy(len);              // attribute info
    }

    void copyCodeAttr(String methodName) throws IOException {
        traceln("Code attr found");
        int attrLength = c.copyU4();        // attr len
        checkLength(attrLength, "Code attr length");
        int maxStack = c.readU2();          // max stack
        c.copyU2();                         // max locals
        int codeLength = c.copyU4();        // code length
        checkLength(codeLength, "Code length");

        copyExceptionTable();

        int attrCount = c.copyU2();
        checkLength(attrCount, "Code attr count");
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForCode();
        }
    }

    /**
     * Copy the exception table for this method code
     */
    void copyExceptionTable() throws IOException {
        int tableLength = c.copyU2();   // exception table len
        checkLength(tableLength, "Exception Table length");
        if (tableLength > 0) {
            traceln();
            traceln("Exception table:");
            traceln(" from:old/new  to:old/new target:old/new type");
            for (int tcnt = tableLength; tcnt > 0; --tcnt) {
                int startPC = c.readU2();
                int endPC = c.readU2();
                int handlerPC = c.readU2();
                int catchType = c.copyU2();
                if (verbose) {
                    traceFixedWidthInt(startPC, 6);
                    traceFixedWidthInt(endPC, 6);
                    traceFixedWidthInt(handlerPC, 6);
                    trace("    ");
                    if (catchType == 0)
                        traceln("any");
                    else {
                        traceln("" + catchType);
                    }
                }
            }
        }
    }

    private void checkIndex(int index, String comment) {
        if (index > constantPoolCount) {
            output.println("ERROR BAD INDEX " + comment + " : " + index);
        } else {
            traceln(comment + " : " + index);
        }
    }

    private void checkLength(int length, String comment) {
        if (length > c.inputBytes().length) {
            output.println("ERROR BAD LENGTH " + comment + " : " + length);
        } else {
            traceln(comment + " : " + length);
        }
    }

    private void trace(String str) {
        if (verbose) {
            output.print(str);
        }
    }

    private void traceln(String str) {
        if (verbose) {
            output.println(str);
        }
    }

    private void traceln() {
        if (verbose) {
            output.println();
        }
    }

    private void trace(int i) {
        if (verbose) {
            output.print(i);
        }
    }

    /**
     * Print an integer so that it takes 'length' characters in
     * the output.  Temporary until formatting code is stable.
     */
    private void traceFixedWidthInt(int x, int length) {
        if (verbose) {
            CharArrayWriter baStream = new CharArrayWriter();
            PrintWriter pStream = new PrintWriter(baStream);
            pStream.print(x);
            String str = baStream.toString();
            for (int cnt = length - str.length(); cnt > 0; --cnt)
                trace(" ");
            trace(str);
        }
    }


}
