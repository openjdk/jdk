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

import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT;
import static org.graalvm.compiler.hotspot.replacements.UnsafeAccess.UNSAFE;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.word.Pointer;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@code com.sun.crypto.provider.CipherBlockChaining} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.CipherBlockChaining", optional = true)
public class CipherBlockChainingSubstitutions {

    private static final long embeddedCipherOffset;
    private static final long rOffset;
    private static final Class<?> cipherBlockChainingClass;
    private static final Class<?> feedbackCipherClass;
    static {
        try {
            // Need to use the system class loader as com.sun.crypto.provider.FeedbackCipher
            // is normally loaded by the extension class loader which is not delegated
            // to by the JVMCI class loader.
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            feedbackCipherClass = Class.forName("com.sun.crypto.provider.FeedbackCipher", true, cl);
            embeddedCipherOffset = UNSAFE.objectFieldOffset(feedbackCipherClass.getDeclaredField("embeddedCipher"));

            cipherBlockChainingClass = Class.forName("com.sun.crypto.provider.CipherBlockChaining", true, cl);
            rOffset = UNSAFE.objectFieldOffset(cipherBlockChainingClass.getDeclaredField("r"));
        } catch (Exception ex) {
            throw new GraalError(ex);
        }
    }

    @Fold
    static Class<?> getAESCryptClass() {
        return AESCryptSubstitutions.AESCryptClass;
    }

    @MethodSubstitution(isStatic = false)
    static int encrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, true, false);
            return inLength;
        } else {
            return encrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false, value = "implEncrypt")
    static int implEncrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, true, false);
            return inLength;
        } else {
            return implEncrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false)
    static int decrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (in != out && getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, false);
            return inLength;
        } else {
            return decrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false)
    static int implDecrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (in != out && getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, false);
            return inLength;
        } else {
            return implDecrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    /**
     * Variation for platforms (e.g. SPARC) that need do key expansion in stubs due to compatibility
     * issues between Java key expansion and hardware crypto instructions.
     */
    @MethodSubstitution(isStatic = false, value = "decrypt")
    static int decryptWithOriginalKey(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (in != out && getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, true);
            return inLength;
        } else {
            return decryptWithOriginalKey(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    /**
     * @see #decryptWithOriginalKey(Object, byte[], int, int, byte[], int)
     */
    @MethodSubstitution(isStatic = false, value = "implDecrypt")
    static int implDecryptWithOriginalKey(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object embeddedCipher = UnsafeLoadNode.load(realReceiver, embeddedCipherOffset, JavaKind.Object, LocationIdentity.any());
        if (in != out && getAESCryptClass().isInstance(embeddedCipher)) {
            Object aesCipher = getAESCryptClass().cast(embeddedCipher);
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, true);
            return inLength;
        } else {
            return implDecryptWithOriginalKey(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset, Object embeddedCipher, boolean encrypt, boolean withOriginalKey) {
        AESCryptSubstitutions.checkArgs(in, inOffset, out, outOffset);
        Object realReceiver = PiNode.piCastNonNull(rcvr, cipherBlockChainingClass);
        Object aesCipher = getAESCryptClass().cast(embeddedCipher);
        Object kObject = UnsafeLoadNode.load(aesCipher, AESCryptSubstitutions.kOffset, JavaKind.Object, LocationIdentity.any());
        Object rObject = UnsafeLoadNode.load(realReceiver, rOffset, JavaKind.Object, LocationIdentity.any());
        Pointer kAddr = Word.objectToTrackedPointer(kObject).add(getArrayBaseOffset(JavaKind.Int));
        Pointer rAddr = Word.objectToTrackedPointer(rObject).add(getArrayBaseOffset(JavaKind.Byte));
        Word inAddr = Word.unsigned(ComputeObjectAddressNode.get(in, getArrayBaseOffset(JavaKind.Byte) + inOffset));
        Word outAddr = Word.unsigned(ComputeObjectAddressNode.get(out, getArrayBaseOffset(JavaKind.Byte) + outOffset));
        if (encrypt) {
            encryptAESCryptStub(ENCRYPT, inAddr, outAddr, kAddr, rAddr, inLength);
        } else {
            if (withOriginalKey) {
                Object lastKeyObject = UnsafeLoadNode.load(aesCipher, AESCryptSubstitutions.lastKeyOffset, JavaKind.Object, LocationIdentity.any());
                Pointer lastKeyAddr = Word.objectToTrackedPointer(lastKeyObject).add(getArrayBaseOffset(JavaKind.Byte));
                decryptAESCryptWithOriginalKeyStub(DECRYPT_WITH_ORIGINAL_KEY, inAddr, outAddr, kAddr, rAddr, inLength, lastKeyAddr);
            } else {
                decryptAESCryptStub(DECRYPT, inAddr, outAddr, kAddr, rAddr, inLength);
            }
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void encryptAESCryptStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptAESCryptStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptAESCryptWithOriginalKeyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength, Pointer originalKey);
}
