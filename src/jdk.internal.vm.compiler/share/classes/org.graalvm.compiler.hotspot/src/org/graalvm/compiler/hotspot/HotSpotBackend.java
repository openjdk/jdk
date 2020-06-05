/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls.UNSAFE_ARRAYCOPY;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.VMErrorNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantStubCall;
import org.graalvm.compiler.hotspot.replacements.AESCryptSubstitutions;
import org.graalvm.compiler.hotspot.replacements.BigIntegerSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions;
import org.graalvm.compiler.hotspot.replacements.DigestBaseSubstitutions;
import org.graalvm.compiler.hotspot.replacements.SHA2Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHA5Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHASubstitutions;
import org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.hotspot.word.MethodCountersPointer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.RestoreRegistersOp;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * HotSpot specific backend.
 */
public abstract class HotSpotBackend extends Backend implements FrameMap.ReferenceMapBuilderFactory {

    public static class Options {
        // @formatter:off
        @Option(help = "Use Graal arithmetic stubs instead of HotSpot stubs where possible")
        public static final OptionKey<Boolean> GraalArithmeticStubs = new OptionKey<>(JavaVersionUtil.JAVA_SPEC >= 9);
        @Option(help = "Enables instruction profiling on assembler level. Valid values are a comma separated list of supported instructions." +
                        " Compare with subclasses of Assembler.InstructionCounter.", type = OptionType.Debug)
        public static final OptionKey<String> ASMInstructionProfiling = new OptionKey<>(null);
        // @formatter:on
    }

    /**
     * Descriptor for {@link ExceptionHandlerStub}. This stub is called by the
     * {@linkplain HotSpotMarkId#EXCEPTION_HANDLER_ENTRY exception handler} in a compiled method.
     */
    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, any(), "exceptionHandler", void.class, Object.class,
                    Word.class);

    /**
     * Descriptor for SharedRuntime::get_ic_miss_stub().
     */
    public static final HotSpotForeignCallDescriptor IC_MISS_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "icMissHandler", void.class);

    /**
     * Descriptor for SharedRuntime::get_handle_wrong_method_stub().
     */
    public static final HotSpotForeignCallDescriptor WRONG_METHOD_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "wrongMethodHandler", void.class);

    /**
     * Descriptor for {@link UnwindExceptionToCallerStub}. This stub is called by code generated
     * from {@link UnwindNode}.
     */
    public static final HotSpotForeignCallDescriptor UNWIND_EXCEPTION_TO_CALLER = new HotSpotForeignCallDescriptor(SAFEPOINT, NOT_REEXECUTABLE, any(), "unwindExceptionToCaller", void.class,
                    Object.class, Word.class);

    /**
     * Descriptor for the arguments when unwinding to an exception handler in a caller.
     */
    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER_IN_CALLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, any(), "exceptionHandlerInCaller",
                    void.class, Object.class, Word.class);

    private final HotSpotGraalRuntimeProvider runtime;

    /**
     * @see AESCryptSubstitutions#encryptBlockStub(ForeignCallDescriptor, Word, Word, Pointer)
     */
    public static final HotSpotForeignCallDescriptor ENCRYPT_BLOCK = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), "encrypt_block",
                    void.class, Word.class, Word.class, Pointer.class);

    /**
     * @see AESCryptSubstitutions#decryptBlockStub(ForeignCallDescriptor, Word, Word, Pointer)
     */
    public static final HotSpotForeignCallDescriptor DECRYPT_BLOCK = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), "decrypt_block",
                    void.class, Word.class, Word.class, Pointer.class);

    /**
     * @see AESCryptSubstitutions#decryptBlockStub(ForeignCallDescriptor, Word, Word, Pointer)
     */
    public static final HotSpotForeignCallDescriptor DECRYPT_BLOCK_WITH_ORIGINAL_KEY = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte),
                    "decrypt_block_with_original_key", void.class, Word.class, Word.class, Pointer.class,
                    Pointer.class);

    /**
     * @see CipherBlockChainingSubstitutions#crypt
     */
    public static final HotSpotForeignCallDescriptor ENCRYPT = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), "encrypt", void.class,
                    Word.class, Word.class, Pointer.class, Pointer.class, int.class);

    /**
     * @see CipherBlockChainingSubstitutions#crypt
     */
    public static final HotSpotForeignCallDescriptor DECRYPT = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), "decrypt", void.class,
                    Word.class, Word.class, Pointer.class, Pointer.class, int.class);

    /**
     * @see CipherBlockChainingSubstitutions#crypt
     */
    public static final HotSpotForeignCallDescriptor DECRYPT_WITH_ORIGINAL_KEY = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte),
                    "decrypt_with_original_key", void.class, Word.class, Word.class, Pointer.class, Pointer.class,
                    int.class, Pointer.class);

    /**
     * @see BigIntegerSubstitutions#multiplyToLen
     */
    public static final HotSpotForeignCallDescriptor MULTIPLY_TO_LEN = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "multiplyToLen",
                    void.class, Word.class, int.class, Word.class, int.class, Word.class, int.class);

    public static void multiplyToLenStub(Word xAddr, int xlen, Word yAddr, int ylen, Word zAddr, int zLen) {
        multiplyToLenStub(HotSpotBackend.MULTIPLY_TO_LEN, xAddr, xlen, yAddr, ylen, zAddr, zLen);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void multiplyToLenStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word xIn, int xLen, Word yIn, int yLen, Word zIn, int zLen);

    /**
     * @see BigIntegerSubstitutions#mulAdd
     */
    public static final HotSpotForeignCallDescriptor MUL_ADD = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int), "mulAdd",
                    int.class,
                    Word.class, Word.class, int.class, int.class, int.class);

    public static int mulAddStub(Word inAddr, Word outAddr, int newOffset, int len, int k) {
        return mulAddStub(HotSpotBackend.MUL_ADD, inAddr, outAddr, newOffset, len, k);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int mulAddStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word inAddr, Word outAddr, int newOffset, int len, int k);

    /**
     * @see BigIntegerSubstitutions#implMontgomeryMultiply
     */
    public static final HotSpotForeignCallDescriptor MONTGOMERY_MULTIPLY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "implMontgomeryMultiply", void.class, Word.class, Word.class, Word.class, int.class, long.class,
                    Word.class);

    public static void implMontgomeryMultiply(Word aAddr, Word bAddr, Word nAddr, int len, long inv, Word productAddr) {
        implMontgomeryMultiply(HotSpotBackend.MONTGOMERY_MULTIPLY, aAddr, bAddr, nAddr, len, inv, productAddr);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void implMontgomeryMultiply(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word aAddr, Word bAddr, Word nAddr, int len, long inv, Word productAddr);

    /**
     * @see BigIntegerSubstitutions#implMontgomerySquare
     */
    public static final HotSpotForeignCallDescriptor MONTGOMERY_SQUARE = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "implMontgomerySquare", void.class, Word.class, Word.class, int.class, long.class, Word.class);

    public static void implMontgomerySquare(Word aAddr, Word nAddr, int len, long inv, Word productAddr) {
        implMontgomerySquare(HotSpotBackend.MONTGOMERY_SQUARE, aAddr, nAddr, len, inv, productAddr);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void implMontgomerySquare(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word aAddr, Word nAddr, int len, long inv, Word productAddr);

    /**
     * @see BigIntegerSubstitutions#implSquareToLen
     */
    public static final HotSpotForeignCallDescriptor SQUARE_TO_LEN = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "implSquareToLen",
                    void.class, Word.class, int.class, Word.class, int.class);

    public static void implSquareToLen(Word xAddr, int len, Word zAddr, int zLen) {
        implSquareToLen(SQUARE_TO_LEN, xAddr, len, zAddr, zLen);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void implSquareToLen(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word xAddr, int len, Word zAddr, int zLen);

    /**
     * @see SHASubstitutions#implCompress0
     */
    public static final HotSpotForeignCallDescriptor SHA_IMPL_COMPRESS = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "shaImplCompress", void.class, Word.class,
                    Object.class);

    public static void shaImplCompressStub(Word bufAddr, Object state) {
        shaImplCompressStub(HotSpotBackend.SHA_IMPL_COMPRESS, bufAddr, state);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void shaImplCompressStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state);

    /**
     * @see SHA2Substitutions#implCompress0
     */
    public static final HotSpotForeignCallDescriptor SHA2_IMPL_COMPRESS = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "sha2ImplCompress", void.class, Word.class,
                    Object.class);

    public static void sha2ImplCompressStub(Word bufAddr, Object state) {
        sha2ImplCompressStub(HotSpotBackend.SHA2_IMPL_COMPRESS, bufAddr, state);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void sha2ImplCompressStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state);

    /**
     * @see SHA5Substitutions#implCompress0
     */
    public static final HotSpotForeignCallDescriptor SHA5_IMPL_COMPRESS = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "sha5ImplCompress", void.class, Word.class,
                    Object.class);

    public static void sha5ImplCompressStub(Word bufAddr, Object state) {
        sha5ImplCompressStub(HotSpotBackend.SHA5_IMPL_COMPRESS, bufAddr, state);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void sha5ImplCompressStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state);

    /**
     * @see DigestBaseSubstitutions#implCompressMultiBlock0
     */
    public static final HotSpotForeignCallDescriptor SHA_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "shaImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int shaImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return shaImplCompressMBStub(HotSpotBackend.SHA_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int shaImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA2_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "sha2ImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int sha2ImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return sha2ImplCompressMBStub(HotSpotBackend.SHA2_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int sha2ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA5_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "sha5ImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int sha5ImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return sha5ImplCompressMBStub(HotSpotBackend.SHA5_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int sha5ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static void unsafeArraycopy(Word srcAddr, Word dstAddr, Word size) {
        unsafeArraycopyStub(UNSAFE_ARRAYCOPY, srcAddr, dstAddr, size);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void unsafeArraycopyStub(@ConstantNodeParameter ForeignCallSignature descriptor, Word srcAddr, Word dstAddr, Word size);

    /**
     * Descriptor for {@code StubRoutines::_ghash_processBlocks}.
     */
    public static final HotSpotForeignCallDescriptor GHASH_PROCESS_BLOCKS = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "ghashProcessBlocks", void.class, Word.class,
                    Word.class, Word.class, int.class);

    /**
     * Descriptor for {@code StubRoutines::_base64_encodeBlock}.
     */
    public static final HotSpotForeignCallDescriptor BASE64_ENCODE_BLOCK = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "base64EncodeBlock", void.class, Word.class,
                    int.class, int.class, Word.class, int.class, boolean.class);

    /**
     * Descriptor for {@code StubRoutines::_counterMode_AESCrypt}.
     */
    public static final HotSpotForeignCallDescriptor COUNTERMODE_IMPL_CRYPT = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "counterModeAESCrypt", int.class,
                    Word.class, Word.class, Word.class, Word.class, int.class,
                    Word.class, Word.class);

    public static int counterModeAESCrypt(Word srcAddr, Word dstAddr, Word kPtr, Word cntPtr, int len, Word encCntPtr, Word used) {
        return counterModeAESCrypt(COUNTERMODE_IMPL_CRYPT, srcAddr, dstAddr, kPtr, cntPtr, len, encCntPtr, used);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int counterModeAESCrypt(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word srcAddr, Word dstAddr, Word kPtr, Word cntPtr, int len, Word encCntPtr,
                    Word used);

    /**
     * Descriptor for {@code StubRoutines::_vectorizedMismatch}.
     */
    public static final HotSpotForeignCallDescriptor VECTORIZED_MISMATCHED = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "vectorizedMismatch", int.class, Word.class,
                    Word.class, int.class, int.class);

    public static int vectorizedMismatch(Word aAddr, Word bAddr, int length, int log2ArrayIndexScale) {
        return vectorizedMismatchStub(VECTORIZED_MISMATCHED, aAddr, bAddr, length, log2ArrayIndexScale);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int vectorizedMismatchStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word aAddr, Word bAddr, int length, int log2ArrayIndexScale);

    /**
     * @see VMErrorNode
     */
    public static final HotSpotForeignCallDescriptor VM_ERROR = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "vm_error", void.class, Object.class, Object.class,
                    long.class);

    private static final LocationIdentity[] TLAB_LOCATIONS = new LocationIdentity[]{TLAB_TOP_LOCATION, TLAB_END_LOCATION};

    /**
     * New multi array stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_MULTI_ARRAY = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_multi_array", Object.class, KlassPointer.class,
                    int.class, Word.class);

    /**
     * New multi array stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_MULTI_ARRAY_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_multi_array_or_null", Object.class,
                    KlassPointer.class, int.class, Word.class);

    /**
     * New array stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_ARRAY = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_array", Object.class, KlassPointer.class, int.class);

    /**
     * New array stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_ARRAY_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_array_or_null", Object.class,
                    KlassPointer.class,
                    int.class);

    /**
     * New instance stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_INSTANCE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_instance", Object.class, KlassPointer.class);

    /**
     * New instance stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_INSTANCE_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "new_instance_or_null", Object.class,
                    KlassPointer.class);

    /**
     * @see ResolveConstantStubCall
     */
    public static final HotSpotForeignCallDescriptor RESOLVE_STRING_BY_SYMBOL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, TLAB_LOCATIONS, "resolve_string_by_symbol", Object.class,
                    Word.class, Word.class);

    /**
     * @see ResolveConstantStubCall
     */
    public static final HotSpotForeignCallDescriptor RESOLVE_DYNAMIC_INVOKE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "resolve_dynamic_invoke", Object.class, Word.class);

    /**
     * @see ResolveConstantStubCall
     */
    public static final HotSpotForeignCallDescriptor RESOLVE_KLASS_BY_SYMBOL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "resolve_klass_by_symbol", Word.class, Word.class,
                    Word.class);

    /**
     * @see ResolveConstantStubCall
     */
    public static final HotSpotForeignCallDescriptor INITIALIZE_KLASS_BY_SYMBOL = new HotSpotForeignCallDescriptor(SAFEPOINT, NOT_REEXECUTABLE, any(), "initialize_klass_by_symbol", Word.class,
                    Word.class,
                    Word.class);

    /**
     * @see ResolveConstantStubCall
     */
    public static final HotSpotForeignCallDescriptor RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS,
                    "resolve_method_by_symbol_and_load_counters", Word.class, Word.class, Word.class,
                    Word.class);

    /**
     * Tiered support.
     */
    public static final HotSpotForeignCallDescriptor INVOCATION_EVENT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "invocation_event", void.class,
                    MethodCountersPointer.class);
    public static final HotSpotForeignCallDescriptor BACKEDGE_EVENT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "backedge_event", void.class, MethodCountersPointer.class,
                    int.class, int.class);

    public HotSpotBackend(HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(providers);
        this.runtime = runtime;
    }

    public HotSpotGraalRuntimeProvider getRuntime() {
        return runtime;
    }

    /**
     * Performs any remaining initialization that was deferred until the {@linkplain #getRuntime()
     * runtime} object was initialized and this backend was registered with it.
     *
     * @param jvmciRuntime
     * @param options
     */
    public void completeInitialization(HotSpotJVMCIRuntime jvmciRuntime, OptionValues options) {
    }

    /**
     * Finds all the registers that are defined by some given LIR.
     *
     * @param gen the result to examine
     * @return the registers that are defined by or used as temps for any instruction in {@code lir}
     */
    private EconomicSet<Register> gatherDestroyedCallerRegisters(HotSpotLIRGenerationResult gen) {
        LIR lir = gen.getLIR();
        final EconomicSet<Register> preservedRegisters = EconomicSet.create(Equivalence.IDENTITY);
        final EconomicSet<Register> destroyedRegisters = EconomicSet.create(Equivalence.IDENTITY);
        ValueConsumer defConsumer = new ValueConsumer() {

            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (ValueUtil.isRegister(value)) {
                    final Register reg = ValueUtil.asRegister(value);
                    if (!preservedRegisters.contains(reg)) {
                        destroyedRegisters.add(reg);
                    }
                }
            }
        };
        boolean sawSaveRegisters = false;
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
            // Ignore the effects of instructions bracketed by save/restore
            SaveRegistersOp save = null;
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op instanceof LabelOp) {
                    // Don't consider this as a definition
                } else if (op instanceof SaveRegistersOp) {
                    save = (SaveRegistersOp) op;
                    sawSaveRegisters = true;
                    preservedRegisters.addAll(save.getSaveableRegisters());
                } else if (op instanceof RestoreRegistersOp) {
                    save = null;
                    preservedRegisters.clear();
                } else {
                    op.visitEachTemp(defConsumer);
                    op.visitEachOutput(defConsumer);
                }
            }
            assert save == null : "missing RestoreRegistersOp";
        }

        if (sawSaveRegisters) {
            // The return value must be killed so it can be propagated out
            CallingConvention cc = gen.getCallingConvention();
            AllocatableValue returnValue = cc.getReturn();
            if (returnValue != null) {
                if (ValueUtil.isRegister(returnValue)) {
                    destroyedRegisters.add(ValueUtil.asRegister(returnValue));
                }
            }
        }
        return translateToCallerRegisters(destroyedRegisters);
    }

    /**
     * Translates a set of registers from the callee's perspective to the caller's perspective. This
     * is needed for architectures where input/output registers are renamed during a call (e.g.
     * register windows on SPARC). Registers which are not visible by the caller are removed.
     */
    protected abstract EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters);

    /**
     * Updates a given stub with respect to the registers it destroys by
     * {@link #gatherDestroyedCallerRegisters(HotSpotLIRGenerationResult) computing the destroyed
     * registers} and removing those registers from the {@linkplain SaveRegistersOp SaveRegistersOp}
     * as these registers are declared as temporaries in the stub's {@linkplain ForeignCallLinkage
     * linkage} (and thus will be saved by the stub's caller).
     *
     * @param stub the stub to update
     * @param gen the HotSpotLIRGenerationResult being emitted
     * @param frameMap used to {@linkplain FrameMap#offsetForStackSlot(StackSlot) convert} a virtual
     */
    protected void updateStub(Stub stub, HotSpotLIRGenerationResult gen, FrameMap frameMap) {
        EconomicSet<Register> destroyedRegisters = gatherDestroyedCallerRegisters(gen);
        EconomicMap<LIRFrameState, SaveRegistersOp> calleeSaveInfo = gen.getCalleeSaveInfo();

        if (stub.getLinkage().needsDebugInfo() && calleeSaveInfo.isEmpty()) {
            // This call is a safepoint but no register saving was done so we must ensure that all
            // registers appear to be killed. The Native ABI may allow caller save registers but
            // for HotSpot they must be described in a RegisterMap so they are accessible.
            for (Register r : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                destroyedRegisters.add(r);
            }
        }

        stub.initDestroyedCallerRegisters(destroyedRegisters);

        MapCursor<LIRFrameState, SaveRegistersOp> cursor = calleeSaveInfo.getEntries();
        while (cursor.advance()) {
            SaveRegistersOp save = cursor.getValue();
            save.remove(destroyedRegisters);
            if (cursor.getKey() != LIRFrameState.NO_STATE) {
                cursor.getKey().debugInfo().setCalleeSaveInfo(save.getMap(frameMap));
            }
        }
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    @Override
    public SuitesProvider getSuites() {
        return getProviders().getSuites();
    }

    protected void profileInstructions(LIR lir, CompilationResultBuilder crb) {
        if (HotSpotBackend.Options.ASMInstructionProfiling.getValue(lir.getOptions()) != null) {
            HotSpotInstructionProfiling.countInstructions(lir, crb.asm);
        }
    }

    @Override
    public CompiledCode createCompiledCode(ResolvedJavaMethod method,
                    CompilationRequest compilationRequest,
                    CompilationResult compResult,
                    boolean isDefault,
                    OptionValues options) {
        assert !isDefault || compResult.getName() == null : "a default nmethod should have a null name since it is associated with a Method*";
        HotSpotCompilationRequest compRequest = compilationRequest instanceof HotSpotCompilationRequest ? (HotSpotCompilationRequest) compilationRequest : null;
        return HotSpotCompiledCodeBuilder.createCompiledCode(getCodeCache(), method, compRequest, compResult, options);
    }

    @Override
    public CompilationIdentifier getCompilationIdentifier(ResolvedJavaMethod resolvedJavaMethod) {
        if (resolvedJavaMethod instanceof HotSpotResolvedJavaMethod) {
            HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) resolvedJavaMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
            return new HotSpotCompilationIdentifier(request);
        }
        return super.getCompilationIdentifier(resolvedJavaMethod);
    }
}
