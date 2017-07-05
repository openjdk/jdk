/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.options.Options;

class SpecializedMethodChooser {
    /** Should specialized function and specialized constructors for the builtin be used if available? */
    private static final boolean DISABLE_SPECIALIZATION = Options.getBooleanProperty("nashorn.scriptfunction.specialization.disable");

    static MethodHandle candidateWithLowestWeight(final MethodType descType, final MethodHandle initialCandidate, final MethodHandle[] specs) {
        if (DISABLE_SPECIALIZATION || specs == null) {
            return initialCandidate;
        }

        int          minimumWeight = Integer.MAX_VALUE;
        MethodHandle candidate     = initialCandidate;

        for (final MethodHandle spec : specs) {
            final MethodType specType = spec.type();

            if (!typeCompatible(descType, specType)) {
                continue;
            }

            //return type is ok. we want a wider or equal one for our callsite.
            final int specWeight = weigh(specType);
            if (specWeight < minimumWeight) {
                candidate = spec;
                minimumWeight = specWeight;
            }
        }

        return candidate;
    }

    private static boolean typeCompatible(final MethodType desc, final MethodType spec) {
        //spec must fit in desc
        final Class<?>[] dparray = desc.parameterArray();
        final Class<?>[] sparray = spec.parameterArray();

        if (dparray.length != sparray.length) {
            return false;
        }

        for (int i = 0; i < dparray.length; i++) {
            final Type dp = Type.typeFor(dparray[i]);
            final Type sp = Type.typeFor(sparray[i]);

            if (dp.isBoolean()) {
                return false; //don't specialize on booleans, we have the "true" vs int 1 ambiguity in resolution
            }

            //specialization arguments must be at least as wide as dp, if not wider
            if (Type.widest(dp, sp) != sp) {
                //e.g. specialization takes double and callsite says "object". reject.
                //but if specialization says double and callsite says "int" or "long" or "double", that's fine
                return false;
            }
        }

        return true; // anything goes for return type, take the convenient one and it will be upcasted thru dynalink magic.
    }

    private static int weigh(final MethodType t) {
        int weight = Type.typeFor(t.returnType()).getWeight();
        for (final Class<?> paramType : t.parameterArray()) {
            final int pweight = Type.typeFor(paramType).getWeight();
            weight += pweight;
        }
        return weight;
    }
}
