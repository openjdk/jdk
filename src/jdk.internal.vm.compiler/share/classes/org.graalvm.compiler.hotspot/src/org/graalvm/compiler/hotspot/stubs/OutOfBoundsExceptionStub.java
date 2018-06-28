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


package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.stubs.StubUtil.printNumber;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.printString;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.AllocaNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;

/**
 * Stub to allocate an {@link ArrayIndexOutOfBoundsException} thrown by a bytecode.
 */
public class OutOfBoundsExceptionStub extends CreateExceptionStub {
    public OutOfBoundsExceptionStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("createOutOfBoundsException", options, providers, linkage);
    }

    // JDK-8201593: Print array length in ArrayIndexOutOfBoundsException.
    private static final boolean PRINT_LENGTH_IN_EXCEPTION = GraalServices.JAVA_SPECIFICATION_VERSION >= 11;
    private static final int MAX_INT_STRING_SIZE = Integer.toString(Integer.MIN_VALUE).length();
    private static final String STR_INDEX = "Index ";
    private static final String STR_OUTOFBOUNDSFORLENGTH = " out of bounds for length ";

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        switch (index) {
            case 2:
                return providers.getRegisters().getThreadRegister();
            case 3:
                int wordSize = providers.getWordTypes().getWordKind().getByteCount();
                int bytes;
                if (PRINT_LENGTH_IN_EXCEPTION) {
                    bytes = STR_INDEX.length() + STR_OUTOFBOUNDSFORLENGTH.length() + 2 * MAX_INT_STRING_SIZE;
                } else {
                    bytes = MAX_INT_STRING_SIZE;
                }
                // (required words for maximum length + nullbyte), rounded up
                return (bytes + 1) / wordSize + 1;
            case 4:
                return PRINT_LENGTH_IN_EXCEPTION;
            default:
                throw GraalError.shouldNotReachHere("unknown parameter " + name + " at index " + index);
        }
    }

    @Snippet
    private static Object createOutOfBoundsException(int idx, int length, @ConstantParameter Register threadRegister, @ConstantParameter int bufferSizeInWords,
                    @ConstantParameter boolean printLengthInException) {
        Word buffer = AllocaNode.alloca(bufferSizeInWords);
        Word ptr;
        if (printLengthInException) {
            ptr = printString(buffer, STR_INDEX);
            ptr = printNumber(ptr, idx);
            ptr = printString(ptr, STR_OUTOFBOUNDSFORLENGTH);
            ptr = printNumber(ptr, length);
        } else {
            ptr = printNumber(buffer, idx);
        }
        ptr.writeByte(0, (byte) 0);
        return createException(threadRegister, ArrayIndexOutOfBoundsException.class, buffer);
    }
}
