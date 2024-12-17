/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304837 8336585
 * @summary Testing BoundAttributes
 * @run junit BoundAttributeTest
 */
import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.DirectClassBuilder;
import jdk.internal.classfile.impl.UnboundAttribute;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.constantpool.ConstantPoolException;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundAttributeTest {

    @Test
    void testReadMethodParametersAttributeWithoutParameterName() {
        var cc = ClassFile.of();
        // build a simple method: void method(int)
        MethodTypeDesc methodTypeDesc = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);
        byte[] raw = cc.build(ClassDesc.of("TestClass"), builder -> {
            builder.withMethod("method", methodTypeDesc, 0, mb -> {
                mb.withCode(CodeBuilder::return_);
                // add a MethodParameters attribute without name for the parameter
                mb.with(MethodParametersAttribute.of(MethodParameterInfo.ofParameter(Optional.empty(), 0)));
            });
        });
        ClassModel model = cc.parse(raw);
        MethodParametersAttribute methodParametersAttribute = model.methods().get(0)
                .findAttribute(Attributes.methodParameters())
                .orElseThrow(() -> new AssertionFailedError("Attribute not present"));
        // MethodParametersAttribute#parameters() materializes the parameters
        List<MethodParameterInfo> parameters = assertDoesNotThrow(methodParametersAttribute::parameters);
        assertTrue(parameters.get(0).name().isEmpty());
    }

    @Test
    void testBadEntryTypeInList() {
        var cf = ClassFile.of();

        // Craft an attribute list with index to badly typed attributes
        var bytes = cf.build(ClassDesc.of("Test"), clb -> {
            var cp = clb.constantPool();
            var oneClassString = cp.utf8Entry("Test$Ape");
            var oneClass = cp.classEntry(oneClassString);
            ((DirectClassBuilder) clb).writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.nestMembers()) {
                @Override
                public void writeBody(BufWriterImpl b) {
                    b.writeU2(2);
                    b.writeIndex(oneClass);
                    b.writeIndex(oneClassString);
                }

                @Override
                public Utf8Entry attributeName() {
                    return cp.utf8Entry(Attributes.NAME_NEST_MEMBERS);
                }
            });
        });

        var nm = cf.parse(bytes).findAttribute(Attributes.nestMembers()).orElseThrow();
        Assertions.assertThrows(ConstantPoolException.class, () -> {
            int sum = 0;
            // this should throw CPE upon encountering non-ClassEntry
            for (var member : nm.nestMembers()) {
                sum += member.index();
            }
        });
    }
}
