/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.hotspot.word.KlassPointer;

import jdk.vm.ci.code.Register;

/**
 */
public class ClassCastExceptionStub extends CreateExceptionStub {

    public ClassCastExceptionStub(HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("createClassCastException", providers, linkage);
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        GraalError.guarantee(index == 2, "unknown parameter %s at index %d", name, index);
        return providers.getRegisters().getThreadRegister();
    }

    @Snippet
    private static Object createClassCastException(Object object, KlassPointer targetKlass, @ConstantParameter Register threadRegister) {
        KlassPointer objKlass = HotSpotReplacementsUtil.loadHub(object);
        return createException(threadRegister, ClassCastException.class, objKlass, targetKlass);
    }
}
