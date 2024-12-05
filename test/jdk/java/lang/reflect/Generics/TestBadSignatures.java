/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6832374 7052898
 * @summary Test bad signatures get a GenericSignatureFormatError thrown.
 * @author Joseph D. Darcy
 * @library /test/lib
 */

import jdk.test.lib.ByteCodeLoader;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.*;

import static java.lang.constant.ConstantDescs.MTD_void;

public class TestBadSignatures {
    public static void main(String[] args) throws Exception {
        String[] badSignatures = {
            // Missing ":" after first type bound
            "<T:Lfoo/tools/nsc/symtab/Names;Lfoo/tools/nsc/symtab/Symbols;",

            // Arrays improperly indicated for exception information
            "<E:Ljava/lang/Exception;>(TE;[Ljava/lang/RuntimeException;)V^[TE;",
        };

        int i = 0;
        for(String badSig : badSignatures) {
            var className = "BadSignature" + i;
            var bytes = ClassFile.of().build(ClassDesc.of(className), clb ->
                    clb.withMethod("test", MTD_void, 0, mb -> mb
                            .withCode(CodeBuilder::return_)
                            .with(SignatureAttribute.of(clb.constantPool().utf8Entry(badSig)))));

            var cl = ByteCodeLoader.load(className, bytes);
            var method = cl.getDeclaredMethod("test");
            try {
                method.getGenericParameterTypes();
                throw new RuntimeException("Expected GenericSignatureFormatError for " +
                                           badSig);
            } catch(GenericSignatureFormatError gsfe) {
                System.out.println(gsfe.toString()); // Expected
            }
        }
    }
}
