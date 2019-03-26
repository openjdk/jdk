/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.Java8OrEarlier;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;

@ClassSubstitution(className = "sun.security.provider.SHA5", optional = true)
public class SHA5Substitutions {

    public static final String implCompressName = Java8OrEarlier ? "implCompress" : "implCompress0";

    @MethodSubstitution(isStatic = false)
    static void implCompress0(Object receiver, byte[] buf, int ofs) {
        Object realReceiver = PiNode.piCastNonNull(receiver, HotSpotReplacementsUtil.methodHolderClass(INJECTED_INTRINSIC_CONTEXT));
        Object state = RawLoadNode.load(realReceiver, stateOffset(INJECTED_INTRINSIC_CONTEXT), JavaKind.Object, LocationIdentity.any());
        Word bufAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(buf, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + ofs));
        Word stateAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(state, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
        HotSpotBackend.sha5ImplCompressStub(bufAddr, stateAddr);
    }

    @Fold
    static long stateOffset(@InjectedParameter IntrinsicContext context) {
        return HotSpotReplacementsUtil.getFieldOffset(HotSpotReplacementsUtil.methodHolderClass(context), "state");
    }

}
