/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Formatter;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Binding;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Checks the intrinsics implemented by Graal against the set of intrinsics declared by HotSpot. The
 * purpose of this test is to detect when new intrinsics are added to HotSpot and process them
 * appropriately in Graal. This will be achieved by working through {@link #toBeInvestigated} and
 * either implementing the intrinsic or moving it to {@link #ignore} .
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
     * The HotSpot intrinsics that:
     * <ul>
     * <li>will never implemented by Graal (comments must explain why)</li>
     * <li>are implemented without {@link InvocationPlugin}s, or</li>
     * <li>whose {@link InvocationPlugin} registration is guarded by a condition that is false in
     * the current VM context.</li>
     * </ul>
     */
    public final Set<String> ignore = new TreeSet<>();

    /**
     * The HotSpot intrinsics whose {@link InvocationPlugin} registration is guarded by a condition
     * too complex to duplicate here.
     * </ul>
     */
    public final Set<String> complexGuard = new TreeSet<>();

    /**
     * The HotSpot intrinsics implemented downstream.
     * </ul>
     */
    public final Set<String> downstream = new TreeSet<>();

    /**
     * The HotSpot intrinsics yet to be implemented or moved to {@link #ignore}.
     */
    public final Set<String> toBeInvestigated = new TreeSet<>();

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

    public final HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
    public final Architecture arch = rt.getHostBackend().getTarget().arch;
    public final GraalHotSpotVMConfig config = rt.getVMConfig();

    public CheckGraalIntrinsics() {
        // These are dead
        add(ignore,
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
        add(ignore,
                        "java/lang/Throwable.fillInStackTrace()Ljava/lang/Throwable;");

        // These are only used for the security handling during stack walking
        add(ignore,
                        "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

        // These are marker intrinsic ids only
        add(ignore,
                        "java/lang/invoke/MethodHandle.<compiledLambdaForm>*",
                        "java/lang/invoke/MethodHandle.invoke*");

        // These are implemented through lowering
        add(ignore,
                        "java/lang/ref/Reference.get()Ljava/lang/Object;");

        // These are only used by C1
        add(ignore,
                        "java/nio/Buffer.checkIndex(I)I");

        // These do general compiler optimizations and convert min/max to cmov instructions. We are
        // ignoring them as cmovs are not necessarily beneficial.
        add(ignore,
                        "java/lang/Math.max(II)I",
                        "java/lang/Math.min(II)I");

        // These are known to be implemented down stream
        add(downstream,
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

        add(complexGuard,
                        "java/lang/Integer.bitCount(I)I",
                        "java/lang/Integer.numberOfLeadingZeros(I)I",
                        "java/lang/Integer.numberOfTrailingZeros(I)I",
                        "java/lang/Long.bitCount(J)I",
                        "java/lang/Long.numberOfLeadingZeros(J)I",
                        "java/lang/Long.numberOfTrailingZeros(J)I");

        // Relevant for Java flight recorder
        add(toBeInvestigated,
                        "oracle/jrockit/jfr/Timing.counterTime()J",
                        "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                        "oracle/jrockit/jfr/VMJFR.threadID()I");

        add(toBeInvestigated,
                        // Similar to addExact
                        "java/lang/Math.negateExact(I)I",
                        // Similar to addExact
                        "java/lang/Math.negateExact(J)J",
                        // HotSpot MacroAssembler-based intrinsic
                        "java/lang/String.indexOf(Ljava/lang/String;)I",
                        // Can share most implementation parts with with
                        // Unsafe.allocateUninitializedArray0
                        "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                        // HotSpot MacroAssembler-based intrinsic
                        "sun/nio/cs/ISO_8859_1$Encoder.encodeISOArray([CI[BII)I",
                        // Stub based intrinsics but implementation seems complex in C2
                        "sun/security/provider/DigestBase.implCompressMultiBlock([BII)I");

        // See JDK-8207146.
        String oopName = isJDK12OrHigher() ? "Reference" : "Object";

        if (isJDK9OrHigher()) {
            // Relevant for Java flight recorder
            add(toBeInvestigated,
                            "jdk/jfr/internal/JVM.counterTime()J",
                            "jdk/jfr/internal/JVM.getBufferWriter()Ljava/lang/Object;",
                            "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J");

            add(toBeInvestigated,
                            // Some logic and a stub call
                            "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I",
                            // Stub and very little logic
                            "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V",
                            // HotSpot MacroAssembler-based intrinsic
                            "java/lang/Math.fma(DDD)D",
                            // HotSpot MacroAssembler-based intrinsic
                            "java/lang/Math.fma(FFF)F",
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
                            // Emits a slow and a fast path and some dispatching logic
                            "jdk/internal/misc/Unsafe.allocateUninitializedArray0(Ljava/lang/Class;I)Ljava/lang/Object;",

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

            add(toBeInvestigated,
                            // Mapped to compareAndExchange*
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteAcquire(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteRelease(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntAcquire(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntRelease(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongAcquire(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongRelease(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchange" + oopName + "Acquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchange" + oopName + "Release(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortAcquire(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortRelease(Ljava/lang/Object;JSS)S",

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
                            "jdk/internal/misc/Unsafe.weakCompareAndSet" + oopName + "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSet" + oopName + "Acquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSet" + oopName + "Plain(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSet" + oopName + "Release(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShort(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortAcquire(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortPlain(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSetShortRelease(Ljava/lang/Object;JSS)Z");

            // Compact string support - HotSpot MacroAssembler-based intrinsic or complex C2 logic.
            add(toBeInvestigated,
                            "java/lang/StringCoding.hasNegatives([BII)Z",
                            "java/lang/StringCoding.implEncodeISOArray([BI[BII)I",
                            "java/lang/StringLatin1.indexOf([B[B)I",
                            "java/lang/StringUTF16.getChar([BI)C",
                            "java/lang/StringUTF16.getChars([BII[CI)V",
                            "java/lang/StringUTF16.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOf([B[B)I",
                            "java/lang/StringUTF16.indexOfChar([BIII)I",
                            "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                            "java/lang/StringUTF16.indexOfLatin1([B[B)I",
                            "java/lang/StringUTF16.putChar([BII)V",
                            "java/lang/StringUTF16.toBytes([CII)[B");
            // These are handled through an intrinsic for String.equals itself
            add(ignore,
                            "java/lang/StringLatin1.equals([B[B)Z",
                            "java/lang/StringUTF16.equals([B[B)Z");
        }

        if (isJDK10OrHigher()) {
            add(toBeInvestigated,
                            "java/lang/Math.multiplyHigh(JJ)J",
                            "jdk/internal/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I");
        }

        if (isJDK11OrHigher()) {
            // Relevant for Java flight recorder
            add(toBeInvestigated,
                            "java/util/Base64$Encoder.encodeBlock([BII[BIZ)V",
                            "jdk/jfr/internal/JVM.getEventWriter()Ljava/lang/Object;");
        }

        if (isJDK12OrHigher()) {
            add(toBeInvestigated,
                            "java/lang/CharacterDataLatin1.isDigit(I)Z",
                            "java/lang/CharacterDataLatin1.isLowerCase(I)Z",
                            "java/lang/CharacterDataLatin1.isUpperCase(I)Z",
                            "java/lang/CharacterDataLatin1.isWhitespace(I)Z");
        }

        if (!config.inlineNotify()) {
            add(ignore, "java/lang/Object.notify()V");
        }
        if (!config.inlineNotifyAll()) {
            add(ignore, "java/lang/Object.notifyAll()V");
        }

        if (!(arch instanceof AMD64)) {
            // Can we implement these on non-AMD64 platforms? C2 seems to.
            add(toBeInvestigated,
                            "java/lang/String.compareTo(Ljava/lang/String;)I",
                            "java/lang/StringLatin1.inflate([BI[BII)V",
                            "java/lang/StringLatin1.inflate([BI[CII)V",
                            "java/lang/StringUTF16.compress([BI[BII)I",
                            "java/lang/StringUTF16.compress([CI[BII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByte(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShort(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.compareAndSetByte(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.compareAndSetShort(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.getAndAddByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndAddShort(Ljava/lang/Object;JS)S",
                            "jdk/internal/misc/Unsafe.getAndSetByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndSetShort(Ljava/lang/Object;JS)S",
                            "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSet" + oopName + "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");

            if (isJDK9OrHigher()) {
                if (!(arch instanceof AArch64)) {
                    add(toBeInvestigated,
                                    "java/lang/StringLatin1.compareTo([B[B)I",
                                    "java/lang/StringLatin1.compareToUTF16([B[B)I",
                                    "java/lang/StringUTF16.compareTo([B[B)I",
                                    "java/lang/StringUTF16.compareToLatin1([B[B)I",
                                    "jdk/internal/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                                    "jdk/internal/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                                    "jdk/internal/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                                    "jdk/internal/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                                    "jdk/internal/misc/Unsafe.getAndSet" + oopName + "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");
                }
                add(toBeInvestigated,
                                "java/lang/Thread.onSpinWait()V",
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

        /*
         * The intrinsics down here are known to be implemented but they are not always enabled on
         * the HotSpot side (e.g., because they require certain CPU features). So, we are ignoring
         * them if the HotSpot config tells us that they can't be used.
         */

        // CRC32 intrinsics
        if (!config.useCRC32Intrinsics) {
            add(ignore, "java/util/zip/CRC32.update(II)I");
            if (isJDK9OrHigher()) {
                add(ignore,
                                "java/util/zip/CRC32.updateByteBuffer0(IJII)I",
                                "java/util/zip/CRC32.updateBytes0(I[BII)I");
            } else {
                add(ignore,
                                "java/util/zip/CRC32.updateByteBuffer(IJII)I",
                                "java/util/zip/CRC32.updateBytes(I[BII)I");
            }
        }

        // CRC32C intrinsics
        if (!config.useCRC32CIntrinsics) {
            add(ignore,
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
        }

        // AES intrinsics
        if (!config.useAESIntrinsics) {
            if (isJDK9OrHigher()) {
                add(ignore,
                                "com/sun/crypto/provider/AESCrypt.implDecryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.implEncryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.implDecrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.implEncrypt([BII[BI)I");
            } else {
                add(ignore,
                                "com/sun/crypto/provider/AESCrypt.decryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/AESCrypt.encryptBlock([BI[BI)V",
                                "com/sun/crypto/provider/CipherBlockChaining.decrypt([BII[BI)I",
                                "com/sun/crypto/provider/CipherBlockChaining.encrypt([BII[BI)I");
            }
        }

        // BigInteger intrinsics
        if (!config.useMultiplyToLenIntrinsic()) {
            if (isJDK9OrHigher()) {
                add(ignore, "java/math/BigInteger.implMultiplyToLen([II[II[I)[I");
            } else {
                add(ignore, "java/math/BigInteger.multiplyToLen([II[II[I)[I");
            }
        }
        if (!config.useMulAddIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMulAdd([I[IIII)I");
        }
        if (!config.useMontgomeryMultiplyIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I");
        }
        if (!config.useMontgomerySquareIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I");
        }
        if (!config.useSquareToLenIntrinsic()) {
            add(ignore, "java/math/BigInteger.implSquareToLen([II[II)[I");
        }

        // SHA intrinsics
        if (!config.useSHA1Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(ignore, "sun/security/provider/SHA.implCompress0([BI)V");
            } else {
                add(ignore, "sun/security/provider/SHA.implCompress([BI)V");
            }
        }
        if (!config.useSHA256Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(ignore, "sun/security/provider/SHA2.implCompress0([BI)V");
            } else {
                add(ignore, "sun/security/provider/SHA2.implCompress([BI)V");
            }
        }
        if (!config.useSHA512Intrinsics()) {
            if (isJDK9OrHigher()) {
                add(ignore, "sun/security/provider/SHA5.implCompress0([BI)V");
            } else {
                add(ignore, "sun/security/provider/SHA5.implCompress([BI)V");
            }
        }
    }

    private static boolean isJDK9OrHigher() {
        return GraalServices.JAVA_SPECIFICATION_VERSION >= 9;
    }

    private static boolean isJDK10OrHigher() {
        return GraalServices.JAVA_SPECIFICATION_VERSION >= 10;
    }

    private static boolean isJDK11OrHigher() {
        return GraalServices.JAVA_SPECIFICATION_VERSION >= 11;
    }

    private static boolean isJDK12OrHigher() {
        return GraalServices.JAVA_SPECIFICATION_VERSION >= 12;
    }

    public interface Refiner {
        void refine(CheckGraalIntrinsics checker);
    }

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException {
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();

        HotSpotVMConfigStore store = config.getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();

        for (Refiner refiner : ServiceLoader.load(Refiner.class)) {
            refiner.refine(this);
        }

        List<String> missing = new ArrayList<>();
        List<String> mischaracterizedAsToBeInvestigated = new ArrayList<>();
        List<String> mischaracterizedAsIgnored = new ArrayList<>();
        EconomicMap<String, List<Binding>> bindings = invocationPlugins.getBindings(true);
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            InvocationPlugin plugin = findPlugin(bindings, intrinsic);
            String m = String.format("%s.%s%s", intrinsic.declaringClass, intrinsic.name, intrinsic.descriptor);
            if (plugin == null) {
                ResolvedJavaMethod method = resolveIntrinsic(providers.getMetaAccess(), intrinsic);
                if (method != null) {
                    IntrinsicMethod intrinsicMethod = providers.getConstantReflection().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
                    if (intrinsicMethod != null) {
                        continue;
                    }
                }
                if (!toBeInvestigated.contains(m) && !ignore.contains(m) && !complexGuard.contains(m) && !downstream.contains(m)) {
                    missing.add(m);
                }
            } else {
                if (toBeInvestigated.contains(m)) {
                    mischaracterizedAsToBeInvestigated.add(m);
                } else if (ignore.contains(m)) {
                    mischaracterizedAsIgnored.add(m);
                }
            }
        }

        Formatter errorMsgBuf = new Formatter();
        if (!missing.isEmpty()) {
            Collections.sort(missing);
            String missingString = missing.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("missing Graal intrinsics for:%n    %s%n", missingString);
        }
        if (!mischaracterizedAsToBeInvestigated.isEmpty()) {
            Collections.sort(mischaracterizedAsToBeInvestigated);
            String missingString = mischaracterizedAsToBeInvestigated.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as toBeInvestigated:%n    %s%n", missingString);
        }
        if (!mischaracterizedAsIgnored.isEmpty()) {
            Collections.sort(mischaracterizedAsIgnored);
            String missingString = mischaracterizedAsIgnored.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as IGNORED:%n    %s%n", missingString);
        }
        String errorMsg = errorMsgBuf.toString();
        if (!errorMsg.isEmpty()) {
            fail(errorMsg);
        }
    }
}
