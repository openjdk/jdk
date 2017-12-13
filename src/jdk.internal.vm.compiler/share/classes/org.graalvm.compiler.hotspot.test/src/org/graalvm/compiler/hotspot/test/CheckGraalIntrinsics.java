/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Binding;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.JDK9Method;
import org.graalvm.compiler.test.GraalTest;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.MapCursor;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Checks the intrinsics implemented by Graal against the set of intrinsics declared by HotSpot. The
 * purpose of this test is to detect when new intrinsics are added to HotSpot and process them
 * appropriately in Graal. This will be achieved by working through {@link #TO_BE_INVESTIGATED} and
 * either implementing the intrinsic or moving it to {@link #IGNORE} .
 */
public class CheckGraalIntrinsics extends GraalTest {

    public static boolean match(String type, Binding binding, VMIntrinsicMethod intrinsic) {
        if (intrinsic.name.equals(binding.name)) {
            if (intrinsic.descriptor.startsWith(binding.argumentsDescriptor)) {
                if (type.equals(intrinsic.declaringClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static InvocationPlugin findPlugin(EconomicMap<String, List<Binding>> bindings, VMIntrinsicMethod intrinsic) {
        MapCursor<String, List<Binding>> cursor = bindings.getEntries();
        while (cursor.advance()) {
            // Match format of VMIntrinsicMethod.declaringClass
            String type = MetaUtil.internalNameToJava(cursor.getKey(), true, false).replace('.', '/');
            for (Binding binding : cursor.getValue()) {
                if (match(type, binding, intrinsic)) {
                    return binding.plugin;
                }
            }
        }
        return null;
    }

    public static ResolvedJavaMethod resolveIntrinsic(MetaAccessProvider metaAccess, VMIntrinsicMethod intrinsic) throws ClassNotFoundException {
        Class<?> c;
        try {
            c = Class.forName(intrinsic.declaringClass.replace('/', '.'), false, CheckGraalIntrinsics.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            try {
                Class.forName("javax.naming.Reference");
            } catch (ClassNotFoundException coreNamingMissing) {
                // if core JDK classes aren't found, we are probably running in a
                // JDK9 java.base environment and then missing class is OK
                return null;
            }
            throw ex;
        }
        for (Method javaMethod : c.getDeclaredMethods()) {
            if (javaMethod.getName().equals(intrinsic.name)) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(javaMethod);
                if (intrinsic.descriptor.equals("*")) {
                    // Signature polymorphic method - name match is enough
                    return method;
                } else {
                    if (method.getSignature().toMethodDescriptor().equals(intrinsic.descriptor)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The HotSpot intrinsics implemented without {@link InvocationPlugin}s or whose
     * {@link InvocationPlugin} registration is guarded by a condition that is false in the current
     * VM context.
     */
    private static final Set<String> IGNORE = new TreeSet<>();

    /**
     * The HotSpot intrinsics yet to be implemented or moved to {@link #IGNORE}.
     */
    private static final Set<String> TO_BE_INVESTIGATED = new TreeSet<>();

    private static Collection<String> add(Collection<String> c, String... elements) {
        String[] sorted = elements.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < elements.length; i++) {
            if (!elements[i].equals(sorted[i])) {
                // Let's keep the list sorted for easier visual inspection
                fail("Element %d is out of order, \"%s\"", i, elements[i]);
            }
        }
        c.addAll(Arrays.asList(elements));
        return c;
    }

    static {
        // These are dead
        add(IGNORE,
                        "java/lang/Math.atan2(DD)D",
                        "jdk/internal/misc/Unsafe.park(ZJ)V",
                        "jdk/internal/misc/Unsafe.unpark(Ljava/lang/Object;)V",
                        "sun/misc/Unsafe.park(ZJ)V",
                        "sun/misc/Unsafe.prefetchRead(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchReadStatic(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchWrite(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchWriteStatic(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.unpark(Ljava/lang/Object;)V");

        // These only exist to assist escape analysis in C2
        add(IGNORE,
                        "java/lang/Throwable.fillInStackTrace()Ljava/lang/Throwable;");

        // These are only used for the security handling during stack walking
        add(IGNORE,
                        "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

        // These are marker intrinsic ids only
        add(IGNORE,
                        "java/lang/invoke/MethodHandle.<compiledLambdaForm>*",
                        "java/lang/invoke/MethodHandle.invoke*");

        // These are implemented through lowering
        add(IGNORE,
                        "java/lang/ref/Reference.get()Ljava/lang/Object;");

        // These are only used by C1
        add(IGNORE,
                        "java/nio/Buffer.checkIndex(I)I");

        // These do general compiler optimizations and convert min/max to cmov instructions. We are
        // ignoring them as cmovs are not necessarily beneficial.
        add(IGNORE,
                        "java/lang/Math.max(II)I",
                        "java/lang/Math.min(II)I");

        // These are known to be implemented down stream
        add(IGNORE,
                        "java/lang/Integer.toString(I)Ljava/lang/String;",
                        "java/lang/String.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuffer.<init>()V",
                        "java/lang/StringBuffer.<init>(I)V",
                        "java/lang/StringBuffer.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.append(I)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.toString()Ljava/lang/String;",
                        "java/lang/StringBuilder.<init>()V",
                        "java/lang/StringBuilder.<init>(I)V",
                        "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuilder.append(C)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.append(I)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.toString()Ljava/lang/String;",
                        "java/util/Arrays.copyOf([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                        "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;");

        // These are known to be implemented but the platform dependent conditions
        // for when they are enabled are complex so just ignore them all the time.
        add(IGNORE,
                        "java/lang/Integer.bitCount(I)I",
                        "java/lang/Integer.numberOfLeadingZeros(I)I",
                        "java/lang/Integer.numberOfTrailingZeros(I)I",
                        "java/lang/Long.bitCount(J)I",
                        "java/lang/Long.numberOfLeadingZeros(J)I",
                        "java/lang/Long.numberOfTrailingZeros(J)I");

        // Relevant for Java flight recorder
        add(TO_BE_INVESTIGATED,
                        "oracle/jrockit/jfr/Timing.counterTime()J",
                        "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                        "oracle/jrockit/jfr/VMJFR.threadID()I");

        add(TO_BE_INVESTIGATED,
                        // Should be fairly easy to implement - C2 intrinsifies these to use "v !=
                        // v" to check for NaN instead of looking at the bit pattern.
                        "java/lang/Double.doubleToLongBits(D)J",
                        "java/lang/Float.floatToIntBits(F)I",

                        // Should be trivial to implement because we already have existing nodes
                        "java/lang/Math.decrementExact(I)I",
                        "java/lang/Math.decrementExact(J)J",
                        "java/lang/Math.incrementExact(I)I",
                        "java/lang/Math.incrementExact(J)J",

                        // Similar to addExact
                        "java/lang/Math.negateExact(I)I",
                        // Similar to addExact
                        "java/lang/Math.negateExact(J)J",
                        // HotSpot MacroAssembler-based intrinsic
                        "java/lang/String.compareTo(Ljava/lang/String;)I",
                        // HotSpot MacroAssembler-based intrinsic
                        "java/lang/String.indexOf(Ljava/lang/String;)I",
                        // Can share most implementation parts with with
                        // Unsafe.allocateUninitializedArray0
                        "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                        // HotSpot MacroAssembler-based intrinsic
                        "sun/nio/cs/ISO_8859_1$Encoder.encodeISOArray([CI[BII)I",
                        // Stub based intrinsics but implementation seems complex in C2
                        "sun/security/provider/DigestBase.implCompressMultiBlock([BII)I");

        if (isJDK9OrHigher()) {
            // Relevant for Java flight recorder
            add(TO_BE_INVESTIGATED,
                            "jdk/jfr/internal/JVM.counterTime()J",
                            "jdk/jfr/internal/JVM.getBufferWriter()Ljava/lang/Object;",
                            "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J");

            add(TO_BE_INVESTIGATED,
                            // Some logic and a stub call
                            "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I",
                            // Stub and very little logic
                            "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V",
                            // HotSpot MacroAssembler-based intrinsic
                            "java/lang/Math.fma(DDD)D",
                            // HotSpot MacroAssembler-based intrinsic
                            "java/lang/Math.fma(FFF)F",
                            // Just a runtime call (the called C code has a better fast path)
                            "java/lang/Object.notify()V",
                            // Just a runtime call (the called C code has a better fast path)
                            "java/lang/Object.notifyAll()V",
                            // Emit pause instruction if os::is_MP()
                            "java/lang/Thread.onSpinWait()V",
                            // Just check if the argument is a compile time constant
                            "java/lang/invoke/MethodHandleImpl.isCompileConstant(Ljava/lang/Object;)Z",
                            // Some logic and a runtime call
                            "java/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I",
                            // Only used as a marker for vectorization?
                            "java/util/stream/Streams$RangeIntSpliterator.forEachRemaining(Ljava/util/function/IntConsumer;)V",
                            // Only implemented on non-AMD64 platforms (some logic and runtime call)
                            "java/util/zip/Adler32.updateByteBuffer(IJII)I",
                            // Only implemented on non-AMD64 platforms (some logic and runtime call)
                            "java/util/zip/Adler32.updateBytes(I[BII)I",
                            // similar to CRC32.updateBytes
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            // similar to CRC32.updateDirectByteBuffer
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I",
                            // Emits a slow and a fast path and some dispatching logic
                            "jdk/internal/misc/Unsafe.allocateUninitializedArray0(Ljava/lang/Class;I)Ljava/lang/Object;",

                            // Should be easy to implement as it seems to match the logic that is
                            // already implemented in ValueCompareAndSwapNode. On the high-level, we
                            // would need something similar to UnsafeCompareAndSwapNode but with a
                            // different result type.
                            "jdk/internal/misc/Unsafe.compareAndExchangeByte(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeInt(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLong(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShort(Ljava/lang/Object;JSS)S",

                            // Should be easy to implement as we already have an implementation for
                            // int, long, and Object.
                            "jdk/internal/misc/Unsafe.compareAndSetByte(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.compareAndSetShort(Ljava/lang/Object;JSS)Z",

                            // Should be easy to implement as we already have an implementation for
                            // int and long.
                            "jdk/internal/misc/Unsafe.getAndAddByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndAddShort(Ljava/lang/Object;JS)S",

                            // Should be easy to implement as we already have an implementation for
                            // int, long, and Object.
                            "jdk/internal/misc/Unsafe.getAndSetByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndSetShort(Ljava/lang/Object;JS)S",

                            // Control flow, deopts, and a cast
                            "jdk/internal/util/Preconditions.checkIndex(IILjava/util/function/BiFunction;)I",
                            // HotSpot MacroAssembler-based intrinsic
                            "sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray([CI[BII)I",
                            // Runtime call and some complex compiler logic
                            "sun/security/provider/DigestBase.implCompressMultiBlock0([BII)I");
            /*
             * Per default, all these operations are mapped to some generic method for which we
             * already have compiler intrinsics. Performance-wise it would be better to support them
             * explicitly as the more generic method might be more restrictive and therefore slower
             * than necessary.
             */
            add(TO_BE_INVESTIGATED,
                            // Mapped to compareAndExchange*
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteAcquire(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteRelease(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntAcquire(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntRelease(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongAcquire(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongRelease(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortAcquire(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortRelease(Ljava/lang/Object;JSS)S",

                            // Mapped to get*Volatile
                            "jdk/internal/misc/Unsafe.getBooleanAcquire(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getBooleanOpaque(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getByteAcquire(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getByteOpaque(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getCharAcquire(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getCharOpaque(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getDoubleAcquire(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.getDoubleOpaque(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.getFloatAcquire(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getFloatOpaque(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getIntAcquire(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getIntOpaque(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLongAcquire(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getLongOpaque(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getObjectAcquire(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getObjectOpaque(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getShortAcquire(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getShortOpaque(Ljava/lang/Object;J)S",

                            // Mapped to put*Volatile
                            "jdk/internal/misc/Unsafe.putBooleanOpaque(Ljava/lang/Object;JZ)V",
                            "jdk/internal/misc/Unsafe.putByteOpaque(Ljava/lang/Object;JB)V",
                            "jdk/internal/misc/Unsafe.putCharOpaque(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putDoubleOpaque(Ljava/lang/Object;JD)V",
                            "jdk/internal/misc/Unsafe.putFloatOpaque(Ljava/lang/Object;JF)V",
                            "jdk/internal/misc/Unsafe.putIntOpaque(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLongOpaque(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.putObjectOpaque(Ljava/lang/Object;JLjava/lang/Object;)V",
                            "jdk/internal/misc/Unsafe.putShortOpaque(Ljava/lang/Object;JS)V",

                            // Mapped to compareAndSet*
                            "jdk/internal/misc/Unsafe.weakCompareAndSetByte(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetByteAcquire(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetBytePlain(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetByteRelease(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetInt(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetIntAcquire(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetIntPlain(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetIntRelease(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetLong(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetLongAcquire(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetLongPlain(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetLongRelease(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetObjectPlain(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShort(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortAcquire(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortPlain(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortRelease(Ljava/lang/Object;JSS)Z");

            // Compact string support - HotSpot MacroAssembler-based intrinsic or complex C2 logic.
            add(TO_BE_INVESTIGATED,
                            "java/lang/StringCoding.hasNegatives([BII)Z",
                            "java/lang/StringCoding.implEncodeISOArray([BI[BII)I",
                            "java/lang/StringLatin1.compareTo([B[B)I",
                            "java/lang/StringLatin1.compareToUTF16([B[B)I",
                            "java/lang/StringLatin1.equals([B[B)Z",
                            "java/lang/StringLatin1.indexOf([BI[BII)I",
                            "java/lang/StringLatin1.indexOf([B[B)I",
                            "java/lang/StringLatin1.inflate([BI[BII)V",
                            "java/lang/StringLatin1.inflate([BI[CII)V",
                            "java/lang/StringUTF16.compareTo([B[B)I",
                            "java/lang/StringUTF16.compareToLatin1([B[B)I",
                            "java/lang/StringUTF16.compress([BI[BII)I",
                            "java/lang/StringUTF16.compress([CI[BII)I",
                            "java/lang/StringUTF16.equals([B[B)Z",
                            "java/lang/StringUTF16.getChar([BI)C",
                            "java/lang/StringUTF16.getChars([BII[CI)V",
                            "java/lang/StringUTF16.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOf([B[B)I",
                            "java/lang/StringUTF16.indexOfChar([BIII)I",
                            "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                            "java/lang/StringUTF16.indexOfLatin1([B[B)I",
                            "java/lang/StringUTF16.putChar([BII)V",
                            "java/lang/StringUTF16.toBytes([CII)[B");
        }

        if (!getHostArchitectureName().equals("amd64")) {
            // Can we implement these on non-AMD64 platforms? C2 seems to.
            add(TO_BE_INVESTIGATED,
                            "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");

            if (isJDK9OrHigher()) {
                add(TO_BE_INVESTIGATED,
                                "jdk/internal/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                                "jdk/internal/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                                "jdk/internal/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                                "jdk/internal/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                                "jdk/internal/misc/Unsafe.getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;",
                                "jdk/internal/misc/Unsafe.getCharUnaligned(Ljava/lang/Object;J)C",
                                "jdk/internal/misc/Unsafe.getIntUnaligned(Ljava/lang/Object;J)I",
                                "jdk/internal/misc/Unsafe.getLongUnaligned(Ljava/lang/Object;J)J",
                                "jdk/internal/misc/Unsafe.getShortUnaligned(Ljava/lang/Object;J)S",
                                "jdk/internal/misc/Unsafe.putCharUnaligned(Ljava/lang/Object;JC)V",
                                "jdk/internal/misc/Unsafe.putIntUnaligned(Ljava/lang/Object;JI)V",
                                "jdk/internal/misc/Unsafe.putLongUnaligned(Ljava/lang/Object;JJ)V",
                                "jdk/internal/misc/Unsafe.putShortUnaligned(Ljava/lang/Object;JS)V");
            }
        }

        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        GraalHotSpotVMConfig config = rt.getVMConfig();

        /*
         * The intrinsics down here are known to be implemented but they are not always enabled on
         * the HotSpot side (e.g., because they require certain CPU features). So, we are ignoring
         * them if the HotSpot config tells us that they can't be used.
         */

        // CRC32 intrinsics
        if (!config.useCRC32Intrinsics) {
            add(IGNORE, "java/util/zip/CRC32.update(II)I");
            if (isJDK9OrHigher()) {
                add(IGNORE,
                                "java/util/zip/CRC32.updateByteBuffer0(IJII)I",
                                "java/util/zip/CRC32.updateBytes0(I[BII)I");
            } else {
                add(IGNORE,
                                "java/util/zip/CRC32.updateByteBuffer(IJII)I",
                                "java/util/zip/CRC32.updateBytes(I[BII)I");
            }
        }

        // CRC32C intrinsics
        if (!config.useCRC32CIntrinsics) {
            add(IGNORE,
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
        }

        // AES intrinsics
        if (!config.useAESIntrinsics) {
            if (isJDK9OrHigher()) {
                add(IGNORE,
                                "com/sun/crypto/provider/AESCrypt.implDecryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.implEncryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.implDecrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.implEncrypt([BII[BI)I");
            } else {
                add(IGNORE,
                                "com/sun/crypto/provider/AESCrypt.decryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.encryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.decrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.encrypt([BII[BI)I");
            }
        }

        // BigInteger intrinsics
        if (!config.useMultiplyToLenIntrinsic()) {
            if (isJDK9OrHigher()) {
                add(IGNORE, "java/math/BigInteger.implMultiplyToLen([II[II[I)[I");
            } else {
                add(IGNORE, "java/math/BigInteger.multiplyToLen([II[II[I)[I");
            }
        }
        if (!config.useMulAddIntrinsic()) {
            add(IGNORE, "java/math/BigInteger.implMulAdd([I[IIII)I");
        }
        if (!config.useMontgomeryMultiplyIntrinsic()) {
            add(IGNORE, "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I");
        }
        if (!config.useMontgomerySquareIntrinsic()) {
            add(IGNORE, "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I");
        }
        if (!config.useSquareToLenIntrinsic()) {
            add(IGNORE, "java/math/BigInteger.implSquareToLen([II[II)[I");
        }

        // SHA intrinsics
        if (!config.useSHA1Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(IGNORE, "sun/security/provider/SHA.implCompress0([BI)V");
            } else {
                add(IGNORE, "sun/security/provider/SHA.implCompress([BI)V");
            }
        }
        if (!config.useSHA256Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(IGNORE, "sun/security/provider/SHA2.implCompress0([BI)V");
            } else {
                add(IGNORE, "sun/security/provider/SHA2.implCompress([BI)V");
            }
        }
        if (!config.useSHA512Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(IGNORE, "sun/security/provider/SHA5.implCompress0([BI)V");
            } else {
                add(IGNORE, "sun/security/provider/SHA5.implCompress([BI)V");
            }
        }
    }

    private static boolean isJDK9OrHigher() {
        return JDK9Method.JAVA_SPECIFICATION_VERSION >= 9;
    }

    private static String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        if (arch.equals("x86_64")) {
            arch = "amd64";
        } else if (arch.equals("sparcv9")) {
            arch = "sparc";
        }
        return arch;
    }

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException {
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();

        HotSpotVMConfigStore store = rt.getVMConfig().getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();

        List<String> missing = new ArrayList<>();
        EconomicMap<String, List<Binding>> bindings = invocationPlugins.getBindings(true);
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            InvocationPlugin plugin = findPlugin(bindings, intrinsic);
            if (plugin == null) {
                ResolvedJavaMethod method = resolveIntrinsic(providers.getMetaAccess(), intrinsic);
                if (method != null) {
                    IntrinsicMethod intrinsicMethod = providers.getConstantReflection().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
                    if (intrinsicMethod != null) {
                        continue;
                    }
                }
                String m = String.format("%s.%s%s", intrinsic.declaringClass, intrinsic.name, intrinsic.descriptor);
                if (!TO_BE_INVESTIGATED.contains(m) && !IGNORE.contains(m)) {
                    missing.add(m);
                }
            }
        }

        if (!missing.isEmpty()) {
            Collections.sort(missing);
            String missingString = missing.stream().collect(Collectors.joining(String.format("%n    ")));
            fail("missing Graal intrinsics for:%n    %s", missingString);
        }
    }
}
