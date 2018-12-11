/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_VMCONFIG;

import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopySnippets;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.WordFactory;

public class HotSpotArraycopySnippets extends ArrayCopySnippets {

    @Override
    public Pointer loadHub(Object nonNullSrc) {
        return HotSpotReplacementsUtil.loadHub(nonNullSrc).asWord();
    }

    @Override
    public Word getSuperCheckOffset(Pointer destElemKlass) {
        return WordFactory.signed(destElemKlass.readInt(HotSpotReplacementsUtil.superCheckOffsetOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION));
    }

    @Override
    public int getReadLayoutHelper(Pointer srcHub) {
        return HotSpotReplacementsUtil.readLayoutHelper(KlassPointer.fromWord(srcHub));
    }

    @Override
    public Pointer getDestElemClass(Pointer destKlassPointer) {
        KlassPointer destKlass = (KlassPointer.fromWord(destKlassPointer));
        return destKlass.readKlassPointer(HotSpotReplacementsUtil.arrayClassElementOffset(INJECTED_VMCONFIG),
                        HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION).asWord();
    }

    @Override
    protected int heapWordSize() {
        return HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG);
    }
}
