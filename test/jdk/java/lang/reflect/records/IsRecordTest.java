/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255560
 * @summary Class::isRecord should check that the current class is final and not abstract
 * @library /test/lib
 * @run junit/othervm IsRecordTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Map;

import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import jdk.test.lib.ByteCodeLoader;
import static java.lang.System.out;
import static java.lang.classfile.ClassFile.ACC_ABSTRACT;
import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.constant.ConstantDescs.CD_int;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class IsRecordTest {

    public static Object[][] scenarios() {
        return new Object[][] {
             // isFinal, isAbstract, extendJLR, withRecAttr, expectIsRecord
             {     false,    false,     false,    true,      false    },
             {     false,    false,     true,     true,      false    },
             {     false,    true,      false,    true,      false    },
             {     false,    true,      true,     true,      false    },
             {     true,     false,     false,    true,      false    },
             {     true,     false,     true,     true,      true     },

             {     false,    false,     false,    false,     false    },
             {     false,    false,     true,     false,     false    },
             {     false,    true,      false,    false,     false    },
             {     false,    true,      true,     false,     false    },
             {     true,     false,     false,    false,     false    },
             {     true,     false,     true,     false,     false    },
        };
    }

    /**
     * Tests the valid combinations of i) final/non-final, ii) abstract/non-abstract,
     * iii) direct subclass of j.l.Record (or not), along with the presence or
     * absence of a record attribute.
     */
    @ParameterizedTest
    @MethodSource("scenarios")
    public void testDirectSubClass(boolean isFinal,
                                   boolean isAbstract,
                                   boolean extendsJLR,
                                   boolean withRecordAttr,
                                   boolean expectIsRecord) throws Exception {
        out.println("\n--- testDirectSubClass isFinal=%s, isAbstract=%s, extendsJLR=%s, withRecordAttr=%s, expectIsRecord=%s ---"
                .formatted(isFinal, isAbstract, extendsJLR, withRecordAttr, expectIsRecord));

        List<RecordComponentInfo> rc = null;
        if (withRecordAttr)
            rc = List.of(RecordComponentInfo.of("x", CD_int));
        String superName = extendsJLR ? "java/lang/Record" : "java/lang/Object";
        var classBytes = generateClassBytes("C", isFinal, isAbstract, superName, rc);
        Class<?> cls = ByteCodeLoader.load("C", classBytes);
        out.println("cls=%s, Record::isAssignable=%s, isRecord=%s"
                .formatted(cls, Record.class.isAssignableFrom(cls), cls.isRecord()));
        assertEquals(expectIsRecord, cls.isRecord());
        assertEquals(expectIsRecord, cls.getRecordComponents() != null);
    }

    /**
     * Tests the valid combinations of i) final/non-final, ii) abstract/non-abstract,
     * along with the presence or absence of a record attribute, where the class has
     * a superclass whose superclass is j.l.Record.
     */
    @ParameterizedTest
    @MethodSource("scenarios")
    public void testIndirectSubClass(boolean isFinal,
                                     boolean isAbstract,
                                     boolean unused1,
                                     boolean withRecordAttr,
                                     boolean unused2) throws Exception {
        out.println("\n--- testIndirectSubClass isFinal=%s, isAbstract=%s withRecordAttr=%s ---"
                .formatted(isFinal, isAbstract, withRecordAttr));

        List<RecordComponentInfo> rc = null;
        if (withRecordAttr)
            rc = List.of(RecordComponentInfo.of("x", CD_int));
        var supFooClassBytes = generateClassBytes("SupFoo", false, isAbstract, "java/lang/Record", rc);
        var subFooClassBytes = generateClassBytes("SubFoo", isFinal, isAbstract, "SupFoo", rc);
        var allClassBytes = Map.of("SupFoo", supFooClassBytes,
                                   "SubFoo", subFooClassBytes);

        ClassLoader loader = new ByteCodeLoader(allClassBytes, null);
        Class<?> supFooCls = loader.loadClass("SupFoo");
        Class<?> subFooCls = loader.loadClass("SubFoo");
        for (var cls : List.of(supFooCls, subFooCls))
            out.println("cls=%s, Record::isAssignable=%s, isRecord=%s"
                    .formatted(cls, Record.class.isAssignableFrom(cls), cls.isRecord()));
        assertFalse(supFooCls.isRecord());
        assertFalse(subFooCls.isRecord());
        assertNull(supFooCls.getRecordComponents());
        assertNull(subFooCls.getRecordComponents());
    }

    /** Tests record-ness properties of traditionally compiled classes. */
    @Test
    public void testBasicRecords() {
        out.println("\n--- testBasicRecords ---");
        record EmptyRecord () { }
        assertTrue(EmptyRecord.class.isRecord());
        assertEquals(0, EmptyRecord.class.getRecordComponents().length);

        record FooRecord (int x) { }
        assertTrue(FooRecord.class.isRecord());
        assertNotNull(FooRecord.class.getRecordComponents());

        final record FinalFooRecord (int x) { }
        assertTrue(FinalFooRecord.class.isRecord());
        assertNotNull(FinalFooRecord.class.getRecordComponents());

        class A { }
        assertFalse(A.class.isRecord());
        assertNull(A.class.getRecordComponents());

        final class B { }
        assertFalse(B.class.isRecord());
        assertNull(B.class.getRecordComponents());
    }

    // --  infra

    // Generates a class with the given properties.
    byte[] generateClassBytes(String className,
                              boolean isFinal,
                              boolean isAbstract,
                              String superName,
                              List<RecordComponentInfo> components) {
        return ClassFile.of().build(ClassDesc.ofInternalName(className), clb -> {
            int access = 0;
            if (isFinal)
                access = access | ACC_FINAL;
            if (isAbstract)
                access = access | ACC_ABSTRACT;
            clb.withFlags(access);
            clb.withSuperclass(ClassDesc.ofInternalName(superName));
            if (components != null)
                clb.accept(RecordAttribute.of(components));
        });
    }

}
