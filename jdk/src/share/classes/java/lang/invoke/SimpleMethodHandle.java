/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A method handle whose behavior is determined only by its LambdaForm.
 * @author jrose
 */
final class SimpleMethodHandle extends MethodHandle {
    private SimpleMethodHandle(MethodType type, LambdaForm form) {
        super(type, form);
    }

    /*non-public*/ static SimpleMethodHandle make(MethodType type, LambdaForm form) {
        return new SimpleMethodHandle(type, form);
    }

    @Override
    MethodHandle bindArgument(int pos, char basicType, Object value) {
        MethodType type2 = type().dropParameterTypes(pos, pos+1);
        LambdaForm form2 = internalForm().bind(1+pos, BoundMethodHandle.SpeciesData.EMPTY);
        return BoundMethodHandle.bindSingle(type2, form2, basicType, value);
    }

    @Override
    MethodHandle dropArguments(MethodType srcType, int pos, int drops) {
        LambdaForm newForm = internalForm().addArguments(pos, srcType.parameterList().subList(pos, pos+drops));
        return new SimpleMethodHandle(srcType, newForm);
    }

    @Override
    MethodHandle permuteArguments(MethodType newType, int[] reorder) {
        LambdaForm form2 = internalForm().permuteArguments(1, reorder, basicTypes(newType.parameterList()));
        return new SimpleMethodHandle(newType, form2);
    }

    @Override
    MethodHandle copyWith(MethodType mt, LambdaForm lf) {
        return new SimpleMethodHandle(mt, lf);
    }

}
