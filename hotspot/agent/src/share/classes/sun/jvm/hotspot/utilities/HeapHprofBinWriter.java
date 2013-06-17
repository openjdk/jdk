/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

/*
 * This class writes Java heap in hprof binary format. This format is
 * used by Heap Analysis Tool (HAT). The class is heavily influenced
 * by 'hprof_io.c' of 1.5 new hprof implementation.
 */

/* hprof binary format: (result either written to a file or sent over
 * the network).
 *
 * WARNING: This format is still under development, and is subject to
 * change without notice.
 *
 * header    "JAVA PROFILE 1.0.1" (0-terminated)
 * u4        size of identifiers. Identifiers are used to represent
 *            UTF8 strings, objects, stack traces, etc. They usually
 *            have the same size as host pointers. For example, on
 *            Solaris and Win32, the size is 4.
 * u4         high word
 * u4         low word    number of milliseconds since 0:00 GMT, 1/1/70
 * [record]*  a sequence of records.
 *
 */

/*
 *
 * Record format:
 *
 * u1         a TAG denoting the type of the record
 * u4         number of *microseconds* since the time stamp in the
 *            header. (wraps around in a little more than an hour)
 * u4         number of bytes *remaining* in the record. Note that
 *            this number excludes the tag and the length field itself.
 * [u1]*      BODY of the record (a sequence of bytes)
 */

/*
 * The following TAGs are supported:
 *
 * TAG           BODY       notes
 *----------------------------------------------------------
 * HPROF_UTF8               a UTF8-encoded name
 *
 *               id         name ID
 *               [u1]*      UTF8 characters (no trailing zero)
 *
 * HPROF_LOAD_CLASS         a newly loaded class
 *
 *                u4        class serial number (> 0)
 *                id        class object ID
 *                u4        stack trace serial number
 *                id        class name ID
 *
 * HPROF_UNLOAD_CLASS       an unloading class
 *
 *                u4        class serial_number
 *
 * HPROF_FRAME              a Java stack frame
 *
 *                id        stack frame ID
 *                id        method name ID
 *                id        method signature ID
 *                id        source file name ID
 *                u4        class serial number
 *                i4        line number. >0: normal
 *                                       -1: unknown
 *                                       -2: compiled method
 *                                       -3: native method
 *
 * HPROF_TRACE              a Java stack trace
 *
 *               u4         stack trace serial number
 *               u4         thread serial number
 *               u4         number of frames
 *               [id]*      stack frame IDs
 *
 *
 * HPROF_ALLOC_SITES        a set of heap allocation sites, obtained after GC
 *
 *               u2         flags 0x0001: incremental vs. complete
 *                                0x0002: sorted by allocation vs. live
 *                                0x0004: whether to force a GC
 *               u4         cutoff ratio
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *               u4         number of sites that follow
 *               [u1        is_array: 0:  normal object
 *                                    2:  object array
 *                                    4:  boolean array
 *                                    5:  char array
 *                                    6:  float array
 *                                    7:  double array
 *                                    8:  byte array
 *                                    9:  short array
 *                                    10: int array
 *                                    11: long array
 *                u4        class serial number (may be zero during startup)
 *                u4        stack trace serial number
 *                u4        number of bytes alive
 *                u4        number of instances alive
 *                u4        number of bytes allocated
 *                u4]*      number of instance allocated
 *
 * HPROF_START_THREAD       a newly started thread.
 *
 *               u4         thread serial number (> 0)
 *               id         thread object ID
 *               u4         stack trace serial number
 *               id         thread name ID
 *               id         thread group name ID
 *               id         thread group parent name ID
 *
 * HPROF_END_THREAD         a terminating thread.
 *
 *               u4         thread serial number
 *
 * HPROF_HEAP_SUMMARY       heap summary
 *
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *
 * HPROF_HEAP_DUMP          denote a heap dump
 *
 *               [heap dump sub-records]*
 *
 *                          There are four kinds of heap dump sub-records:
 *
 *               u1         sub-record type
 *
 *               HPROF_GC_ROOT_UNKNOWN         unknown root
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_OBJ      thread object
 *
 *                          id         thread object ID  (may be 0 for a
 *                                     thread newly attached through JNI)
 *                          u4         thread sequence number
 *                          u4         stack trace sequence number
 *
 *               HPROF_GC_ROOT_JNI_GLOBAL      JNI global ref root
 *
 *                          id         object ID
 *                          id         JNI global ref ID
 *
 *               HPROF_GC_ROOT_JNI_LOCAL       JNI local ref
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_JAVA_FRAME      Java stack frame
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_NATIVE_STACK    Native stack
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_STICKY_CLASS    System class
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_BLOCK    Reference from thread block
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_MONITOR_USED    Busy monitor
 *
 *                          id         object ID
 *
 *               HPROF_GC_CLASS_DUMP           dump of a class object
 *
 *                          id         class object ID
 *                          u4         stack trace serial number
 *                          id         super class object ID
 *                          id         class loader object ID
 *                          id         signers object ID
 *                          id         protection domain object ID
 *                          id         reserved
 *                          id         reserved
 *
 *                          u4         instance size (in bytes)
 *
 *                          u2         size of constant pool
 *                          [u2,       constant pool index,
 *                           ty,       type
 *                                     2:  object
 *                                     4:  boolean
 *                                     5:  char
 *                                     6:  float
 *                                     7:  double
 *                                     8:  byte
 *                                     9:  short
 *                                     10: int
 *                                     11: long
 *                           vl]*      and value
 *
 *                          u2         number of static fields
 *                          [id,       static field name,
 *                           ty,       type,
 *                           vl]*      and value
 *
 *                          u2         number of inst. fields (not inc. super)
 *                          [id,       instance field name,
 *                           ty]*      type
 *
 *               HPROF_GC_INSTANCE_DUMP        dump of a normal object
 *
 *                          id         object ID
 *                          u4         stack trace serial number
 *                          id         class object ID
 *                          u4         number of bytes that follow
 *                          [vl]*      instance field values (class, followed
 *                                     by super, super's super ...)
 *
 *               HPROF_GC_OBJ_ARRAY_DUMP       dump of an object array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          id         array class ID
 *                          [id]*      elements
 *
 *               HPROF_GC_PRIM_ARRAY_DUMP      dump of a primitive array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          u1         element type
 *                                     4:  boolean array
 *                                     5:  char array
 *                                     6:  float array
 *                                     7:  double array
 *                                     8:  byte array
 *                                     9:  short array
 *                                     10: int array
 *                                     11: long array
 *                          [u1]*      elements
 *
 * HPROF_CPU_SAMPLES        a set of sample traces of running threads
 *
 *                u4        total number of samples
 *                u4        # of traces
 *               [u4        # of samples
 *                u4]*      stack trace serial number
 *
 * HPROF_CONTROL_SETTINGS   the settings of on/off switches
 *
 *                u4        0x00000001: alloc traces on/off
 *                          0x00000002: cpu sampling on/off
 *                u2        stack trace depth
 *
 */

public class HeapHprofBinWriter extends AbstractHeapGraphWriter {
    // hprof binary file header
    private static final String HPROF_HEADER = "JAVA PROFILE 1.0.1";

    // constants in enum HprofTag
    private static final int HPROF_UTF8             = 0x01;
    private static final int HPROF_LOAD_CLASS       = 0x02;
    private static final int HPROF_UNLOAD_CLASS     = 0x03;
    private static final int HPROF_FRAME            = 0x04;
    private static final int HPROF_TRACE            = 0x05;
    private static final int HPROF_ALLOC_SITES      = 0x06;
    private static final int HPROF_HEAP_SUMMARY     = 0x07;
    private static final int HPROF_START_THREAD     = 0x0A;
    private static final int HPROF_END_THREAD       = 0x0B;
    private static final int HPROF_HEAP_DUMP        = 0x0C;
    private static final int HPROF_CPU_SAMPLES      = 0x0D;
    private static final int HPROF_CONTROL_SETTINGS = 0x0E;

    // Heap dump constants
    // constants in enum HprofGcTag
    private static final int HPROF_GC_ROOT_UNKNOWN       = 0xFF;
    private static final int HPROF_GC_ROOT_JNI_GLOBAL    = 0x01;
    private static final int HPROF_GC_ROOT_JNI_LOCAL     = 0x02;
    private static final int HPROF_GC_ROOT_JAVA_FRAME    = 0x03;
    private static final int HPROF_GC_ROOT_NATIVE_STACK  = 0x04;
    private static final int HPROF_GC_ROOT_STICKY_CLASS  = 0x05;
    private static final int HPROF_GC_ROOT_THREAD_BLOCK  = 0x06;
    private static final int HPROF_GC_ROOT_MONITOR_USED  = 0x07;
    private static final int HPROF_GC_ROOT_THREAD_OBJ    = 0x08;
    private static final int HPROF_GC_CLASS_DUMP         = 0x20;
    private static final int HPROF_GC_INSTANCE_DUMP      = 0x21;
    private static final int HPROF_GC_OBJ_ARRAY_DUMP     = 0x22;
    private static final int HPROF_GC_PRIM_ARRAY_DUMP    = 0x23;

    // constants in enum HprofType
    private static final int HPROF_ARRAY_OBJECT  = 1;
    private static final int HPROF_NORMAL_OBJECT = 2;
    private static final int HPROF_BOOLEAN       = 4;
    private static final int HPROF_CHAR          = 5;
    private static final int HPROF_FLOAT         = 6;
    private static final int HPROF_DOUBLE        = 7;
    private static final int HPROF_BYTE          = 8;
    private static final int HPROF_SHORT         = 9;
    private static final int HPROF_INT           = 10;
    private static final int HPROF_LONG          = 11;

    // Java type codes
    private static final int JVM_SIGNATURE_BOOLEAN = 'Z';
    private static final int JVM_SIGNATURE_CHAR    = 'C';
    private static final int JVM_SIGNATURE_BYTE    = 'B';
    private static final int JVM_SIGNATURE_SHORT   = 'S';
    private static final int JVM_SIGNATURE_INT     = 'I';
    private static final int JVM_SIGNATURE_LONG    = 'J';
    private static final int JVM_SIGNATURE_FLOAT   = 'F';
    private static final int JVM_SIGNATURE_DOUBLE  = 'D';
    private static final int JVM_SIGNATURE_ARRAY   = '[';
    private static final int JVM_SIGNATURE_CLASS   = 'L';


    public synchronized void write(String fileName) throws IOException {
        // open file stream and create buffered data output stream
        FileOutputStream fos = new FileOutputStream(fileName);
        FileChannel chn = fos.getChannel();
        out = new DataOutputStream(new BufferedOutputStream(fos));

        VM vm = VM.getVM();
        dbg = vm.getDebugger();
        objectHeap = vm.getObjectHeap();
        symTbl = vm.getSymbolTable();

        OBJ_ID_SIZE = (int) vm.getOopSize();

        BOOLEAN_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_BOOLEAN);
        BYTE_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_BYTE);
        CHAR_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_CHAR);
        SHORT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_SHORT);
        INT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_INT);
        LONG_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_LONG);
        FLOAT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_FLOAT);
        DOUBLE_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_DOUBLE);
        OBJECT_BASE_OFFSET = TypeArray.baseOffsetInBytes(BasicType.T_OBJECT);

        BOOLEAN_SIZE = objectHeap.getBooleanSize();
        BYTE_SIZE = objectHeap.getByteSize();
        CHAR_SIZE = objectHeap.getCharSize();
        SHORT_SIZE = objectHeap.getShortSize();
        INT_SIZE = objectHeap.getIntSize();
        LONG_SIZE = objectHeap.getLongSize();
        FLOAT_SIZE = objectHeap.getFloatSize();
        DOUBLE_SIZE = objectHeap.getDoubleSize();

        // hprof bin format header
        writeFileHeader();

        // dummy stack trace without any frames so that
        // HAT can be run without -stack false option
        writeDummyTrace();

        // hprof UTF-8 symbols section
        writeSymbols();
        // HPROF_LOAD_CLASS records for all classes
        writeClasses();

        // write heap data now
        out.writeByte((byte)HPROF_HEAP_DUMP);
        out.writeInt(0); // relative timestamp

        // remember position of dump length, we will fixup
        // length later - hprof format requires length.
        out.flush();
        long dumpStart = chn.position();

        // write dummy length of 0 and we'll fix it later.
        out.writeInt(0);

        // write CLASS_DUMP records
        writeClassDumpRecords();

        // this will write heap data into the buffer stream
        super.write();

        // flush buffer stream and throw it.
        out.flush();
        out = null;

        // now get current position to calculate length
        long dumpEnd = chn.position();
        // calculate length of heap data
        int dumpLen = (int) (dumpEnd - dumpStart - 4);

        // seek the position to write length
        chn.position(dumpStart);

        // write length as integer
        fos.write((dumpLen >>> 24) & 0xFF);
        fos.write((dumpLen >>> 16) & 0xFF);
        fos.write((dumpLen >>> 8) & 0xFF);
        fos.write((dumpLen >>> 0) & 0xFF);

        // close the file stream
        fos.close();
    }

    private void writeClassDumpRecords() throws IOException {
        SystemDictionary sysDict = VM.getVM().getSystemDictionary();
        try {
            sysDict.allClassesDo(new SystemDictionary.ClassVisitor() {
                            public void visit(Klass k) {
                                try {
                                    writeClassDumpRecord(k);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        } catch (RuntimeException re) {
            handleRuntimeException(re);
        }
    }

    protected void writeClass(Instance instance) throws IOException {
        Klass reflectedKlass = java_lang_Class.asKlass(instance);
        // dump instance record only for primitive type Class objects.
        // all other Class objects are covered by writeClassDumpRecords.
        if (reflectedKlass == null) {
            writeInstance(instance);
        }
    }

    private void writeClassDumpRecord(Klass k) throws IOException {
        out.writeByte((byte)HPROF_GC_CLASS_DUMP);
        writeObjectID(k.getJavaMirror());
        out.writeInt(DUMMY_STACK_TRACE_ID);
        Klass superKlass = k.getJavaSuper();
        if (superKlass != null) {
            writeObjectID(superKlass.getJavaMirror());
        } else {
            writeObjectID(null);
        }

        if (k instanceof InstanceKlass) {
            InstanceKlass ik = (InstanceKlass) k;
            writeObjectID(ik.getClassLoader());
            writeObjectID(null);  // ik.getJavaMirror().getSigners());
            writeObjectID(null);  // ik.getJavaMirror().getProtectionDomain());
            // two reserved id fields
            writeObjectID(null);
            writeObjectID(null);
            List fields = getInstanceFields(ik);
            int instSize = getSizeForFields(fields);
            classDataCache.put(ik, new ClassData(instSize, fields));
            out.writeInt(instSize);

            // For now, ignore constant pool - HAT ignores too!
            // output number of cp entries as zero.
            out.writeShort((short) 0);

            List declaredFields = ik.getImmediateFields();
            List staticFields = new ArrayList();
            List instanceFields = new ArrayList();
            Iterator itr = null;
            for (itr = declaredFields.iterator(); itr.hasNext();) {
                Field field = (Field) itr.next();
                if (field.isStatic()) {
                    staticFields.add(field);
                } else {
                    instanceFields.add(field);
                }
            }

            // dump static field descriptors
            writeFieldDescriptors(staticFields, ik);

            // dump instance field descriptors
            writeFieldDescriptors(instanceFields, null);
        } else {
            if (k instanceof ObjArrayKlass) {
                ObjArrayKlass oak = (ObjArrayKlass) k;
                Klass bottomKlass = oak.getBottomKlass();
                if (bottomKlass instanceof InstanceKlass) {
                    InstanceKlass ik = (InstanceKlass) bottomKlass;
                    writeObjectID(ik.getClassLoader());
                    writeObjectID(null); // ik.getJavaMirror().getSigners());
                    writeObjectID(null); // ik.getJavaMirror().getProtectionDomain());
                } else {
                    writeObjectID(null);
                    writeObjectID(null);
                    writeObjectID(null);
                }
            } else {
                writeObjectID(null);
                writeObjectID(null);
                writeObjectID(null);
            }
            // two reserved id fields
            writeObjectID(null);
            writeObjectID(null);
            // write zero instance size -- as instance size
            // is variable for arrays.
            out.writeInt(0);
            // no constant pool for array klasses
            out.writeShort((short) 0);
            // no static fields for array klasses
            out.writeShort((short) 0);
            // no instance fields for array klasses
            out.writeShort((short) 0);
        }
    }

    protected void writeJavaThread(JavaThread jt, int index) throws IOException {
        out.writeByte((byte) HPROF_GC_ROOT_THREAD_OBJ);
        writeObjectID(jt.getThreadObj());
        out.writeInt(index);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        writeLocalJNIHandles(jt, index);
    }

    protected void writeLocalJNIHandles(JavaThread jt, int index) throws IOException {
        final int threadIndex = index;
        JNIHandleBlock blk = jt.activeHandles();
        if (blk != null) {
            try {
                blk.oopsDo(new AddressVisitor() {
                           public void visitAddress(Address handleAddr) {
                               try {
                                   if (handleAddr != null) {
                                       OopHandle oopHandle = handleAddr.getOopHandleAt(0);
                                       Oop oop = objectHeap.newOop(oopHandle);
                                       // exclude JNI handles hotspot internal objects
                                       if (oop != null && isJavaVisible(oop)) {
                                           out.writeByte((byte) HPROF_GC_ROOT_JNI_LOCAL);
                                           writeObjectID(oop);
                                           out.writeInt(threadIndex);
                                           out.writeInt(EMPTY_FRAME_DEPTH);
                                       }
                                   }
                               } catch (IOException exp) {
                                   throw new RuntimeException(exp);
                               }
                           }
                           public void visitCompOopAddress(Address handleAddr) {
                             throw new RuntimeException(
                                   " Should not reach here. JNIHandles are not compressed \n");
                           }
                       });
            } catch (RuntimeException re) {
                handleRuntimeException(re);
            }
        }
    }

    protected void writeGlobalJNIHandle(Address handleAddr) throws IOException {
        OopHandle oopHandle = handleAddr.getOopHandleAt(0);
        Oop oop = objectHeap.newOop(oopHandle);
        // exclude JNI handles of hotspot internal objects
        if (oop != null && isJavaVisible(oop)) {
            out.writeByte((byte) HPROF_GC_ROOT_JNI_GLOBAL);
            writeObjectID(oop);
            // use JNIHandle address as ID
            writeObjectID(getAddressValue(handleAddr));
        }
    }

    protected void writeObjectArray(ObjArray array) throws IOException {
        out.writeByte((byte) HPROF_GC_OBJ_ARRAY_DUMP);
        writeObjectID(array);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt((int) array.getLength());
        writeObjectID(array.getKlass().getJavaMirror());
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
            OopHandle handle = array.getOopHandleAt(index);
            writeObjectID(getAddressValue(handle));
        }
    }

    protected void writePrimitiveArray(TypeArray array) throws IOException {
        out.writeByte((byte) HPROF_GC_PRIM_ARRAY_DUMP);
        writeObjectID(array);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt((int) array.getLength());
        TypeArrayKlass tak = (TypeArrayKlass) array.getKlass();
        final int type = (int) tak.getElementType();
        out.writeByte((byte) type);
        switch (type) {
            case TypeArrayKlass.T_BOOLEAN:
                writeBooleanArray(array);
                break;
            case TypeArrayKlass.T_CHAR:
                writeCharArray(array);
                break;
            case TypeArrayKlass.T_FLOAT:
                writeFloatArray(array);
                break;
            case TypeArrayKlass.T_DOUBLE:
                writeDoubleArray(array);
                break;
            case TypeArrayKlass.T_BYTE:
                writeByteArray(array);
                break;
            case TypeArrayKlass.T_SHORT:
                writeShortArray(array);
                break;
            case TypeArrayKlass.T_INT:
                writeIntArray(array);
                break;
            case TypeArrayKlass.T_LONG:
                writeLongArray(array);
                break;
            default:
                throw new RuntimeException("should not reach here");
        }
    }

    private void writeBooleanArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = BOOLEAN_BASE_OFFSET + index * BOOLEAN_SIZE;
             out.writeBoolean(array.getHandle().getJBooleanAt(offset));
        }
    }

    private void writeByteArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = BYTE_BASE_OFFSET + index * BYTE_SIZE;
             out.writeByte(array.getHandle().getJByteAt(offset));
        }
    }

    private void writeShortArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = SHORT_BASE_OFFSET + index * SHORT_SIZE;
             out.writeShort(array.getHandle().getJShortAt(offset));
        }
    }

    private void writeIntArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = INT_BASE_OFFSET + index * INT_SIZE;
             out.writeInt(array.getHandle().getJIntAt(offset));
        }
    }

    private void writeLongArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = LONG_BASE_OFFSET + index * LONG_SIZE;
             out.writeLong(array.getHandle().getJLongAt(offset));
        }
    }

    private void writeCharArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = CHAR_BASE_OFFSET + index * CHAR_SIZE;
             out.writeChar(array.getHandle().getJCharAt(offset));
        }
    }

    private void writeFloatArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = FLOAT_BASE_OFFSET + index * FLOAT_SIZE;
             out.writeFloat(array.getHandle().getJFloatAt(offset));
        }
    }

    private void writeDoubleArray(TypeArray array) throws IOException {
        final int length = (int) array.getLength();
        for (int index = 0; index < length; index++) {
             long offset = DOUBLE_BASE_OFFSET + index * DOUBLE_SIZE;
             out.writeDouble(array.getHandle().getJDoubleAt(offset));
        }
    }

    protected void writeInstance(Instance instance) throws IOException {
        out.writeByte((byte) HPROF_GC_INSTANCE_DUMP);
        writeObjectID(instance);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        Klass klass = instance.getKlass();
        writeObjectID(klass.getJavaMirror());

        ClassData cd = (ClassData) classDataCache.get(klass);
        if (Assert.ASSERTS_ENABLED) {
            Assert.that(cd != null, "can not get class data for " + klass.getName().asString() + klass.getAddress());
        }
        List fields = cd.fields;
        int size = cd.instSize;
        out.writeInt(size);
        for (Iterator itr = fields.iterator(); itr.hasNext();) {
            writeField((Field) itr.next(), instance);
        }
    }

    //-- Internals only below this point

    private void writeFieldDescriptors(List fields, InstanceKlass ik)
        throws IOException {
        // ik == null for instance fields.
        out.writeShort((short) fields.size());
        for (Iterator itr = fields.iterator(); itr.hasNext();) {
            Field field = (Field) itr.next();
            Symbol name = symTbl.probe(field.getID().getName());
            writeSymbolID(name);
            char typeCode = (char) field.getSignature().getByteAt(0);
            int kind = signatureToHprofKind(typeCode);
            out.writeByte((byte)kind);
            if (ik != null) {
                // static field
                writeField(field, ik.getJavaMirror());
            }
        }
    }

    public static int signatureToHprofKind(char ch) {
        switch (ch) {
        case JVM_SIGNATURE_CLASS:
        case JVM_SIGNATURE_ARRAY:
            return HPROF_NORMAL_OBJECT;
        case JVM_SIGNATURE_BOOLEAN:
            return HPROF_BOOLEAN;
        case JVM_SIGNATURE_CHAR:
            return HPROF_CHAR;
        case JVM_SIGNATURE_FLOAT:
            return HPROF_FLOAT;
        case JVM_SIGNATURE_DOUBLE:
            return HPROF_DOUBLE;
        case JVM_SIGNATURE_BYTE:
            return HPROF_BYTE;
        case JVM_SIGNATURE_SHORT:
            return HPROF_SHORT;
        case JVM_SIGNATURE_INT:
            return HPROF_INT;
        case JVM_SIGNATURE_LONG:
            return HPROF_LONG;
        default:
            throw new RuntimeException("should not reach here");
        }
    }

    private void writeField(Field field, Oop oop) throws IOException {
        char typeCode = (char) field.getSignature().getByteAt(0);
        switch (typeCode) {
        case JVM_SIGNATURE_BOOLEAN:
            out.writeBoolean(((BooleanField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_CHAR:
            out.writeChar(((CharField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_BYTE:
            out.writeByte(((ByteField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_SHORT:
            out.writeShort(((ShortField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_INT:
            out.writeInt(((IntField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_LONG:
            out.writeLong(((LongField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_FLOAT:
            out.writeFloat(((FloatField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_DOUBLE:
            out.writeDouble(((DoubleField)field).getValue(oop));
            break;
        case JVM_SIGNATURE_CLASS:
        case JVM_SIGNATURE_ARRAY: {
            if (VM.getVM().isCompressedOopsEnabled()) {
              OopHandle handle = ((NarrowOopField)field).getValueAsOopHandle(oop);
              writeObjectID(getAddressValue(handle));
            } else {
              OopHandle handle = ((OopField)field).getValueAsOopHandle(oop);
              writeObjectID(getAddressValue(handle));
            }
            break;
        }
        default:
            throw new RuntimeException("should not reach here");
        }
    }

    private void writeHeader(int tag, int len) throws IOException {
        out.writeByte((byte)tag);
        out.writeInt(0); // current ticks
        out.writeInt(len);
    }

    private void writeDummyTrace() throws IOException {
        writeHeader(HPROF_TRACE, 3 * 4);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt(0);
        out.writeInt(0);
    }

    private void writeSymbols() throws IOException {
        try {
            symTbl.symbolsDo(new SymbolTable.SymbolVisitor() {
                    public void visit(Symbol sym) {
                        try {
                            writeSymbol(sym);
                        } catch (IOException exp) {
                            throw new RuntimeException(exp);
                        }
                    }
                });
        } catch (RuntimeException re) {
            handleRuntimeException(re);
        }
    }

    private void writeSymbol(Symbol sym) throws IOException {
        byte[] buf = sym.asString().getBytes("UTF-8");
        writeHeader(HPROF_UTF8, buf.length + OBJ_ID_SIZE);
        writeSymbolID(sym);
        out.write(buf);
    }

    private void writeClasses() throws IOException {
        // write class list (id, name) association
        SystemDictionary sysDict = VM.getVM().getSystemDictionary();
        try {
            sysDict.allClassesDo(new SystemDictionary.ClassVisitor() {
                private int serialNum = 1;
                public void visit(Klass k) {
                    try {
                        Instance clazz = k.getJavaMirror();
                        writeHeader(HPROF_LOAD_CLASS, 2 * (OBJ_ID_SIZE + 4));
                        out.writeInt(serialNum);
                        writeObjectID(clazz);
                        out.writeInt(DUMMY_STACK_TRACE_ID);
                        writeSymbolID(k.getName());
                        serialNum++;
                    } catch (IOException exp) {
                        throw new RuntimeException(exp);
                    }
                }
            });
        } catch (RuntimeException re) {
            handleRuntimeException(re);
        }
    }

    // writes hprof binary file header
    private void writeFileHeader() throws IOException {
        // version string
        out.writeBytes(HPROF_HEADER);
        out.writeByte((byte)'\0');

        // write identifier size. we use pointers as identifiers.
        out.writeInt(OBJ_ID_SIZE);

        // timestamp -- file creation time.
        out.writeLong(System.currentTimeMillis());
    }

    // writes unique ID for an object
    private void writeObjectID(Oop oop) throws IOException {
        OopHandle handle = (oop != null)? oop.getHandle() : null;
        long address = getAddressValue(handle);
        writeObjectID(address);
    }

    private void writeSymbolID(Symbol sym) throws IOException {
        writeObjectID(getAddressValue(sym.getAddress()));
    }

    private void writeObjectID(long address) throws IOException {
        if (OBJ_ID_SIZE == 4) {
            out.writeInt((int) address);
        } else {
            out.writeLong(address);
        }
    }

    private long getAddressValue(Address addr) {
        return (addr == null)? 0L : dbg.getAddressValue(addr);
    }

    // get all declared as well as inherited (directly/indirectly) fields
    private static List/*<Field>*/ getInstanceFields(InstanceKlass ik) {
        InstanceKlass klass = ik;
        List res = new ArrayList();
        while (klass != null) {
            List curFields = klass.getImmediateFields();
            for (Iterator itr = curFields.iterator(); itr.hasNext();) {
                Field f = (Field) itr.next();
                if (! f.isStatic()) {
                    res.add(f);
                }
            }
            klass = (InstanceKlass) klass.getSuper();
        }
        return res;
    }

    // get size in bytes (in stream) required for given fields.  Note
    // that this is not the same as object size in heap. The size in
    // heap will include size of padding/alignment bytes as well.
    private int getSizeForFields(List fields) {
        int size = 0;
        for (Iterator itr = fields.iterator(); itr.hasNext();) {
            Field field = (Field) itr.next();
            char typeCode = (char) field.getSignature().getByteAt(0);
            switch (typeCode) {
            case JVM_SIGNATURE_BOOLEAN:
            case JVM_SIGNATURE_BYTE:
                size++;
                break;
            case JVM_SIGNATURE_CHAR:
            case JVM_SIGNATURE_SHORT:
                size += 2;
                break;
            case JVM_SIGNATURE_INT:
            case JVM_SIGNATURE_FLOAT:
                size += 4;
                break;
            case JVM_SIGNATURE_CLASS:
            case JVM_SIGNATURE_ARRAY:
                size += OBJ_ID_SIZE;
                break;
            case JVM_SIGNATURE_LONG:
            case JVM_SIGNATURE_DOUBLE:
                size += 8;
                break;
            default:
                throw new RuntimeException("should not reach here");
            }
        }
        return size;
    }

    // We don't have allocation site info. We write a dummy
    // stack trace with this id.
    private static final int DUMMY_STACK_TRACE_ID = 1;
    private static final int EMPTY_FRAME_DEPTH = -1;

    private DataOutputStream out;
    private Debugger dbg;
    private ObjectHeap objectHeap;
    private SymbolTable symTbl;

    // oopSize of the debuggee
    private int OBJ_ID_SIZE;

    private long BOOLEAN_BASE_OFFSET;
    private long BYTE_BASE_OFFSET;
    private long CHAR_BASE_OFFSET;
    private long SHORT_BASE_OFFSET;
    private long INT_BASE_OFFSET;
    private long LONG_BASE_OFFSET;
    private long FLOAT_BASE_OFFSET;
    private long DOUBLE_BASE_OFFSET;
    private long OBJECT_BASE_OFFSET;

    private long BOOLEAN_SIZE;
    private long BYTE_SIZE;
    private long CHAR_SIZE;
    private long SHORT_SIZE;
    private long INT_SIZE;
    private long LONG_SIZE;
    private long FLOAT_SIZE;
    private long DOUBLE_SIZE;

    private static class ClassData {
        int instSize;
        List fields;
        ClassData(int instSize, List fields) {
            this.instSize = instSize;
            this.fields = fields;
        }
    }

    private Map classDataCache = new HashMap(); // <InstanceKlass, ClassData>
}
