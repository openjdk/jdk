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
package org.graalvm.compiler.nodes.util;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider.ConstantFieldTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public class ConstantFoldUtil {

    public static ConstantNode tryConstantFold(ConstantFieldProvider fieldProvider, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, ResolvedJavaField field,
                    JavaConstant receiver, OptionValues options) {
        if (!field.isStatic()) {
            if (receiver == null || receiver.isNull()) {
                return null;
            }
        }

        return fieldProvider.readConstantField(field, new ConstantFieldTool<ConstantNode>() {

            @Override
            public JavaConstant readValue() {
                return constantReflection.readFieldValue(field, receiver);
            }

            @Override
            public JavaConstant getReceiver() {
                return receiver;
            }

            @Override
            public ConstantNode foldConstant(JavaConstant ret) {
                if (ret != null) {
                    return ConstantNode.forConstant(ret, metaAccess);
                } else {
                    return null;
                }
            }

            @Override
            public ConstantNode foldStableArray(JavaConstant ret, int stableDimensions, boolean isDefaultStable) {
                if (ret != null) {
                    return ConstantNode.forConstant(ret, stableDimensions, isDefaultStable, metaAccess);
                } else {
                    return null;
                }
            }

            @Override
            public OptionValues getOptions() {
                return options;
            }
        });
    }
}
