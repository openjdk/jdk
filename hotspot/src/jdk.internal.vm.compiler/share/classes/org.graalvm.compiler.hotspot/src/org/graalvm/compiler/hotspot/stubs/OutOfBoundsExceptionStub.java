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
import org.graalvm.compiler.hotspot.nodes.AllocaNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;

/**
 * Stub to allocate an {@link ArrayIndexOutOfBoundsException} thrown by a bytecode.
 */
public class OutOfBoundsExceptionStub extends CreateExceptionStub {

    public OutOfBoundsExceptionStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("createOutOfBoundsException", options, providers, linkage);
    }

    private static final int MAX_INT_STRING_SIZE = Integer.toString(Integer.MIN_VALUE).length();

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        switch (index) {
            case 1:
                return providers.getRegisters().getThreadRegister();
            case 2:
                int wordSize = providers.getWordTypes().getWordKind().getByteCount();
                // (MAX_INT_STRING_SIZE + 1) / wordSize, rounded up
                return MAX_INT_STRING_SIZE / wordSize + 1;
            default:
                throw GraalError.shouldNotReachHere("unknown parameter " + name + " at index " + index);
        }
    }

    @Snippet
    private static Object createOutOfBoundsException(int idx, @ConstantParameter Register threadRegister, @ConstantParameter int bufferSizeInWords) {
        Word buffer = AllocaNode.alloca(bufferSizeInWords);

        long number = idx;
        if (number < 0) {
            number = -number;
        }

        Word ptr = buffer.add(MAX_INT_STRING_SIZE);
        ptr.writeByte(0, (byte) 0);
        do {
            long digit = number % 10;
            number /= 10;

            ptr = ptr.subtract(1);
            ptr.writeByte(0, (byte) ('0' + digit));
        } while (number > 0);

        if (idx < 0) {
            ptr = ptr.subtract(1);
            ptr.writeByte(0, (byte) '-');
        }

        return createException(threadRegister, ArrayIndexOutOfBoundsException.class, ptr);
    }
}
