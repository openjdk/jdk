/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8347472
 * @summary Testing Attribute behavior for those appearing on CodeModel
 * @run junit AttributeInCodeTest
 */
class AttributeInCodeTest {

    static final String STRANGE_ATTRIBUTE_NAME = "StrangeAttribute";
    static class StrangeAttribute extends CustomAttribute<StrangeAttribute> {
        private final Utf8Entry name;

        StrangeAttribute(Utf8Entry name) {
            super(STRANGE_ATTRIBUTE_MAPPER);
            this.name = name;
        }

        @Override
        public Utf8Entry attributeName() {
            return name;
        }
    }

    static final AttributeMapper<StrangeAttribute> STRANGE_ATTRIBUTE_MAPPER = new AttributeMapper<>() {

        @Override
        public String name() {
            return STRANGE_ATTRIBUTE_NAME;
        }

        @Override
        public StrangeAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new StrangeAttribute(cf.readEntry(pos - 6, Utf8Entry.class));
        }

        @Override
        public void writeAttribute(BufWriter buf, StrangeAttribute attr) {
            buf.writeIndex(attr.name);
            buf.writeInt(0);
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeMapper.AttributeStability.STATELESS;
        }
    };

    @Test
    void testUserAttributeDelivery() {
        var strangeAwareCf = ClassFile.of(ClassFile.AttributeMapperOption.of(utf8 -> {
            if (utf8.equalsString(STRANGE_ATTRIBUTE_NAME)) return STRANGE_ATTRIBUTE_MAPPER;
            return null;
        }));
        var cpb = ConstantPoolBuilder.of();
        var entry = cpb.utf8Entry(STRANGE_ATTRIBUTE_NAME);
        int pos = entry.index();
        var classBytes = ClassFile.of().build(cpb.classEntry(ClassDesc.of("StrangeClass")), cpb, clb -> clb
                .withMethodBody("dummy", MTD_void, ACC_STATIC, cob -> cob.return_().with(new StrangeAttribute(entry))));

        // read unknown
        var withUnknown = ClassFile.of().parse(classBytes).methods().getFirst()
                .code().orElseThrow().elementStream()
                .filter(e -> e instanceof Attribute<?>).toList();
        assertEquals(1, withUnknown.size());
        UnknownAttribute unknown = (UnknownAttribute) withUnknown.getFirst();
        assertEquals(pos, unknown.attributeName().index());
        assertArrayEquals(new byte[0], unknown.contents());

        // read known
        var withKnown = strangeAwareCf.parse(classBytes).methods().getFirst()
                .code().orElseThrow().elementStream()
                .filter(e -> e instanceof Attribute<?>).toList();
        assertEquals(1, withKnown.size());
        StrangeAttribute strange = (StrangeAttribute) withKnown.getFirst();
        assertEquals(pos, strange.attributeName().index());
    }

    // Verifies reusing stack maps updates the label indices.
    @Test
    void testStackMapReuse() throws Throwable {
        ClassModel exampleClass;
        try (var is = Objects.requireNonNull(AttributeInCodeTest.class.getResourceAsStream("/ExampleClass.class"))) {
            exampleClass = ClassFile.of().parse(is.readAllBytes());
        }

        var code = exampleClass.methods().getFirst()
                .findAttribute(Attributes.code()).orElseThrow();
        var stackMap = code
                .findAttribute(Attributes.stackMapTable()).orElseThrow();
        assertEquals(1, stackMap.entries().size());
        var transform = ClassTransform.transformingMethodBodies(new CodeTransform() {
            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                builder.with(element);
            }

            @Override
            public void atStart(CodeBuilder builder) {
                var ps = ClassDesc.of("java.io.PrintStream");
                builder.getstatic(ClassDesc.of("java.lang.System"), "out", ps)
                       .ldc("Injected")
                       .invokevirtual(ps, "println", MethodTypeDesc.of(CD_void, CD_String))
                       .with(stackMap);
            }
        });

        var cf = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        var bytes = cf.transformClass(exampleClass, transform);
        assertEquals(List.of(), cf.verify(bytes));
        var l = MethodHandles.lookup().defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.STRONG);
        var mh = l.findConstructor(l.lookupClass(), MethodType.methodType(void.class, String.class)).asType(MethodType.methodType(Object.class, String.class));
        Object _ = mh.invokeExact((String) "ape");
    }
}

class ExampleClass {
    ExampleClass(String s) {
        if (s.isEmpty()) {
            System.out.println("Empty");
        }
        // Frame here
        System.out.println(s.length());
    }
}
