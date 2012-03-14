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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.apple.internal.jobjc.generator.classes.MixedPrimitiveCoderClassFile;
import com.apple.internal.jobjc.generator.model.types.NType;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.utils.Fp.Pair;
import com.apple.jobjc.JObjCRuntime;

/**
 * Used to code two primitives of different 32/64 types.
 */
public class ComplexCoderDescriptor extends CoderDescriptor {
    static Map<Pair<NType,NType>, ComplexCoderDescriptor> cache = new HashMap<Pair<NType,NType>, ComplexCoderDescriptor>();
    static Set<MixedEncodingDescriptor> mixedEncodingDescriptors = new HashSet<MixedEncodingDescriptor>();

    public static Set<MixedEncodingDescriptor> getMixedEncoders() { return mixedEncodingDescriptors; }

    public static ComplexCoderDescriptor getCoderDescriptorFor(final NType nt32, final NType nt64) {
        Pair<NType,NType> cacheKey = new Pair(nt32, nt64);
        if(cache.containsKey(cacheKey)) return cache.get(cacheKey);

        final PrimitiveCoderDescriptor desc32 = PrimitiveCoderDescriptor.getCoderDescriptorFor((NPrimitive) nt32);
        final PrimitiveCoderDescriptor desc64 = PrimitiveCoderDescriptor.getCoderDescriptorFor((NPrimitive) nt64);

        final ComplexCoderDescriptor newDesc = nt32.equals(nt64) ? new ComplexCoderDescriptor(desc64) : new MixedEncodingDescriptor(desc32, desc64);
        cache.put(cacheKey, newDesc);
        if(newDesc instanceof MixedEncodingDescriptor)
            mixedEncodingDescriptors.add((MixedEncodingDescriptor) newDesc);

        return newDesc;
    }

    protected final PrimitiveCoderDescriptor desc64;

    public ComplexCoderDescriptor(final PrimitiveCoderDescriptor desc64) {
        super(desc64.coder, desc64.pushName, desc64.popName);
        this.desc64 = desc64;
    }

    public String getName() { return desc64.javaPrimitiveClazz.getName(); }
    @Override public String getDefaultReturnValue() { return desc64.defaultReturnValue; }
    public String getJavaObjectClass() { return desc64.javaObjectClazz.getName(); }
    public String getDefinition() { return get64CoderName(); }
    public String getCoderAccessor() { return get64CoderName(); }
    String get64CoderName() { return desc64.getCoderInstanceName(); }

    // ** Subclasses
    // -------------

    public static class MixedEncodingDescriptor extends ComplexCoderDescriptor {
        protected final PrimitiveCoderDescriptor desc32;

        public MixedEncodingDescriptor(final PrimitiveCoderDescriptor desc32, final PrimitiveCoderDescriptor desc64) {
            super(desc64);
            this.desc32 = desc32;
        }

        @Override public String getDefinition() { return JObjCRuntime.class.getName() + ".IS64 ? " + get64CoderName() + " : " + get32CoderName(); }
        @Override public String getCoderAccessor() { return MixedPrimitiveCoderClassFile.FULL_MULTI_CODER_CLASSNAME + "." + getMixedName(); }
        String get32CoderName() { return desc32.getCoderInstanceName(); }
        @Override public String getCoderInstanceName(){ return getCoderAccessor(); }
        @Override public String toString() { return getMixedName(); }

        public String getMixedName() {
            final String coder32Name = getBaseNameOfCoder(desc32.primitiveCoderName);
            final String coder64Name = getBaseNameOfCoder(desc64.primitiveCoderName);
            return coder32Name + coder64Name + "Coder";
        }

        static String getBaseNameOfCoder(final String coderName) { return coderName.substring(0, coderName.indexOf("Coder")); }
    }
}
