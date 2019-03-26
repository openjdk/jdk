/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_INTRINSIC_CONTEXT;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions.aesCryptType;
import static org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions.embeddedCipherOffset;
import static org.graalvm.compiler.nodes.PiNode.piCastNonNull;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

@ClassSubstitution(className = "com.sun.crypto.provider.CounterMode", optional = true)
public class CounterModeSubstitutions {

    @MethodSubstitution(isStatic = false)
    static int implCrypt(Object receiver, byte[] in, int inOff, int len, byte[] out, int outOff) {
        Object realReceiver = piCastNonNull(receiver, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object embeddedCipher = RawLoadNode.load(realReceiver, embeddedCipherOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        Object aesCipher = piCastNonNull(embeddedCipher, aesCryptType(INJECTED_INTRINSIC_CONTEXT));

        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(in, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + inOff));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(out, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + outOff));
        Word usedPtr = WordFactory.unsigned(ComputeObjectAddressNode.get(realReceiver, usedOffset(INJECTED_INTRINSIC_CONTEXT)));

        int cntOffset = counterOffset(INJECTED_INTRINSIC_CONTEXT);
        int encCntOffset = encCounterOffset(INJECTED_INTRINSIC_CONTEXT);
        Object kObject = RawLoadNode.load(aesCipher, AESCryptSubstitutions.kOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        Object cntObj = RawLoadNode.load(realReceiver, cntOffset, JavaKind.Object, LocationIdentity.any());
        Object encCntObj = RawLoadNode.load(realReceiver, encCntOffset, JavaKind.Object, LocationIdentity.any());

        Word kPtr = Word.objectToTrackedPointer(kObject).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int));
        Word cntPtr = Word.objectToTrackedPointer(cntObj).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte));
        Word encCntPtr = Word.objectToTrackedPointer(encCntObj).add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte));

        return HotSpotBackend.counterModeAESCrypt(srcAddr, dstAddr, kPtr, cntPtr, len, encCntPtr, usedPtr);
    }

    static ResolvedJavaType counterModeType(IntrinsicContext context) {
        return HotSpotReplacementsUtil.getType(context, "Lcom/sun/crypto/provider/CounterMode;");
    }

    @Fold
    static int counterOffset(@InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getFieldOffset(counterModeType(context), "counter");
    }

    @Fold
    static int encCounterOffset(@InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getFieldOffset(counterModeType(context), "encryptedCounter");
    }

    @Fold
    static int usedOffset(@InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getFieldOffset(counterModeType(context), "used");
    }
}
