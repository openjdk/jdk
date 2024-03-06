/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.cp.share;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import vm.mlvm.share.ClassfileGenerator;

public class GenCPFullOfMT extends GenFullCP {

    public static void main(String[] args) {
        ClassfileGenerator.main(args);
    }

    @Override
    protected byte[] generateCommonData(byte[] bytes) {
        return super.generateCommonData(bytes);
    }

    @Override
    protected byte[] generateCPEntryData(byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);

        bytes = ClassFile.of().transform(cm,
                ClassTransform.endHandler(cb -> cb.withMethod("generateCPEntryData",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V")), ClassFile.ACC_PUBLIC,
                        mb -> mb.withCode(
                                cob -> {
                                    cob.ldc(MethodTypeDesc.ofDescriptor("(FIZ)V"));
                                    cob.pop();
                                    cob.return_();
                                }))));

        return bytes;
    }

}
