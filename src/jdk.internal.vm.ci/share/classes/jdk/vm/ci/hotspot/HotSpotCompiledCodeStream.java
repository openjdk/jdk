/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.CALLSITE_TARGET_VALUE;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.CONCRETE_METHOD;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.CONCRETE_SUBTYPE;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.ILLEGAL;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.JOBJECT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.LEAF_TYPE;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.NO_FINALIZABLE_SUBCLASS;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.NULL_CONSTANT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.OBJECT_ID;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.OBJECT_ID2;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_JOBJECT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_KLASS;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_METHOD;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_NARROW_JOBJECT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_NARROW_KLASS;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_NARROW_OBJECT_ID;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_NARROW_OBJECT_ID2;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_OBJECT_ID;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PATCH_OBJECT_ID2;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PRIMITIVE4;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PRIMITIVE8;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.PRIMITIVE_0;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.RAW_CONSTANT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.REGISTER_NARROW_OOP;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.REGISTER_OOP;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.REGISTER_PRIMITIVE;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.REGISTER_VECTOR;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_CALL;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_DATA_PATCH;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_EXCEPTION_HANDLER;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_FOREIGN_CALL;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_FOREIGN_CALL_NO_DEBUG_INFO;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_IMPLICIT_EXCEPTION;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_IMPLICIT_EXCEPTION_DISPATCH;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_INFOPOINT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_MARK;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.SITE_SAFEPOINT;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.STACK_SLOT_NARROW_OOP;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.STACK_SLOT_OOP;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.STACK_SLOT_PRIMITIVE;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.STACK_SLOT_VECTOR;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.VIRTUAL_OBJECT_ID;
import static jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag.VIRTUAL_OBJECT_ID2;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Location;
import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterSaveLayout;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.ExceptionHandler;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompiledCode.Comment;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.Option;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.Assumptions.CallSiteTargetValue;
import jdk.vm.ci.meta.Assumptions.ConcreteMethod;
import jdk.vm.ci.meta.Assumptions.ConcreteSubtype;
import jdk.vm.ci.meta.Assumptions.LeafType;
import jdk.vm.ci.meta.Assumptions.NoFinalizableSubclass;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.services.Services;

/**
 * Serializes {@link HotSpotCompiledCode} to a list linked native memory chunks. Each chunk has the
 * following layout:
 *
 * <pre>
 *   | word |  4   |<------  size -------->|             |
 *   +------+------+-----------------------+-------------+
 *   | next | size |       used data       | unused data |
 *   +------+------+-----------------------+-------------+
 *
 *   |<----------- chunkSize --------------------------->|
 *   |<-- HEADER ->|
 * </pre>
 *
 * Each chunk is twice as large as its predecessor. See {@link #ensureCapacity(int)}.
 *
 * @see Option#DumpSerializedCode
 * @see Option#CodeSerializationTypeInfo
 */
final class HotSpotCompiledCodeStream implements AutoCloseable {

    // 24K is sufficient for most compilations.
    private static final int INITIAL_CHUNK_SIZE = 24 * 1024;
    private static final int HEADER = Unsafe.ADDRESS_SIZE + 4;

    // @formatter:off
    // HotSpotCompiledCode flags.
    // Defined by HotSpotCompiledCodeFlags enum in jvmciCodeInstaller.hpp.
    private static final int IS_NMETHOD            = c("HCC_IS_NMETHOD");
    private static final int HAS_ASSUMPTIONS       = c("HCC_HAS_ASSUMPTIONS");
    private static final int HAS_METHODS           = c("HCC_HAS_METHODS");
    private static final int HAS_DEOPT_RESCUE_SLOT = c("HCC_HAS_DEOPT_RESCUE_SLOT");
    private static final int HAS_COMMENTS          = c("HCC_HAS_COMMENTS");

    // DebugInfo flags.
    // Defined by DebugInfoFlags enum in jvmciCodeInstaller.hpp.
    private static final int HAS_REFERENCE_MAP    = c("DI_HAS_REFERENCE_MAP");
    private static final int HAS_CALLEE_SAVE_INFO = c("DI_HAS_CALLEE_SAVE_INFO");
    private static final int HAS_FRAMES           = c("DI_HAS_FRAMES");

    // BytecodeFrame flags
    // Defined by DebugInfoFrameFlags enum in jvmciCodeInstaller.hpp.
    private static final int HAS_LOCALS        = c("DIF_HAS_LOCALS");
    private static final int HAS_STACK         = c("DIF_HAS_STACK");
    private static final int HAS_LOCKS         = c("DIF_HAS_LOCKS");
    private static final int DURING_CALL       = c("DIF_DURING_CALL");
    private static final int RETHROW_EXCEPTION = c("DIF_RETHROW_EXCEPTION");
    // @formatter:on

    // Sentinel value in a DebugInfo stream denoting no register.
    private static final int NO_REGISTER = c("NO_REGISTER");

    enum Tag {
        ILLEGAL,
        REGISTER_PRIMITIVE,
        REGISTER_OOP,
        REGISTER_NARROW_OOP,
        REGISTER_VECTOR,
        STACK_SLOT_PRIMITIVE,
        STACK_SLOT_OOP,
        STACK_SLOT_NARROW_OOP,
        STACK_SLOT_VECTOR,
        VIRTUAL_OBJECT_ID,
        VIRTUAL_OBJECT_ID2,
        NULL_CONSTANT,
        RAW_CONSTANT,
        PRIMITIVE_0,
        PRIMITIVE4,
        PRIMITIVE8,
        JOBJECT,
        OBJECT_ID,
        OBJECT_ID2,

        NO_FINALIZABLE_SUBCLASS,
        CONCRETE_SUBTYPE,
        LEAF_TYPE,
        CONCRETE_METHOD,
        CALLSITE_TARGET_VALUE,

        PATCH_OBJECT_ID,
        PATCH_OBJECT_ID2,
        PATCH_NARROW_OBJECT_ID,
        PATCH_NARROW_OBJECT_ID2,
        PATCH_JOBJECT,
        PATCH_NARROW_JOBJECT,
        PATCH_KLASS,
        PATCH_NARROW_KLASS,
        PATCH_METHOD,
        PATCH_DATA_SECTION_REFERENCE,

        SITE_CALL,
        SITE_FOREIGN_CALL,
        SITE_FOREIGN_CALL_NO_DEBUG_INFO,
        SITE_SAFEPOINT,
        SITE_INFOPOINT,
        SITE_IMPLICIT_EXCEPTION,
        SITE_IMPLICIT_EXCEPTION_DISPATCH,
        SITE_MARK,
        SITE_DATA_PATCH,
        SITE_EXCEPTION_HANDLER;

        Tag() {
            int expect = ordinal();
            int actual = c(name());
            if (expect != actual) {
                throw new JVMCIError("%s: expected %d, got %d", name(), expect, actual);
            }
        }
    }

    private final Unsafe unsafe = UnsafeAccess.UNSAFE;
    private final HotSpotJVMCIRuntime runtime;

    /**
     * Specifies if the name and size of each data element is written to the buffer.
     */
    private final boolean withTypeInfo;

    /**
     * Lazily initialized string from {@link HotSpotCompiledCode#name} or
     * {@link HotSpotCompiledNmethod#method}.
     */
    private Object codeDesc;

    /**
     * Constant pool for {@linkplain DirectHotSpotObjectConstantImpl direct} object references.
     */

    /**
     * Alternative to using {@link IdentityHashMap} which would require dealing with the
     * {@link IdentityHashMap#NULL_KEY} constant.
     */
    static class IdentityBox {
        Object obj;

        IdentityBox(Object obj) {
            this.obj = obj;
        }

        @Override
        public boolean equals(Object other) {
            IdentityBox that = (IdentityBox) other;
            return that.obj == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(obj);
        }
    }

    private HashMap<IdentityBox, Integer> objects;
    final Object[] objectPool;

    /**
     * Head and current chunk.
     */
    final long headChunk;
    private long currentChunk;

    /**
     * Size of current chunk.
     */
    private int currentChunkSize;

    /**
     * Index of current chunk.
     */
    private int currentChunkIndex;

    /**
     * Insertion position in current chunk.
     */
    private int currentChunkOffset;

    /**
     * Nanoseconds spent in {@link HotSpotCompiledCodeStream#HotSpotCompiledCodeStream}.
     */
    long timeNS;

    private JVMCIError error(String format, Object... args) {
        String prefix = String.format("%s[offset=%d]", codeDesc(), getTotalDataSize());
        throw new JVMCIError(prefix + ": " + format, args);
    }

    /**
     * Gets the size of payload data in the buffer.
     */
    private int getTotalDataSize() {
        int offset = currentChunkOffset - HEADER;
        for (long chunk = headChunk; chunk != currentChunk; chunk = getChunkNext(chunk)) {
            offset += getDataSize(chunk);
        }
        return offset;
    }

    /**
     * Reads the size of the payload in {@code chunk}.
     */
    private int getDataSize(long chunk) {
        int sizeOffset = Unsafe.ADDRESS_SIZE;
        return unsafe.getInt(chunk + sizeOffset);
    }

    /**
     * Writes the size of the payload in {@code chunk}.
     */
    private void setDataSize(long chunk, int size) {
        int sizeOffset = Unsafe.ADDRESS_SIZE;
        unsafe.putInt(chunk + sizeOffset, size);
    }

    /**
     * Reads the pointer in chunk pointing to the next chunk in the list.
     */
    private long getChunkNext(long chunk) {
        return unsafe.getAddress(chunk);
    }

    /**
     * Writes the pointer in chunk pointing to the next chunk in the list.
     */
    private void setChunkNext(long chunk, long next) {
        unsafe.putAddress(chunk, next);
    }

    /**
     * Ensures there is capacity for appending {@code toWrite} additional bytes.
     */
    private void ensureCapacity(int toWrite) {
        if (currentChunkOffset + toWrite > currentChunkSize) {
            // Save current chunk data size
            int dataSize = currentChunkOffset - HEADER;
            setDataSize(currentChunk, dataSize);

            // Allocate new chunk and link it. Each chunk is
            // at least as twice as large as its predecessor.
            int nextChunkSize = currentChunkSize * 2;
            if (nextChunkSize < toWrite + HEADER) {
                nextChunkSize = toWrite + HEADER;
            }
            long nextChunk = unsafe.allocateMemory(nextChunkSize);
            setChunkNext(currentChunk, nextChunk);
            setChunkNext(nextChunk, 0);

            // Make new chunk current
            currentChunk = nextChunk;
            currentChunkSize = nextChunkSize;
            currentChunkOffset = HEADER;
            currentChunkIndex++;
        }
    }

    /**
     * Emits type info for a write to the buffer and ensures there's sufficient capacity.
     *
     * @param name name of data element to be written
     * @param sizeInBytes the size of the data element to be written
     */
    private void beforeWrite(String name, int sizeInBytes) {
        emitType(name, sizeInBytes);
        ensureCapacity(sizeInBytes);
    }

    /**
     * Emits the name and size in bytes of a data element if type info is enabled.
     *
     * @param name
     * @param sizeInBytes
     */
    private void emitType(String name, int sizeInBytes) {
        if (withTypeInfo) {
            int len = name.length();
            if ((len & 0xFF) != len) {
                // Length must be <= 0xFF
                throw error("Data element label is too long (%d): %s", len, name);
            }
            if (sizeInBytes < 0 || sizeInBytes > 8) {
                throw error("Data element size is not between 0 and 8 inclusive: %d", sizeInBytes);
            }
            int toWrite = 1 + 1 + len + 1;
            ensureCapacity(toWrite);

            // sizeInBytes
            unsafe.putByte(currentChunk + currentChunkOffset, (byte) sizeInBytes);
            currentChunkOffset++;

            // length
            unsafe.putByte(currentChunk + currentChunkOffset, (byte) len);
            currentChunkOffset++;

            // body
            for (int i = 0; i < len; i++) {
                int c = name.charAt(i);
                if (c >= 0x80 || c == 0) {
                    throw error("label contains non-ascii char at %d: %s", i, name);
                }
                unsafe.putByte(currentChunk + currentChunkOffset, (byte) c);
                currentChunkOffset++;
            }
        }
    }

    private static boolean isU1(int value) {
        return (value & 0xFF) == value;
    }

    private void writeU1(String name, int value) {
        if (!isU1(value)) {
            throw error("value not a u1: " + value);
        }
        byte b = (byte) value;
        beforeWrite(name, 1);
        unsafe.putByte(currentChunk + currentChunkOffset, b);
        currentChunkOffset++;
    }

    private void writeBoolean(String name, boolean value) {
        writeU1(name, value ? 1 : 0);
    }

    private void writeInt(String name, int value) {
        beforeWrite(name, 4);
        unsafe.putInt(currentChunk + currentChunkOffset, value);
        currentChunkOffset += 4;
    }

    private void rawWriteU2(String name, int value) {
        beforeWrite(name, 2);
        char ch = (char) value;
        unsafe.putChar(currentChunk + currentChunkOffset, ch);
        currentChunkOffset += 2;
    }

    private void writeU2(String name, int value) {
        if (value < Character.MIN_VALUE || value > Character.MAX_VALUE) {
            throw error("value not a u2: " + value);
        }
        rawWriteU2(name, value);
    }

    private void writeS2(String name, int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw error("value not an s2: " + value);
        }
        rawWriteU2(name, value);
    }

    private void writeLong(String name, long value) {
        beforeWrite(name, 8);
        unsafe.putLong(currentChunk + currentChunkOffset, value);
        currentChunkOffset += 8;
    }

    /**
     * Writes the UTF8 bytes for {@code value} to the stream followed by a 0.
     */
    private void writeUTF8(String name, String value) {
        if (value == null) {
            writeInt(name, -1);
            return;
        }

        byte[] utf = value.getBytes(StandardCharsets.UTF_8);

        emitType(name, 4);

        int toWrite = 4 + utf.length + 1;
        ensureCapacity(toWrite);

        // length
        unsafe.putInt(currentChunk + currentChunkOffset, utf.length);
        currentChunkOffset += 4;

        // body
        for (int i = 0; i < utf.length; i++) {
            byte b = utf[i];
            unsafe.putByte(currentChunk + currentChunkOffset, b);
            currentChunkOffset++;
        }

        // trailing 0
        unsafe.putByte(currentChunk + currentChunkOffset, (byte) 0);
        currentChunkOffset++;
    }

    private String codeDesc() {
        if (codeDesc instanceof ResolvedJavaMethod) {
            codeDesc = ((ResolvedJavaMethod) codeDesc).format("%H.%n(%p)");
        } else if (codeDesc == null) {
            codeDesc = "<unknown>";
        }
        return codeDesc.toString();
    }

    /**
     * Serializes {@code}.
     *
     * @param code the object to serialize
     * @param withTypeInfo see {@link Option#CodeSerializationTypeInfo}
     * @param withComments include {@link HotSpotCompiledCode#comments} in the stream
     * @param withMethods include {@link HotSpotCompiledCode#methods} in the stream
     */
    HotSpotCompiledCodeStream(HotSpotCompiledCode code, boolean withTypeInfo, boolean withComments, boolean withMethods) {
        long start = System.nanoTime();
        this.currentChunkSize = INITIAL_CHUNK_SIZE;
        this.headChunk = unsafe.allocateMemory(currentChunkSize);
        this.currentChunk = headChunk;
        this.currentChunkOffset = HEADER;
        setChunkNext(currentChunk, 0);
        setDataSize(currentChunk, 0);

        this.runtime = HotSpotJVMCIRuntime.runtime();
        this.withTypeInfo = withTypeInfo;

        ResolvedJavaMethod[] methods = withMethods ? code.methods : null;
        Assumption[] assumptions = code.assumptions;
        StackSlot deoptRescueSlot = code.deoptRescueSlot;
        Comment[] comments = withComments ? code.comments : null;

        String name = code.name;
        codeDesc = name;
        HotSpotCompiledNmethod nmethod;
        if (code instanceof HotSpotCompiledNmethod) {
            nmethod = (HotSpotCompiledNmethod) code;
            if (codeDesc == null) {
                codeDesc = nmethod.method;
            }
        } else {
            nmethod = null;
        }

        // @formatter:off
        int flags = setIf(IS_NMETHOD, nmethod != null) |
                    setIf(HAS_METHODS, nmethod != null && methods != null && methods.length != 0 ) |
                    setIf(HAS_ASSUMPTIONS, assumptions) |
                    setIf(HAS_DEOPT_RESCUE_SLOT, deoptRescueSlot != null) |
                    setIf(HAS_COMMENTS, comments);
        // @formatter:on

        writeU1("code:flags", flags);
        writeUTF8("name", name);
        if (nmethod != null) {
            writeMethod("method", nmethod.method);
            writeInt("entryBCI", nmethod.entryBCI);
            writeLong("compileState", nmethod.compileState);
            writeBoolean("hasUnsafeAccess", nmethod.hasUnsafeAccess);
            writeBoolean("hasScopedAccess", nmethod.hasScopedAccess());
            writeInt("id", nmethod.id);
        }

        if (isSet(flags, HAS_ASSUMPTIONS)) {
            writeAssumptions(assumptions);
        }
        if (isSet(flags, HAS_METHODS)) {
            writeU2("methods:length", methods.length);
            for (ResolvedJavaMethod method : methods) {
                writeMethod("method", method);
            }
        }

        writeInt("sites:length", code.sites.length);
        writeInt("targetCodeSize", code.targetCodeSize);
        writeInt("totalFrameSize", code.totalFrameSize);
        if (isSet(flags, HAS_DEOPT_RESCUE_SLOT)) {
            writeS2("offset", deoptRescueSlot.getRawOffset());
            writeBoolean("addRawFrameSize", deoptRescueSlot.getRawAddFrameSize());
        }
        writeInt("dataSectionSize", code.dataSection.length);
        writeU1("dataSectionAlignment", code.dataSectionAlignment);

        writeStubCounts(code);
        writeDataSectionPatches(code.dataSectionPatches);
        writeSites(code);

        if (isSet(flags, HAS_COMMENTS)) {
            writeU2("comments:length", comments.length);
            for (Comment c : comments) {
                writeInt("comment:pcOffset", c.pcOffset);
                writeUTF8("comment:text", c.text);
            }
        }

        // Finalize current (and last) chunk
        int dataSize = currentChunkOffset - HEADER;
        setDataSize(currentChunk, dataSize);

        objectPool = !Services.IS_IN_NATIVE_IMAGE ? finalizeObjectPool() : null;

        maybeDump(name, nmethod);

        this.timeNS = System.nanoTime() - start;
    }

    /**
     * Creates the pool for {@link DirectHotSpotObjectConstantImpl} values written to the stream.
     */
    private Object[] finalizeObjectPool() throws JVMCIError {
        if (objects != null) {
            Object[] pool = new Object[objects.size()];
            for (Map.Entry<IdentityBox, Integer> e : objects.entrySet()) {
                int id = e.getValue();
                Object object = e.getKey().obj;
                if (object == null) {
                    throw error("unexpected null in object pool at %d - map is %s", id, objects);
                }
                pool[id] = object;
            }
            return pool;
        } else {
            return null;
        }
    }

    /**
     * Determines if {@code name} or {@code method} are matched by
     * {@link Option#DumpSerializedCode}.
     *
     * @return the matched value or null if no match was made
     */
    private static String shouldDump(String name, HotSpotCompiledNmethod nmethod) {
        String filter = Option.DumpSerializedCode.getString();
        if (filter == null) {
            return null;
        }
        if (name != null && name.contains(filter)) {
            return name;
        }
        if (nmethod != null) {
            String fqn = nmethod.method.format("%H.%n(%p)");
            if (fqn.contains(filter)) {
                return fqn;
            }
        }
        return null;
    }

    /**
     * Dumps the buffer to TTY if {@code name} or {@code method} are matched by
     * {@link Option#DumpSerializedCode}.
     */
    private void maybeDump(String name, HotSpotCompiledNmethod nmethod) {
        String dumpName = shouldDump(name, nmethod);
        if (dumpName != null) {
            dump(dumpName);
        }
    }

    private void dump(String dumpName) {
        int dataSize;
        PrintStream out = new PrintStream(runtime.getLogStream());
        out.printf("Dumping serialized HotSpotCompiledMethod data for %s (head: 0x%016x, chunks: %d, total data size:%d):%n",
                        dumpName, headChunk, currentChunkIndex + 1, getTotalDataSize());
        int chunkIndex = 0;
        for (long c = headChunk; c != 0; c = getChunkNext(c)) {
            long data0 = c + HEADER;
            dataSize = getDataSize(c);
            byte[] data = new byte[dataSize];
            unsafe.copyMemory(null, data0, data, Unsafe.ARRAY_BYTE_BASE_OFFSET, dataSize);
            out.printf("[CHUNK %d: address=0x%016x, data=0x%016x:0x%016x, data size=%d]%n",
                            chunkIndex, c, data0, data0 + dataSize, dataSize);
            hexdump(out, data0, data);
            chunkIndex++;
        }
    }

    private static void hexdump(PrintStream out, long address, byte[] data) {
        int col = 0;
        for (int pos = 0; pos < data.length; pos++) {
            if (col % 16 == 0) {
                out.printf("0x%016x:", address + pos);
            }
            if (col % 2 == 0) {
                out.print(' ');
            }
            if (pos < data.length) {
                byte b = data[pos];
                char ch = (char) ((char) b & 0xff);
                out.printf("%02X", (int) ch);
            } else {
                out.print("  ");
            }
            if ((col + 1) % 16 == 0) {
                out.print("  ");
                for (int j = pos - 15; j <= pos; ++j) {
                    byte b = data[j];
                    char ch = (char) ((char) b & 0xff);
                    out.print(ch >= 32 && ch <= 126 ? ch : '.');
                }
                out.println();
            }
            col++;
        }
        out.println();
    }

    @Override
    public void close() {
        for (long c = headChunk; c != 0;) {
            long next = getChunkNext(c);
            unsafe.freeMemory(c);
            c = next;
        }
    }

    private void writeSites(HotSpotCompiledCode code) {
        Site[] sites = code.sites;
        for (Site site : sites) {
            writeInt("site:pcOffset", site.pcOffset);
            if (site instanceof Call) {
                Call call = (Call) site;
                DebugInfo debugInfo = call.debugInfo;
                InvokeTarget target = call.target;
                if (target instanceof HotSpotForeignCallTarget) {
                    HotSpotForeignCallTarget foreignCall = (HotSpotForeignCallTarget) target;
                    writeTag(debugInfo == null ? SITE_FOREIGN_CALL_NO_DEBUG_INFO : SITE_FOREIGN_CALL);
                    writeLong("target", foreignCall.address);
                    if (debugInfo != null) {
                        writeDebugInfo(debugInfo, true);
                    }
                } else {
                    if (debugInfo == null) {
                        throw error("debug info expected at call %s", call);
                    }
                    writeTag(SITE_CALL);
                    ResolvedJavaMethod method = (ResolvedJavaMethod) target;
                    writeMethod("target", method);
                    writeBoolean("direct", call.direct);
                    writeDebugInfo(debugInfo, true);
                }
            } else if (site instanceof Infopoint) {
                Infopoint info = (Infopoint) site;
                InfopointReason reason = info.reason;
                DebugInfo debugInfo = info.debugInfo;
                if (debugInfo == null) {
                    throw error("debug info expected at infopoint %s", info);
                }
                Tag tag;
                switch (reason) {
                    case SAFEPOINT:
                        tag = SITE_SAFEPOINT;
                        break;
                    case IMPLICIT_EXCEPTION:
                        tag = info instanceof ImplicitExceptionDispatch ? SITE_IMPLICIT_EXCEPTION_DISPATCH : SITE_IMPLICIT_EXCEPTION;
                        break;
                    case CALL:
                        throw error("only %s objects expected to have CALL reason: %s", Call.class.getName(), info);
                    default:
                        tag = SITE_INFOPOINT;
                        break;
                }
                writeTag(tag);
                boolean fullInfo = reason == InfopointReason.SAFEPOINT || reason == InfopointReason.IMPLICIT_EXCEPTION;
                writeDebugInfo(debugInfo, fullInfo);
                if (tag == SITE_IMPLICIT_EXCEPTION_DISPATCH) {
                    int dispatchOffset = ((ImplicitExceptionDispatch) info).dispatchOffset;
                    writeInt("dispatchOffset", dispatchOffset);
                }

            } else if (site instanceof DataPatch) {
                writeTag(SITE_DATA_PATCH);
                DataPatch patch = (DataPatch) site;
                writeDataPatch(patch, code);
            } else if (site instanceof Mark) {
                Mark mark = (Mark) site;
                writeTag(SITE_MARK);
                writeU1("mark:id", (int) mark.id);
            } else if (site instanceof ExceptionHandler) {
                ExceptionHandler handler = (ExceptionHandler) site;
                writeTag(SITE_EXCEPTION_HANDLER);
                writeInt("site:handlerPos", handler.handlerPos);
            }
        }
    }

    private void writeDataSectionPatches(DataPatch[] dataSectionPatches) {
        writeU2("dataSectionPatches:length", dataSectionPatches.length);
        for (DataPatch dp : dataSectionPatches) {
            Reference ref = dp.reference;
            if (!(ref instanceof ConstantReference)) {
                throw error("invalid patch in data section: %s", dp);
            }
            writeInt("patch:pcOffset", dp.pcOffset);
            ConstantReference cref = (ConstantReference) ref;
            VMConstant con = cref.getConstant();
            if (con instanceof HotSpotMetaspaceConstantImpl) {
                writeMetaspaceConstantPatch((HotSpotMetaspaceConstantImpl) con);
            } else {
                if (!(con instanceof HotSpotObjectConstantImpl)) {
                    throw error("invalid constant in data section: %s", con);
                }
                writeOopConstantPatch((HotSpotObjectConstantImpl) con);
            }
        }
    }

    private void writeDataPatch(DataPatch patch, HotSpotCompiledCode code) {
        Reference ref = patch.reference;
        if (ref instanceof ConstantReference) {
            ConstantReference cref = (ConstantReference) ref;
            VMConstant con = cref.getConstant();
            if (con instanceof HotSpotObjectConstantImpl) {
                writeOopConstantPatch((HotSpotObjectConstantImpl) con);
            } else if (con instanceof HotSpotMetaspaceConstantImpl) {
                writeMetaspaceConstantPatch((HotSpotMetaspaceConstantImpl) con);
            } else {
                throw error("unexpected constant patch: %s", con);
            }
        } else if (ref instanceof DataSectionReference) {
            DataSectionReference dsref = (DataSectionReference) ref;
            int dataOffset = dsref.getOffset();
            if (dataOffset < 0 || dataOffset >= code.dataSection.length) {
                throw error("data offset 0x%X points outside data section (size 0x%X)", dataOffset, code.dataSection.length);
            }
            writeTag(Tag.PATCH_DATA_SECTION_REFERENCE);
            writeInt("data:offset", dataOffset);
        } else {
            throw error("unexpected data reference patch: %s", ref);
        }
    }

    private void writeOopConstantPatch(HotSpotObjectConstantImpl con) {
        if (con instanceof DirectHotSpotObjectConstantImpl) {
            if (Services.IS_IN_NATIVE_IMAGE) {
                throw error("Direct object constant reached the backend: %s", con);
            }
            DirectHotSpotObjectConstantImpl obj = (DirectHotSpotObjectConstantImpl) con;
            if (!obj.isCompressed()) {
                writeObjectID(obj, PATCH_OBJECT_ID, PATCH_OBJECT_ID2);
            } else {
                writeObjectID(obj, PATCH_NARROW_OBJECT_ID, PATCH_NARROW_OBJECT_ID2);
            }
        } else {
            IndirectHotSpotObjectConstantImpl obj = (IndirectHotSpotObjectConstantImpl) con;
            if (!obj.isCompressed()) {
                writeTag(PATCH_JOBJECT);
            } else {
                writeTag(PATCH_NARROW_JOBJECT);
            }
            writeLong("jobject", obj.getHandle());
        }
    }

    private void writeMetaspaceConstantPatch(HotSpotMetaspaceConstantImpl metaspaceCon) throws JVMCIError {
        if (metaspaceCon.isCompressed()) {
            writeTag(PATCH_NARROW_KLASS);
            HotSpotResolvedObjectType type = metaspaceCon.asResolvedJavaType();
            if (type == null) {
                throw error("unexpected compressed pointer: %s", metaspaceCon);
            }
            writeObjectType("patch:klass", type);
        } else {
            HotSpotResolvedObjectType type = metaspaceCon.asResolvedJavaType();
            HotSpotResolvedJavaMethod method = metaspaceCon.asResolvedJavaMethod();
            if (type != null) {
                writeTag(PATCH_KLASS);
                writeObjectType("patch:klass", type);
            } else {
                if (method == null) {
                    throw error("unexpected metadata reference: %s", metaspaceCon);
                }
                writeTag(PATCH_METHOD);
                writeMethod("patch:method", method);
            }
        }
    }

    private static final int MARK_INVOKEINTERFACE = c("INVOKEINTERFACE");
    private static final int MARK_INVOKEVIRTUAL = c("INVOKEVIRTUAL");
    private static final int MARK_INVOKESTATIC = c("INVOKESTATIC");
    private static final int MARK_INVOKESPECIAL = c("INVOKESPECIAL");

    private static int c(String name) {
        return HotSpotJVMCIRuntime.runtime().config.getConstant("CodeInstaller::" + name, Integer.class);
    }

    private void writeStubCounts(HotSpotCompiledCode code) {
        int numStaticCallStubs = 0;
        int numTrampolineStubs = 0;
        for (Site site : code.sites) {
            if (site instanceof Mark) {
                Mark mark = (Mark) site;
                if (!(mark.id instanceof Integer)) {
                    error("Mark id must be Integer, not %s", mark.id.getClass().getName());
                }
                int id = (Integer) mark.id;
                if (id == MARK_INVOKEINTERFACE || id == MARK_INVOKEVIRTUAL) {
                    numTrampolineStubs++;
                } else if (id == MARK_INVOKESTATIC || id == MARK_INVOKESPECIAL) {
                    numStaticCallStubs++;
                    numTrampolineStubs++;
                }
            }
        }
        writeU2("numStaticCallStubs", numStaticCallStubs);
        writeU2("numTrampolineStubs", numTrampolineStubs);
    }

    private void writeAssumptions(Assumption[] assumptions) {
        writeU2("assumptions:length", assumptions.length);
        for (Assumption a : assumptions) {
            if (a instanceof NoFinalizableSubclass) {
                writeTag(NO_FINALIZABLE_SUBCLASS);
                writeObjectType("receiverType", ((NoFinalizableSubclass) a).receiverType);
            } else if (a instanceof ConcreteSubtype) {
                writeTag(CONCRETE_SUBTYPE);
                ConcreteSubtype cs = (ConcreteSubtype) a;
                writeObjectType("context", cs.context);
                writeObjectType("subtype", cs.subtype);
            } else if (a instanceof LeafType) {
                writeTag(LEAF_TYPE);
                writeObjectType("context", ((LeafType) a).context);
            } else if (a instanceof ConcreteMethod) {
                writeTag(CONCRETE_METHOD);
                ConcreteMethod cm = (ConcreteMethod) a;
                writeObjectType("context", cm.context);
                writeMethod("impl", cm.impl);
            } else if (a instanceof CallSiteTargetValue) {
                writeTag(CALLSITE_TARGET_VALUE);
                CallSiteTargetValue cs = (CallSiteTargetValue) a;
                writeJavaValue(cs.callSite, JavaKind.Object);
                writeJavaValue(cs.methodHandle, JavaKind.Object);
            } else {
                throw error("unexpected assumption %s", a);
            }
        }
    }

    private void writeDebugInfo(DebugInfo debugInfo, boolean fullInfo) {
        ReferenceMap referenceMap = debugInfo.getReferenceMap();
        RegisterSaveLayout calleeSaveInfo = debugInfo.getCalleeSaveInfo();
        BytecodePosition bytecodePosition = debugInfo.getBytecodePosition();

        int flags = 0;
        if (bytecodePosition != null) {
            flags |= HAS_FRAMES;
        }
        if (fullInfo) {
            if (referenceMap == null) {
                throw error("reference map is null");
            }
            flags |= HAS_REFERENCE_MAP;
            if (calleeSaveInfo != null) {
                flags |= HAS_CALLEE_SAVE_INFO;
            }
        }
        writeU1("debugInfo:flags", flags);

        if (fullInfo) {
            writeReferenceMap(referenceMap);
            if (calleeSaveInfo != null) {
                writeCalleeSaveInfo(calleeSaveInfo);
            }
            writeVirtualObjects(debugInfo.getVirtualObjectMapping());
        }
        if (bytecodePosition != null) {
            writeFrame(bytecodePosition, fullInfo, 0);
        }
    }

    private void writeVirtualObjects(VirtualObject[] virtualObjects) {
        int length = virtualObjects != null ? virtualObjects.length : 0;
        writeU2("virtualObjects:length", length);
        for (int i = 0; i < length; i++) {
            VirtualObject vo = virtualObjects[i];
            writeObjectType("type", vo.getType());
            writeBoolean("isAutoBox", vo.isAutoBox());
        }
        for (int i = 0; i < length; i++) {
            VirtualObject vo = virtualObjects[i];
            int id = vo.getId();
            if (id != i) {
                throw error("duplicate virtual object id %d", id);
            }
            JavaValue[] values = vo.getValues();
            int valuesLength = values != null ? values.length : 0;
            writeU2("values:length", valuesLength);
            if (valuesLength != 0) {
                for (int j = 0; j < values.length; j++) {
                    writeBasicType(vo.getSlotKind(j));
                    JavaValue jv = values[j];
                    writeJavaValue(jv, vo.getSlotKind(j));
                }
            }
        }
    }

    private void writeCalleeSaveInfo(RegisterSaveLayout calleeSaveInfo) {
        Map<Register, Integer> map = calleeSaveInfo.registersToSlots(false);
        writeU2("calleeSaveInfo:length", map.size());
        for (Map.Entry<Register, Integer> e : map.entrySet()) {
            writeRegister(e.getKey());
            writeU2("slot", e.getValue());
        }
    }

    private void writeTag(Tag tag) {
        writeU1("tag", (byte) tag.ordinal());
    }

    private void writeBasicType(JavaKind kind) {
        writeU1("basicType", (byte) kind.getBasicType());
    }

    private void writeObjectType(String name, ResolvedJavaType type) {
        HotSpotResolvedObjectTypeImpl objType = (HotSpotResolvedObjectTypeImpl) type;
        writeLong(name, objType.getKlassPointer());
    }

    private void writeMethod(String name, ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethodImpl impl = (HotSpotResolvedJavaMethodImpl) method;
        writeLong(name, impl.getMethodPointer());
    }

    private boolean isNarrowOop(Value oopValue) {
        return oopValue.getPlatformKind() != runtime.getHostJVMCIBackend().getTarget().arch.getWordKind();
    }

    private boolean isVector(Value value) {
        return value.getPlatformKind().getVectorLength() > 1;
    }

    private void writeJavaValue(JavaValue value, JavaKind kind) {
        if (value == Value.ILLEGAL) {
            writeTag(ILLEGAL);
        } else if (value == JavaConstant.NULL_POINTER || value instanceof HotSpotCompressedNullConstant) {
            if (JavaKind.Object != kind) {
                throw error("object constant (%s) kind expected to be Object, got %s", value, kind);
            }
            writeTag(NULL_CONSTANT);
        } else if (value instanceof RegisterValue) {
            RegisterValue reg = (RegisterValue) value;
            Tag tag;
            if (kind == JavaKind.Object) {
                if (isVector(reg)) {
                    tag = REGISTER_VECTOR;
                } else {
                    tag = isNarrowOop(reg) ? REGISTER_NARROW_OOP : REGISTER_OOP;
                }
            } else {
                tag = REGISTER_PRIMITIVE;
            }
            writeTag(tag);
            writeRegister(reg.getRegister());
        } else if (value instanceof StackSlot) {
            StackSlot slot = (StackSlot) value;
            Tag tag;
            if (kind == JavaKind.Object) {
                if (isVector(slot)) {
                    tag = STACK_SLOT_VECTOR;
                } else {
                    tag = isNarrowOop(slot) ? STACK_SLOT_NARROW_OOP : STACK_SLOT_OOP;
                }
            } else {
                tag = STACK_SLOT_PRIMITIVE;
            }
            writeTag(tag);
            writeS2("offset", slot.getRawOffset());
            writeBoolean("addRawFrameSize", slot.getRawAddFrameSize());
        } else if (value instanceof VirtualObject) {
            VirtualObject vo = (VirtualObject) value;
            if (kind != JavaKind.Object) {
                throw error("virtual object kind must be Object, not %s", kind);
            }
            int id = vo.getId();
            if (isU1(id)) {
                writeTag(VIRTUAL_OBJECT_ID);
                writeU1("id", id);
            } else {
                writeTag(VIRTUAL_OBJECT_ID2);
                writeU2("id:2", id);
            }
        } else if (value instanceof RawConstant) {
            RawConstant prim = (RawConstant) value;
            writeTag(RAW_CONSTANT);
            writeLong("primitive", prim.getRawValue());
        } else if (value instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) value;
            if (prim.getJavaKind() != kind) {
                throw error("primitive constant (%s) kind expected to be %s, got %s", prim, kind, prim.getJavaKind());
            }
            long raw = prim.getRawValue();
            if (raw == 0) {
                writeTag(PRIMITIVE_0);
            } else if (raw >= Integer.MIN_VALUE && raw <= Integer.MAX_VALUE) {
                writeTag(PRIMITIVE4);
                int rawInt = (int) raw;
                writeInt("primitive4", rawInt);
            } else {
                writeTag(PRIMITIVE8);
                writeLong("primitive8", raw);
            }
        } else if (value instanceof IndirectHotSpotObjectConstantImpl) {
            if (JavaKind.Object != kind) {
                throw error("object constant (%s) kind expected to be Object, got %s", value, kind);
            }
            IndirectHotSpotObjectConstantImpl obj = (IndirectHotSpotObjectConstantImpl) value;
            writeTag(JOBJECT);
            writeLong("jobject", obj.getHandle());
        } else if (value instanceof DirectHotSpotObjectConstantImpl) {
            if (JavaKind.Object != kind) {
                throw error("object constant (%s) kind expected to be Object, got %s", value, kind);
            }
            writeObjectID((DirectHotSpotObjectConstantImpl) value, OBJECT_ID, OBJECT_ID2);
        } else {
            throw error("unsupported type: " + value.getClass());
        }
    }

    private int writeObjectID(DirectHotSpotObjectConstantImpl value, Tag u1Tag, Tag u2Tag) {
        if (Services.IS_IN_NATIVE_IMAGE) {
            throw error("SVM object value cannot be installed in HotSpot code cache: %s", value);
        }
        Object obj = value.object;
        if (obj == null) {
            throw error("direct object should not be null");
        }
        IdentityBox key = new IdentityBox(obj);
        if (objects == null) {
            objects = new HashMap<>(8);
        }
        Integer idBox = objects.get(key);
        if (idBox == null) {
            idBox = objects.size();
            objects.put(key, idBox);
        }
        int id = idBox;
        if (isU1(id)) {
            writeTag(u1Tag);
            writeU1("id", id);
        } else {
            writeTag(u2Tag);
            writeU2("id:2", id);
        }
        return id;
    }

    /**
     * Returns {@code flag} if {@code condition == true} else {@code 0}.
     */
    private static int setIf(int flag, Object[] array) {
        return array != null && array.length > 0 ? flag : 0;
    }

    /**
     * Returns {@code flag} if {@code condition == true} else {@code 0}.
     */
    private static int setIf(int flag, boolean condition) {
        return condition ? flag : 0;
    }

    /**
     * Returns {@code flag} if {@code condition != 0} else {@code 0}.
     */
    private static int setIf(int flag, int condition) {
        return condition != 0 ? flag : 0;
    }

    private static boolean isSet(int flags, int bit) {
        return (flags & bit) != 0;
    }

    private void writeFrame(BytecodePosition pos, boolean fullInfo, int depth) {
        if (pos == null) {
            writeU2("depth", depth);
            return;
        }
        writeFrame(pos.getCaller(), fullInfo, depth + 1);
        writeMethod("method", pos.getMethod());
        writeInt("bci", pos.getBCI());
        if (fullInfo) {
            BytecodeFrame f = (BytecodeFrame) pos;
            f.verifyInvariants();
            int numLocals = f.numLocals;
            int numStack = f.numStack;
            int numLocks = f.numLocks;

            // @formatter:off
            int flags = setIf(HAS_LOCALS, numLocals) |
                        setIf(HAS_STACK, numStack) |
                        setIf(HAS_LOCKS, numLocks) |
                        setIf(DURING_CALL, f.duringCall) |
                        setIf(RETHROW_EXCEPTION, f.rethrowException);
            // @formatter:on
            writeU1("flags", flags);

            if (numLocals != 0) {
                writeU2("numLocals", numLocals);
                for (int i = 0; i < numLocals; i++) {
                    JavaKind kind = f.getLocalValueKind(i);
                    writeBasicType(kind);
                    writeJavaValue(f.getLocalValue(i), kind);
                }
            }
            if (numStack != 0) {
                writeU2("numStack", numStack);
                for (int i = 0; i < numStack; i++) {
                    JavaKind kind = f.getStackValueKind(i);
                    writeBasicType(kind);
                    writeJavaValue(f.getStackValue(i), kind);
                }
            }
            if (numLocks != 0) {
                writeU2("numLocks", numLocks);
                for (int i = 0; i < numLocks; i++) {
                    StackLockValue lock = (StackLockValue) f.getLockValue(i);
                    writeBoolean("isEliminated", lock.isEliminated());
                    writeJavaValue(lock.getOwner(), JavaKind.Object);
                    writeJavaValue(lock.getSlot(), JavaKind.Object);
                }
            }
        }
    }

    private void writeReferenceMap(ReferenceMap map) {
        HotSpotReferenceMap hsMap = (HotSpotReferenceMap) map;
        int length = hsMap.objects.length;
        writeU2("maxRegisterSize", hsMap.maxRegisterSize);
        int derivedBaseLength = hsMap.derivedBase.length;
        int sizeInBytesLength = hsMap.sizeInBytes.length;
        if (derivedBaseLength != length || sizeInBytesLength != length) {
            throw error("arrays in reference map have different sizes: %d %d %d", length, derivedBaseLength, sizeInBytesLength);
        }
        writeU2("referenceMap:length", length);
        for (int i = 0; i < length; i++) {
            Location derived = hsMap.derivedBase[i];
            writeBoolean("hasDerived", derived != null);
            int size = hsMap.sizeInBytes[i];
            if (size % 4 != 0) {
                throw error("invalid oop size in ReferenceMap: %d", size);
            }
            writeU2("sizeInBytes", size);
            writeLocation(hsMap.objects[i]);
            if (derived != null) {
                writeLocation(derived);
            }
        }
    }

    private void writeLocation(Location loc) {
        writeRegister(loc.reg);
        writeU2("offset", loc.offset);
    }

    private void writeRegister(Register reg) {
        if (reg == null) {
            writeU2("register", NO_REGISTER);
        } else {
            writeU2("register", reg.number);
        }
    }
}
