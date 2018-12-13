/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_INTRINSIC_CONTEXT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT;
import static org.graalvm.compiler.nodes.PiNode.piCastNonNull;
import static org.graalvm.compiler.nodes.java.InstanceOfNode.doInstanceof;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

// JaCoCo Exclude

/**
 * Substitutions for {@code com.sun.crypto.provider.CipherBlockChaining} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.CipherBlockChaining", optional = true)
public class CipherBlockChainingSubstitutions {

    @Fold
    static ResolvedJavaType aesCryptType(@Fold.InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getType(context, "Lcom/sun/crypto/provider/AESCrypt;");
    }

    @MethodSubstitution(isStatic = false)
    static int encrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, true, false);
            return inLength;
        } else {
            return encrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @Fold
    static long embeddedCipherOffset(@Fold.InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getFieldOffset(HotSpotReplacementsUtil.getType(context, "Lcom/sun/crypto/provider/FeedbackCipher;"), "embeddedCipher");
    }

    @Fold
    static long rOffset(@Fold.InjectedParameter IntrinsicContext intrinsicContext) {
        return HotSpotReplacementsUtil.getFieldOffset(HotSpotReplacementsUtil.methodHolderClass(intrinsicContext), "r");
    }

    @MethodSubstitution(isStatic = false, value = "implEncrypt")
    static int implEncrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, true, false);
            return inLength;
        } else {
            return implEncrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false)
    static int decrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (in != out && doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, false);
            return inLength;
        } else {
            return decrypt(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false)
    static int implDecrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (in != out && doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
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
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (in != out && doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
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
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        if (in != out && doInstanceof(aesCryptType(INJECTED_INTRINSIC_CONTEXT), embeddedCipher)) {
            Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
            crypt(realReceiver, in, inOffset, inLength, out, outOffset, aesCipher, false, true);
            return inLength;
        } else {
            return implDecryptWithOriginalKey(realReceiver, in, inOffset, inLength, out, outOffset);
        }
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset, Object embeddedCipher, boolean encrypt, boolean withOriginalKey) {
        AESCryptSubstitutions.checkArgs(in, inOffset, out, outOffset);
        Object realReceiver = piCastNonNull(rcvr, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));
        Object kObject = RawLoadNode.load(aesCipher, AESCryptSubstitutions.kOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        Object rObject = RawLoadNode.load(realReceiver, rOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        Pointer kAddr = Word.objectToTrackedPointer(kObject).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int));
        Pointer rAddr = Word.objectToTrackedPointer(rObject).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte));
        Word inAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(in, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + inOffset));
        Word outAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(out, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + outOffset));
        if (encrypt) {
            encryptAESCryptStub(ENCRYPT, inAddr, outAddr, kAddr, rAddr, inLength);
        } else {
            if (withOriginalKey) {
                Object lastKeyObject = RawLoadNode.load(aesCipher, AESCryptSubstitutions.lastKeyOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
                Pointer lastKeyAddr = Word.objectToTrackedPointer(lastKeyObject).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte));
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
