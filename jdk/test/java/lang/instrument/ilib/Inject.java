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
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

public class Inject implements RuntimeConstants {

    public static byte[] instrumentation(Options opt,
                                         ClassLoader loader,
                                         String className,
                                         byte[] classfileBuffer) {
        ClassReaderWriter c = new ClassReaderWriter(classfileBuffer);
        (new Inject(className, c, loader == null, opt)).doit();
        return c.result();
    }

    static boolean verbose = false;

    final String className;
    final ClassReaderWriter c;
    final boolean isSystem;
    final Options options;

    int constantPoolCount;
    int methodsCount;
    int methodsCountPos;
    int profiler;
    int wrappedTrackerIndex = 0;
    int thisClassIndex = 0;

    TrackerInjector callInjector;
    TrackerInjector allocInjector;
    TrackerInjector defaultInjector;

    static interface TrackerInjector extends Injector {
        void reinit(int tracker);
        int stackSize(int currentSize);
    }

    static class SimpleInjector implements TrackerInjector {
        byte[] injection;

        public int stackSize(int currentSize) {
            return currentSize;
        }

        public void reinit(int tracker) {
            injection = new byte[3];
            injection[0] = (byte)opc_invokestatic;
            injection[1] = (byte)(tracker >> 8);
            injection[2] = (byte)tracker;
        }

        public byte[] bytecodes(String className, String methodName, int location) {
            return injection;
        }
    }

    static class ObjectInjector implements TrackerInjector {
        byte[] injection;

        public int stackSize(int currentSize) {
            return currentSize + 1;
        }

        public void reinit(int tracker) {
            injection = new byte[4];
            injection[0] = (byte)opc_dup;
            injection[1] = (byte)opc_invokestatic;
            injection[2] = (byte)(tracker >> 8);
            injection[3] = (byte)tracker;
        }

        public byte[] bytecodes(String className, String methodName, int location) {
            return injection;
        }
    }

    class IndexedInjector implements TrackerInjector {
        int counter = 0;
        int tracker;
        List<Info> infoList = new ArrayList<>();

        public int stackSize(int currentSize) {
            return currentSize + 1;
        }

        public void reinit(int tracker) {
            this.tracker = tracker;
        }

        void dump(File outDir, String filename) throws IOException {
            try (FileOutputStream fileOut =
                     new FileOutputStream(new File(outDir, filename));
                 DataOutputStream dataOut = new DataOutputStream(fileOut))
            {
                String currentClassName = null;

                dataOut.writeInt(infoList.size());
                for (Iterator<Info> it = infoList.iterator(); it.hasNext(); ) {
                    Info info = it.next();
                    if (!info.className.equals(currentClassName)) {
                        dataOut.writeInt(123456); // class name marker
                        currentClassName = info.className;
                        dataOut.writeUTF(currentClassName);
                    }
                    dataOut.writeInt(info.location);
                    dataOut.writeUTF(info.methodName);
                }
            }
        }

        public byte[] bytecodes(String className, String methodName, int location) {
            byte[] injection = new byte[6];
            int injectedIndex = options.fixedIndex != 0? options.fixedIndex : ++counter;
            infoList.add(new Info(counter, className, methodName, location));
            injection[0] = (byte)opc_sipush;
            injection[1] = (byte)(injectedIndex >> 8);
            injection[2] = (byte)injectedIndex;
            injection[3] = (byte)opc_invokestatic;
            injection[4] = (byte)(tracker >> 8);
            injection[5] = (byte)tracker;
            return injection;
        }
    }

    Inject(String className, ClassReaderWriter c, boolean isSystem, Options options) {
        this.className = className;
        this.c = c;
        this.isSystem = isSystem;
        this.options = options;
    }

    void doit() {
        int i;
        c.copy(4 + 2 + 2); // magic min/maj version
        int constantPoolCountPos = c.generatedPosition();
        constantPoolCount = c.copyU2();
        // copy old constant pool
        c.copyConstantPool(constantPoolCount);

        if (verbose) {
            System.out.println("ConstantPool expanded from: " +
                               constantPoolCount);
        }

        profiler = addClassToConstantPool(options.trackerClassName);
        if (options.shouldInstrumentNew || options.shouldInstrumentObjectInit) {
            if (options.shouldInstrumentIndexed) {
                if (allocInjector == null) {
                    // first time - create it
                    allocInjector = new IndexedInjector();
                }
                int allocTracker = addMethodToConstantPool(profiler,
                                                           options.allocTrackerMethodName,
                                                           "(I)V");
                allocInjector.reinit(allocTracker);
            } else if (options.shouldInstrumentObject) {
                if (allocInjector == null) {
                    // first time - create it
                    allocInjector = new ObjectInjector();
                }
                int allocTracker = addMethodToConstantPool(profiler,
                                                           options.allocTrackerMethodName,
                                                           "(Ljava/lang/Object;)V");
                allocInjector.reinit(allocTracker);
            } else {
                if (allocInjector == null) {
                    // first time - create it
                    allocInjector = new SimpleInjector();
                }
                int allocTracker = addMethodToConstantPool(profiler,
                                                           options.allocTrackerMethodName,
                                                           "()V");
                allocInjector.reinit(allocTracker);
            }
            defaultInjector = allocInjector;
        }
        if (options.shouldInstrumentCall) {
            if (options.shouldInstrumentIndexed) {
                if (callInjector == null) {
                    // first time - create it
                    callInjector = new IndexedInjector();
                }
                int callTracker = addMethodToConstantPool(profiler,
                                                          options.callTrackerMethodName,
                                                          "(I)V");
                callInjector.reinit(callTracker);
            } else {
                if (callInjector == null) {
                    // first time - create it
                    callInjector = new SimpleInjector();
                }
                int callTracker = addMethodToConstantPool(profiler,
                                                          options.callTrackerMethodName,
                                                          "()V");
                callInjector.reinit(callTracker);
            }
            defaultInjector = callInjector;
        }

        if (verbose) {
            System.out.println("To: " + constantPoolCount);
        }

        c.setSection(1);

        c.copy(2 + 2 + 2);  // access, this, super
        int interfaceCount = c.copyU2();
        if (verbose) {
            System.out.println("interfaceCount: " + interfaceCount);
        }
        c.copy(interfaceCount * 2);
        copyFields(); // fields
        copyMethods(); // methods
        int attrCountPos = c.generatedPosition();
        int attrCount = c.copyU2();
        if (verbose) {
            System.out.println("class attrCount: " + attrCount);
        }
        // copy the class attributes
        copyAttrs(attrCount);

        c.randomAccessWriteU2(constantPoolCountPos, constantPoolCount);
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
        methodsCountPos = c.generatedPosition();
        methodsCount = c.copyU2();
        int initialMethodsCount = methodsCount;
        if (verbose) {
            System.out.println("methods count: " + methodsCount);
        }
        for (int i = 0; i < initialMethodsCount; ++i) {
            copyMethod();
        }
    }

    void copyMethod() {
        int accessFlags = c.copyU2();// access flags
        if (options.shouldInstrumentNativeMethods && (accessFlags & ACC_NATIVE) != 0) {
            wrapNativeMethod(accessFlags);
            return;
        }
        int nameIndex = c.copyU2();  // name
        String methodName = c.constantPoolString(nameIndex);
        c.copyU2();                  // descriptor
        int attrCount = c.copyU2();  // attribute count
        if (verbose) {
            System.out.println("methods attr count: " + attrCount);
        }
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForMethod(methodName, accessFlags);
        }
    }

    void wrapNativeMethod(int accessFlags) {
        // first, copy the native method with the name changed
        // accessFlags have already been copied
        int nameIndex = c.readU2();        // name
        String methodName = c.constantPoolString(nameIndex);
        String wrappedMethodName = options.wrappedPrefix + methodName;
        int wrappedNameIndex = writeCPEntryUtf8(wrappedMethodName);
        c.writeU2(wrappedNameIndex);       // change to the wrapped name

        int descriptorIndex = c.copyU2();  // descriptor index

        int attrCount = c.copyU2();        // attribute count
        // need to replicate these attributes (esp Exceptions) in wrapper
        // so mark this location so we can rewind
        c.markLocalPositionStart();
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForMethod(methodName, accessFlags);
        }
        if (true) {
            System.err.println("   wrapped: " + methodName);
        }

        // now write the wrapper method
        c.writeU2(accessFlags & ~ACC_NATIVE);
        c.writeU2(nameIndex);           // original unwrapped name
        c.writeU2(descriptorIndex);     // descriptor is the same

        c.writeU2(attrCount + 1);       // wrapped plus a code attribute
        // rewind to wrapped attributes
        c.rewind();
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForMethod(methodName, accessFlags);
        }

        // generate a Code attribute for the wrapper method
        int wrappedIndex = addMethodToConstantPool(getThisClassIndex(),
                                                   wrappedNameIndex,
                                                   descriptorIndex);
        String descriptor = c.constantPoolString(descriptorIndex);
        createWrapperCodeAttr(nameIndex, accessFlags, descriptor, wrappedIndex);

        // increment method count
        c.randomAccessWriteU2(methodsCountPos, ++methodsCount);
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
        if (nameIndex == c.codeAttributeIndex) {
            try {
                copyCodeAttr(methodName);
            } catch (IOException exc) {
                System.err.println("Code Exception - " + exc);
                System.exit(1);
            }
        } else {
            int len = c.copyU4();     // attr len
            if (verbose) {
                System.out.println("method attr len: " + len);
            }
            c.copy(len);              // attribute info
        }
    }

    void copyAttrForCode(InjectBytecodes ib) throws IOException {
        int nameIndex = c.copyU2();   // name

        // check for Code attr
        if (nameIndex == c.lineNumberAttributeIndex) {
            ib.copyLineNumberAttr();
        } else if (nameIndex == c.localVarAttributeIndex) {
            ib.copyLocalVarAttr();
        } else {
            int len = c.copyU4();     // attr len
            if (verbose) {
                System.out.println("code attr len: " + len);
            }
            c.copy(len);              // attribute info
        }
    }

    void copyCodeAttr(String methodName) throws IOException {
        if (verbose) {
            System.out.println("Code attr found");
        }
        int attrLengthPos = c.generatedPosition();
        int attrLength = c.copyU4();        // attr len
        int maxStack = c.readU2();          // max stack
        c.writeU2(defaultInjector == null? maxStack :
                  defaultInjector.stackSize(maxStack));  // big enough for injected code
        c.copyU2();                         // max locals
        int codeLengthPos = c.generatedPosition();
        int codeLength = c.copyU4();        // code length
        if (options.targetMethod != null && !options.targetMethod.equals(methodName)) {
            c.copy(attrLength - 8); // copy remainder minus already copied
            return;
        }
        if (isSystem) {
            if (codeLength == 1 && methodName.equals("finalize")) {
                if (verbose) {
                    System.out.println("empty system finalizer not instrumented");
                }
                c.copy(attrLength - 8); // copy remainder minus already copied
                return;
            }
            if (codeLength == 1 && methodName.equals("<init>")) {
                if (verbose) {
                    System.out.println("empty system constructor not instrumented");
                }
                if (!options.shouldInstrumentObjectInit) {
                    c.copy(attrLength - 8); // copy remainder minus already copied
                    return;
                }
            }
            if (methodName.equals("<clinit>")) {
                if (verbose) {
                    System.out.println("system class initializer not instrumented");
                }
                c.copy(attrLength - 8); // copy remainder minus already copied
                return;
            }
        }
        if (options.shouldInstrumentObjectInit
            && (!className.equals("java/lang/Object")
                || !methodName.equals("<init>"))) {
            c.copy(attrLength - 8); // copy remainder minus already copied
            return;
        }

        InjectBytecodes ib = new InjectBytecodes(c, codeLength, className, methodName);

        if (options.shouldInstrumentNew) {
            ib.injectAfter(opc_new, allocInjector);
            ib.injectAfter(opc_newarray, allocInjector);
            ib.injectAfter(opc_anewarray, allocInjector);
            ib.injectAfter(opc_multianewarray, allocInjector);
        }
        if (options.shouldInstrumentCall) {
            ib.inject(0, callInjector.bytecodes(className, methodName, 0));
        }
        if (options.shouldInstrumentObjectInit) {
            ib.inject(0, allocInjector.bytecodes(className, methodName, 0));
        }

        ib.adjustOffsets();

        // fix up code length
        int newCodeLength = c.generatedPosition() - (codeLengthPos + 4);
        c.randomAccessWriteU4(codeLengthPos, newCodeLength);
        if (verbose) {
            System.out.println("code length old: " + codeLength +
                               ", new: " + newCodeLength);
        }

        ib.copyExceptionTable();

        int attrCount = c.copyU2();
        for (int i = 0; i < attrCount; ++i) {
            copyAttrForCode(ib);
        }

        // fix up attr length
        int newAttrLength = c.generatedPosition() - (attrLengthPos + 4);
        c.randomAccessWriteU4(attrLengthPos, newAttrLength);
        if (verbose) {
            System.out.println("attr length old: " + attrLength +
                               ", new: " + newAttrLength);
        }
    }

    int nextDescriptorIndex(String descriptor, int index) {
        switch (descriptor.charAt(index)) {
        case 'B': // byte
        case 'C': // char
        case 'I': // int
        case 'S': // short
        case 'Z': // boolean
        case 'F': // float
        case 'D': // double
        case 'J': // long
            return index + 1;
        case 'L': // object
            int i = index + 1;
            while (descriptor.charAt(i) != ';') {
                ++i;
            }
            return i + 1;
        case '[': // array
            return nextDescriptorIndex(descriptor, index + 1);
        }
        throw new InternalError("should not reach here");
    }

    int getWrappedTrackerIndex() {
        if (wrappedTrackerIndex == 0) {
            wrappedTrackerIndex = addMethodToConstantPool(profiler,
                                                          options.wrappedTrackerMethodName,
                                                          "(Ljava/lang/String;I)V");
        }
        return wrappedTrackerIndex;
    }

    int getThisClassIndex() {
        if (thisClassIndex == 0) {
            thisClassIndex = addClassToConstantPool(className);
        }
        return thisClassIndex;
    }

    int computeMaxLocals(String descriptor, int accessFlags) {
        int index = 1;
        int slot = 0;

        if ((accessFlags & ACC_STATIC) == 0) {
            ++slot;
        }
        char type;
        while ((type = descriptor.charAt(index)) != ')') {
            switch (type) {
            case 'B': // byte
            case 'C': // char
            case 'I': // int
            case 'S': // short
            case 'Z': // boolean
            case 'F': // float
            case 'L': // object
            case '[': // array
                ++slot;
                break;
            case 'D': // double
            case 'J': // long
                slot += 2;
                break;
            }
            index = nextDescriptorIndex(descriptor, index);
        }

        return slot;
    }


    void createWrapperCodeAttr(int methodNameIndex, int accessFlags,
                               String descriptor, int wrappedIndex) {
        int maxLocals = computeMaxLocals(descriptor, accessFlags);

        c.writeU2(c.codeAttributeIndex);        //
        int attrLengthPos = c.generatedPosition();
        c.writeU4(0);                // attr len -- fix up below
        c.writeU2(maxLocals + 4);    // max stack
        c.writeU2(maxLocals);        // max locals
        int codeLengthPos = c.generatedPosition();
        c.writeU4(0);                // code length -- fix up below

        int methodStringIndex = writeCPEntryString(methodNameIndex);

        c.writeU1(opc_ldc_w);
        c.writeU2(methodStringIndex);  // send the method name
        c.writeU1(opc_sipush);
        c.writeU2(options.fixedIndex);
        c.writeU1(opc_invokestatic);
        c.writeU2(getWrappedTrackerIndex());

        // set-up args
        int index = 1;
        int slot = 0;
        if ((accessFlags & ACC_STATIC) == 0) {
            c.writeU1(opc_aload_0);  // this
            ++slot;
        }
        char type;
        while ((type = descriptor.charAt(index)) != ')') {
            switch (type) {
            case 'B': // byte
            case 'C': // char
            case 'I': // int
            case 'S': // short
            case 'Z': // boolean
                c.writeU1(opc_iload);
                c.writeU1(slot);
                ++slot;
                break;
            case 'F': // float
                c.writeU1(opc_fload);
                c.writeU1(slot);
                ++slot;
                break;
            case 'D': // double
                c.writeU1(opc_dload);
                c.writeU1(slot);
                slot += 2;
                break;
            case 'J': // long
                c.writeU1(opc_lload);
                c.writeU1(slot);
                slot += 2;
                break;
            case 'L': // object
            case '[': // array
                c.writeU1(opc_aload);
                c.writeU1(slot);
                ++slot;
                break;
            }
            index = nextDescriptorIndex(descriptor, index);
        }

        // call the wrapped version
        if ((accessFlags & ACC_STATIC) == 0) {
            c.writeU1(opc_invokevirtual);
        } else {
            c.writeU1(opc_invokestatic);
        }
        c.writeU2(wrappedIndex);

        // return correct type
        switch (descriptor.charAt(index+1)) {
        case 'B': // byte
        case 'C': // char
        case 'I': // int
        case 'S': // short
        case 'Z': // boolean
            c.writeU1(opc_ireturn);
            break;
        case 'F': // float
            c.writeU1(opc_freturn);
            break;
        case 'D': // double
            c.writeU1(opc_dreturn);
            break;
        case 'J': // long
            c.writeU1(opc_lreturn);
            break;
        case 'L': // object
        case '[': // array
            c.writeU1(opc_areturn);
            break;
        case 'V': // void
            c.writeU1(opc_return);
            break;
        }

        // end of code

        // fix up code length
        int newCodeLength = c.generatedPosition() - (codeLengthPos + 4);
        c.randomAccessWriteU4(codeLengthPos, newCodeLength);

        c.writeU2(0);                // exception table length
        c.writeU2(0);                // attribute count

        // fix up attr length
        int newAttrLength = c.generatedPosition() - (attrLengthPos + 4);
        c.randomAccessWriteU4(attrLengthPos, newAttrLength);
    }


    int addClassToConstantPool(String className) {
        int prevSection = c.setSection(0);
        int classNameIndex = writeCPEntryUtf8(className);
        int classIndex = writeCPEntryClass(classNameIndex);
        c.setSection(prevSection);
        return classIndex;
    }

    int addMethodToConstantPool(int classIndex,
                                String methodName,
                                String descr) {
        int prevSection = c.setSection(0);
        int methodNameIndex = writeCPEntryUtf8(methodName);
        int descrIndex = writeCPEntryUtf8(descr);
        c.setSection(prevSection);
        return addMethodToConstantPool(classIndex, methodNameIndex, descrIndex);
    }

    int addMethodToConstantPool(int classIndex,
                                int methodNameIndex,
                                int descrIndex) {
        int prevSection = c.setSection(0);
        int nameAndTypeIndex = writeCPEntryNameAndType(methodNameIndex,
                                                       descrIndex);
        int methodIndex = writeCPEntryMethodRef(classIndex, nameAndTypeIndex);
        c.setSection(prevSection);
        return methodIndex;
    }

    int writeCPEntryUtf8(String str) {
        int prevSection = c.setSection(0);
        int len = str.length();
        c.writeU1(CONSTANT_UTF8); // Utf8 tag
        c.writeU2(len);
        for (int i = 0; i < len; ++i) {
            c.writeU1(str.charAt(i));
        }
        c.setSection(prevSection);
        return constantPoolCount++;
    }

    int writeCPEntryString(int utf8Index) {
        int prevSection = c.setSection(0);
        c.writeU1(CONSTANT_STRING);
        c.writeU2(utf8Index);
        c.setSection(prevSection);
        return constantPoolCount++;
    }

    int writeCPEntryClass(int classNameIndex) {
        int prevSection = c.setSection(0);
        c.writeU1(CONSTANT_CLASS);
        c.writeU2(classNameIndex);
        c.setSection(prevSection);
        return constantPoolCount++;
    }

    int writeCPEntryNameAndType(int nameIndex, int descrIndex) {
        int prevSection = c.setSection(0);
        c.writeU1(CONSTANT_NAMEANDTYPE);
        c.writeU2(nameIndex);
        c.writeU2(descrIndex);
        c.setSection(prevSection);
        return constantPoolCount++;
    }

    int writeCPEntryMethodRef(int classIndex, int nameAndTypeIndex) {
        int prevSection = c.setSection(0);
        c.writeU1(CONSTANT_METHOD);
        c.writeU2(classIndex);
        c.writeU2(nameAndTypeIndex);
        c.setSection(prevSection);
        return constantPoolCount++;
    }
}
