/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.apple.internal.jobjc.generator.model.coders;

import java.util.HashMap;
import java.util.Map;

import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.jobjc.Coder;
import com.apple.jobjc.PrimitiveCoder;
import com.apple.jobjc.PrimitiveCoder.BoolCoder;
import com.apple.jobjc.PrimitiveCoder.SCharCoder;
import com.apple.jobjc.PrimitiveCoder.SIntCoder;
import com.apple.jobjc.PrimitiveCoder.SLongCoder;
import com.apple.jobjc.PrimitiveCoder.SLongLongCoder;
import com.apple.jobjc.PrimitiveCoder.SShortCoder;
import com.apple.jobjc.PrimitiveCoder.UCharCoder;
import com.apple.jobjc.PrimitiveCoder.UIntCoder;
import com.apple.jobjc.PrimitiveCoder.ULongCoder;
import com.apple.jobjc.PrimitiveCoder.ULongLongCoder;
import com.apple.jobjc.PrimitiveCoder.UShortCoder;
import com.apple.internal.jobjc.generator.Utils;

public class PrimitiveCoderDescriptor extends CoderDescriptor {
    static Map<Character, PrimitiveCoderDescriptor> descriptors = new HashMap<Character, PrimitiveCoderDescriptor>();

    public static PrimitiveCoderDescriptor getCoderDescriptorFor(NPrimitive nt) {
        return getCoderDescriptorFor(nt.type);
    }

    public static PrimitiveCoderDescriptor getCoderDescriptorFor(char c) {
        final PrimitiveCoderDescriptor desc = descriptors.get(c);
        if (desc != null) return desc;
        final PrimitiveCoderDescriptor newDesc = createCoderDescriptorFor(c);
        descriptors.put(c, newDesc);
        return newDesc;
    }

    public static PrimitiveCoderDescriptor createCoderDescriptorFor(final char encoding) {
        switch(encoding) {
            case 'B': return new PrimitiveCoderDescriptor(BoolCoder.INST, "false");

            case 'c': return new PrimitiveCoderDescriptor(SCharCoder.INST, "0");
            case 'C': return new PrimitiveCoderDescriptor(UCharCoder.INST, "0");

            case 's': return new PrimitiveCoderDescriptor(SShortCoder.INST, "0");
            case 'S': return new PrimitiveCoderDescriptor(UShortCoder.INST, "0");

            case 'i': return new PrimitiveCoderDescriptor(SIntCoder.INST, "0");
            case 'I': return new PrimitiveCoderDescriptor(UIntCoder.INST, "0");

            case 'l': return new PrimitiveCoderDescriptor(SLongCoder.INST, "0");
            case 'L': return new PrimitiveCoderDescriptor(ULongCoder.INST, "0", "x86_64: no suitable Java primitive for unsigned long.");
            case 'q': return new PrimitiveCoderDescriptor(SLongLongCoder.INST, "0");
            case 'Q': return new PrimitiveCoderDescriptor(ULongLongCoder.INST, "0", "x86_64: no suitable Java primitive for unsigned long long.");

            case 'f': return new PrimitiveCoderDescriptor(PrimitiveCoder.FloatCoder.INST, "0");
            case 'd': return new PrimitiveCoderDescriptor(PrimitiveCoder.DoubleCoder.INST, "0");
            default: throw new RuntimeException("unknown encoding: " + encoding);
        }
    }

    public final Class<?> javaPrimitiveClazz;
    final Class<?> javaObjectClazz;
    final String defaultReturnValue;
    final String primitiveCoderName;
    final String _mismatchMessage;

    public PrimitiveCoderDescriptor(final Coder coder, final String defaultRetVal) {
        this(coder, defaultRetVal, null);
    }

    public PrimitiveCoderDescriptor(final Coder coder,
            final String defaultReturnValue, final String mismatchMessage) {
        super(coder, "push", "pop" + Utils.capitalize(coder.getJavaPrimitive().getSimpleName()));
        this.javaPrimitiveClazz = coder.getJavaPrimitive();
        this.javaObjectClazz = coder.getJavaClass();
        this.defaultReturnValue = defaultReturnValue;
        this.primitiveCoderName = coder.getClass().getSimpleName();
        this._mismatchMessage = mismatchMessage;
    }

    @Override public PrimitiveCoder getCoder(){ return (PrimitiveCoder) super.getCoder(); }
    @Override public String mismatchMessage(){ return _mismatchMessage; }
    @Override public String getDefaultReturnValue() { return defaultReturnValue; }
}
