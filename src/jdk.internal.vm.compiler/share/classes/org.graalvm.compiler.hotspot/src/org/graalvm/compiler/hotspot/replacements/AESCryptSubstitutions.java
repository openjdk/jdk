/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.getArrayBaseOffset;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@code com.sun.crypto.provider.AESCrypt} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.AESCrypt", optional = true)
public class AESCryptSubstitutions {

    static final long kOffset;
    static final long lastKeyOffset;
    static final Class<?> AESCryptClass;

    /**
     * The AES block size is a constant 128 bits as defined by the
     * <a href="http://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.197.pdf">standard<a/>.
     */
    static final int AES_BLOCK_SIZE_IN_BYTES = 16;

    static {
        try {
            // Need to use the system class loader as com.sun.crypto.provider.AESCrypt
            // is normally loaded by the extension class loader which is not delegated
            // to by the JVMCI class loader.
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            AESCryptClass = Class.forName("com.sun.crypto.provider.AESCrypt", true, cl);
            kOffset = UnsafeAccess.UNSAFE.objectFieldOffset(AESCryptClass.getDeclaredField("K"));
            lastKeyOffset = UnsafeAccess.UNSAFE.objectFieldOffset(AESCryptClass.getDeclaredField("lastKey"));
        } catch (Exception ex) {
            throw new GraalError(ex);
        }
    }

    @MethodSubstitution(isStatic = false)
    static void encryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, true, false);
    }

    @MethodSubstitution(isStatic = false)
    static void implEncryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, true, false);
    }

    @MethodSubstitution(isStatic = false)
    static void decryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false, false);
    }

    @MethodSubstitution(isStatic = false)
    static void implDecryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false, false);
    }

    /**
     * Variation for platforms (e.g. SPARC) that need do key expansion in stubs due to compatibility
     * issues between Java key expansion and hardware crypto instructions.
     */
    @MethodSubstitution(value = "decryptBlock", isStatic = false)
    static void decryptBlockWithOriginalKey(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false, true);
    }

    /**
     * @see #decryptBlockWithOriginalKey(Object, byte[], int, byte[], int)
     */
    @MethodSubstitution(value = "implDecryptBlock", isStatic = false)
    static void implDecryptBlockWithOriginalKey(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false, true);
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset, boolean encrypt, boolean withOriginalKey) {
        checkArgs(in, inOffset, out, outOffset);
        Object realReceiver = PiNode.piCastNonNull(rcvr, AESCryptClass);
        Object kObject = RawLoadNode.load(realReceiver, kOffset, JavaKind.Object, LocationIdentity.any());
        Pointer kAddr = Word.objectToTrackedPointer(kObject).add(getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int));
        Word inAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(in, getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + inOffset));
        Word outAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(out, getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + outOffset));
        if (encrypt) {
            encryptBlockStub(ENCRYPT_BLOCK, inAddr, outAddr, kAddr);
        } else {
            if (withOriginalKey) {
                Object lastKeyObject = RawLoadNode.load(realReceiver, lastKeyOffset, JavaKind.Object, LocationIdentity.any());
                Pointer lastKeyAddr = Word.objectToTrackedPointer(lastKeyObject).add(getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte));
                decryptBlockWithOriginalKeyStub(DECRYPT_BLOCK_WITH_ORIGINAL_KEY, inAddr, outAddr, kAddr, lastKeyAddr);
            } else {
                decryptBlockStub(DECRYPT_BLOCK, inAddr, outAddr, kAddr);
            }
        }
    }

    /**
     * Perform null and array bounds checks for arguments to a cipher operation.
     */
    static void checkArgs(byte[] in, int inOffset, byte[] out, int outOffset) {
        if (probability(VERY_SLOW_PATH_PROBABILITY, inOffset < 0 || in.length - AES_BLOCK_SIZE_IN_BYTES < inOffset || outOffset < 0 || out.length - AES_BLOCK_SIZE_IN_BYTES < outOffset)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void encryptBlockStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptBlockStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptBlockWithOriginalKeyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer originalKey);
}
