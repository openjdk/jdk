/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.util.Util.JAVA_SPECIFICATION_VERSION;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Checks the set of intrinsics implemented by Graal against the set of intrinsics declared by
 * HotSpot. The purpose of this test is to detect when new intrinsics are added to HotSpot and
 * process them appropriately in Graal. This will be achieved by working through
 * {@link #TO_BE_INVESTIGATED} and either implementing the intrinsic or moving it to {@link #IGNORE}
 * .
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

    public static InvocationPlugin findPlugin(Map<String, List<Binding>> bindings, VMIntrinsicMethod intrinsic) {
        for (Map.Entry<String, List<Binding>> e : bindings.entrySet()) {
            // Match format of VMIntrinsicMethod.declaringClass
            String type = MetaUtil.internalNameToJava(e.getKey(), true, false).replace('.', '/');
            for (Binding binding : e.getValue()) {
                if (match(type, binding, intrinsic)) {
                    return binding.plugin;
                }
            }
        }
        return null;
    }

    public static ResolvedJavaMethod resolveIntrinsic(MetaAccessProvider metaAccess, VMIntrinsicMethod intrinsic) throws ClassNotFoundException {
        Class<?> c = Class.forName(intrinsic.declaringClass.replace('/', '.'), false, CheckGraalIntrinsics.class.getClassLoader());
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
        add(IGNORE,
                        // dead
                        "java/lang/Math.atan2(DD)D",
                        // Used during stack walking
                        "java/lang/Throwable.fillInStackTrace()Ljava/lang/Throwable;",
                        // Marker intrinsic id
                        "java/lang/invoke/MethodHandle.<compiledLambdaForm>*",
                        // Marker intrinsic id
                        "java/lang/invoke/MethodHandle.invoke*",
                        // Implemented through lowering
                        "java/lang/ref/Reference.get()Ljava/lang/Object;",
                        // Used during stack walk
                        "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                        // Only used by C1
                        "java/nio/Buffer.checkIndex(I)I",
                        // dead
                        "sun/misc/Unsafe.park(ZJ)V",
                        // dead
                        "sun/misc/Unsafe.prefetchRead(Ljava/lang/Object;J)V",
                        // dead
                        "sun/misc/Unsafe.prefetchReadStatic(Ljava/lang/Object;J)V",
                        // dead
                        "sun/misc/Unsafe.prefetchWrite(Ljava/lang/Object;J)V",
                        // dead
                        "sun/misc/Unsafe.prefetchWriteStatic(Ljava/lang/Object;J)V",
                        // dead
                        "sun/misc/Unsafe.unpark(Ljava/lang/Object;)V");

        add(TO_BE_INVESTIGATED,
                        // JDK 8
                        "java/lang/Double.doubleToLongBits(D)J",
                        "java/lang/Float.floatToIntBits(F)I",
                        "java/lang/Integer.toString(I)Ljava/lang/String;",
                        "java/lang/Math.decrementExact(I)I",
                        "java/lang/Math.decrementExact(J)J",
                        "java/lang/Math.incrementExact(I)I",
                        "java/lang/Math.incrementExact(J)J",
                        "java/lang/Math.max(II)I",
                        "java/lang/Math.min(II)I",
                        "java/lang/Math.negateExact(I)I",
                        "java/lang/Math.negateExact(J)J",
                        "java/lang/String.<init>(Ljava/lang/String;)V",
                        "java/lang/String.compareTo(Ljava/lang/String;)I",
                        "java/lang/String.indexOf(Ljava/lang/String;)I",
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
                        "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                        "java/util/Arrays.copyOf([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                        "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
                        "oracle/jrockit/jfr/Timing.counterTime()J",
                        "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                        "oracle/jrockit/jfr/VMJFR.threadID()I",
                        "sun/misc/Unsafe.copyMemory(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
                        "sun/nio/cs/ISO_8859_1$Encoder.encodeISOArray([CI[BII)I",
                        "sun/security/provider/DigestBase.implCompressMultiBlock([BII)I",
                        "sun/security/provider/SHA.implCompress([BI)V",
                        "sun/security/provider/SHA2.implCompress([BI)V",
                        "sun/security/provider/SHA5.implCompress([BI)V");

        add(TO_BE_INVESTIGATED,
                        // JDK 9
                        "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I",
                        "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V",
                        "java/lang/Math.fma(DDD)D",
                        "java/lang/Math.fma(FFF)F",
                        "java/lang/Object.notify()V",
                        "java/lang/Object.notifyAll()V",
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
                        "java/lang/StringUTF16.toBytes([CII)[B",
                        "java/lang/Thread.onSpinWait()V",
                        "java/lang/invoke/MethodHandleImpl.isCompileConstant(Ljava/lang/Object;)Z",
                        "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I",
                        "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I",
                        "java/math/BigInteger.implMulAdd([I[IIII)I",
                        "java/math/BigInteger.implSquareToLen([II[II)[I",
                        "java/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I",
                        "java/util/stream/Streams$RangeIntSpliterator.forEachRemaining(Ljava/util/function/IntConsumer;)V",
                        "java/util/zip/Adler32.updateByteBuffer(IJII)I",
                        "java/util/zip/Adler32.updateBytes(I[BII)I",
                        "jdk/internal/misc/Unsafe.allocateUninitializedArray0(Ljava/lang/Class;I)Ljava/lang/Object;",
                        "jdk/internal/misc/Unsafe.compareAndExchangeByteAcquire(Ljava/lang/Object;JBB)B",
                        "jdk/internal/misc/Unsafe.compareAndExchangeByteRelease(Ljava/lang/Object;JBB)B",
                        "jdk/internal/misc/Unsafe.compareAndExchangeByte(Ljava/lang/Object;JBB)B",
                        "jdk/internal/misc/Unsafe.compareAndExchangeIntAcquire(Ljava/lang/Object;JII)I",
                        "jdk/internal/misc/Unsafe.compareAndExchangeIntRelease(Ljava/lang/Object;JII)I",
                        "jdk/internal/misc/Unsafe.compareAndExchangeInt(Ljava/lang/Object;JII)I",
                        "jdk/internal/misc/Unsafe.compareAndExchangeLongAcquire(Ljava/lang/Object;JJJ)J",
                        "jdk/internal/misc/Unsafe.compareAndExchangeLongRelease(Ljava/lang/Object;JJJ)J",
                        "jdk/internal/misc/Unsafe.compareAndExchangeLong(Ljava/lang/Object;JJJ)J",
                        "jdk/internal/misc/Unsafe.compareAndExchangeObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                        "jdk/internal/misc/Unsafe.compareAndExchangeObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                        "jdk/internal/misc/Unsafe.compareAndExchangeObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                        "jdk/internal/misc/Unsafe.compareAndExchangeShortAcquire(Ljava/lang/Object;JSS)S",
                        "jdk/internal/misc/Unsafe.compareAndExchangeShortRelease(Ljava/lang/Object;JSS)S",
                        "jdk/internal/misc/Unsafe.compareAndExchangeShort(Ljava/lang/Object;JSS)S",
                        "jdk/internal/misc/Unsafe.compareAndSetByte(Ljava/lang/Object;JBB)Z",
                        "jdk/internal/misc/Unsafe.compareAndSetShort(Ljava/lang/Object;JSS)Z",
                        "jdk/internal/misc/Unsafe.copyMemory0(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
                        "jdk/internal/misc/Unsafe.getAndAddByte(Ljava/lang/Object;JB)B",
                        "jdk/internal/misc/Unsafe.getAndAddShort(Ljava/lang/Object;JS)S",
                        "jdk/internal/misc/Unsafe.getAndSetByte(Ljava/lang/Object;JB)B",
                        "jdk/internal/misc/Unsafe.getAndSetShort(Ljava/lang/Object;JS)S",
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
                        "jdk/internal/misc/Unsafe.park(ZJ)V",
                        "jdk/internal/misc/Unsafe.putBooleanOpaque(Ljava/lang/Object;JZ)V",
                        "jdk/internal/misc/Unsafe.putByteOpaque(Ljava/lang/Object;JB)V",
                        "jdk/internal/misc/Unsafe.putCharOpaque(Ljava/lang/Object;JC)V",
                        "jdk/internal/misc/Unsafe.putDoubleOpaque(Ljava/lang/Object;JD)V",
                        "jdk/internal/misc/Unsafe.putFloatOpaque(Ljava/lang/Object;JF)V",
                        "jdk/internal/misc/Unsafe.putIntOpaque(Ljava/lang/Object;JI)V",
                        "jdk/internal/misc/Unsafe.putLongOpaque(Ljava/lang/Object;JJ)V",
                        "jdk/internal/misc/Unsafe.putObjectOpaque(Ljava/lang/Object;JLjava/lang/Object;)V",
                        "jdk/internal/misc/Unsafe.putShortOpaque(Ljava/lang/Object;JS)V",
                        "jdk/internal/misc/Unsafe.unpark(Ljava/lang/Object;)V",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetBytePlain(Ljava/lang/Object;JBB)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetByteAcquire(Ljava/lang/Object;JBB)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetByteRelease(Ljava/lang/Object;JBB)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetByte(Ljava/lang/Object;JBB)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetIntPlain(Ljava/lang/Object;JII)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetIntAcquire(Ljava/lang/Object;JII)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetIntRelease(Ljava/lang/Object;JII)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetInt(Ljava/lang/Object;JII)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetLongPlain(Ljava/lang/Object;JJJ)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetLongAcquire(Ljava/lang/Object;JJJ)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetLongRelease(Ljava/lang/Object;JJJ)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetLong(Ljava/lang/Object;JJJ)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetObjectPlain(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetShortPlain(Ljava/lang/Object;JSS)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetShortAcquire(Ljava/lang/Object;JSS)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetShortRelease(Ljava/lang/Object;JSS)Z",
                        "jdk/internal/misc/Unsafe.weakCompareAndSetShort(Ljava/lang/Object;JSS)Z",
                        "jdk/internal/util/Preconditions.checkIndex(IILjava/util/function/BiFunction;)I",
                        "jdk/jfr/internal/JVM.counterTime()J",
                        "jdk/jfr/internal/JVM.getBufferWriter()Ljava/lang/Object;",
                        "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J",
                        "sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray([CI[BII)I",
                        "sun/security/provider/DigestBase.implCompressMultiBlock0([BII)I",
                        "sun/security/provider/SHA.implCompress0([BI)V",
                        "sun/security/provider/SHA2.implCompress0([BI)V",
                        "sun/security/provider/SHA5.implCompress0([BI)V");

        if (!getHostArchitectureName().equals("amd64")) {
            add(TO_BE_INVESTIGATED,
                            // Can we implement these on non-AMD64 platforms? C2 seems to.
                            "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");
            // JDK 9
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

        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        GraalHotSpotVMConfig config = rt.getVMConfig();

        /*
         * These are known to be implemented but the platform dependent conditions for when they are
         * enabled are complex so just ignore them all the time.
         */
        add(IGNORE,
                        "java/lang/Integer.bitCount(I)I",
                        "java/lang/Integer.numberOfLeadingZeros(I)I",
                        "java/lang/Integer.numberOfTrailingZeros(I)I",
                        "java/lang/Long.bitCount(J)I",
                        "java/lang/Long.numberOfLeadingZeros(J)I",
                        "java/lang/Long.numberOfTrailingZeros(J)I");

        if (!config.useCRC32Intrinsics) {
            // Registration of the CRC32 plugins is guarded by UseCRC32Intrinsics
            add(IGNORE, "java/util/zip/CRC32.update(II)I");
            if (JAVA_SPECIFICATION_VERSION < 9) {
                add(IGNORE,
                                "java/util/zip/CRC32.updateByteBuffer(IJII)I",
                                "java/util/zip/CRC32.updateBytes(I[BII)I");
            } else {
                add(IGNORE,
                                "java/util/zip/CRC32.updateByteBuffer0(IJII)I",
                                "java/util/zip/CRC32.updateBytes0(I[BII)I",
                                "java/util/zip/CRC32C.updateBytes(I[BII)I",
                                "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
            }
        } else {
            if (JAVA_SPECIFICATION_VERSION >= 9) {
                add(TO_BE_INVESTIGATED,
                                "java/util/zip/CRC32C.updateBytes(I[BII)I",
                                "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
            }
        }

        if (!config.useAESIntrinsics) {
            // Registration of the AES plugins is guarded by UseAESIntrinsics
            if (JAVA_SPECIFICATION_VERSION < 9) {
                add(IGNORE,
                                "com/sun/crypto/provider/AESCrypt.decryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.encryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.decrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.encrypt([BII[BI)I");
            } else {
                add(IGNORE,
                                "com/sun/crypto/provider/AESCrypt.implDecryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.implEncryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.implDecrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.implEncrypt([BII[BI)I");
            }
        }
        if (!config.useMultiplyToLenIntrinsic()) {
            // Registration of the AES plugins is guarded by UseAESIntrinsics
            if (JAVA_SPECIFICATION_VERSION < 9) {
                add(IGNORE, "java/math/BigInteger.multiplyToLen([II[II[I)[I");
            } else {
                add(IGNORE, "java/math/BigInteger.implMultiplyToLen([II[II[I)[I");
            }
        }
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
        Map<String, List<Binding>> bindings = invocationPlugins.getBindings(true);
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
