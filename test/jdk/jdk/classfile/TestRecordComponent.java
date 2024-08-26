/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile RecordComponent.
 * @run junit TestRecordComponent
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import helpers.ClassRecord;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TestRecordComponent {

    static final String testClassName = "TestRecordComponent$TestRecord";
    static final Path testClassPath = Paths.get(URI.create(ArrayTest.class.getResource(testClassName + ".class").toString()));

    @Test
    void testAdapt() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(Files.readAllBytes(testClassPath));
        ClassTransform xform = (cb, ce) -> {
            if (ce instanceof RecordAttribute rm) {
                List<RecordComponentInfo> components = rm.components();
                components = components.stream()
                                       .map(c -> RecordComponentInfo.of(c.name(), c.descriptor(), c.attributes()))
                                       .toList();
                cb.with(RecordAttribute.of(components));
            } else
                cb.with(ce);
        };
        ClassModel newModel = cc.parse(cc.transformClass(cm, xform));
        ClassRecord.assertEquals(newModel, cm);
    }

    @Test
    void testPassThrough() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(Files.readAllBytes(testClassPath));
        ClassTransform xform = (cb, ce) -> cb.with(ce);
        ClassModel newModel = cc.parse(cc.transformClass(cm, xform));
        ClassRecord.assertEquals(newModel, cm);
    }

    @Test
    void testChagne() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(Files.readAllBytes(testClassPath));
        ClassTransform xform = (cb, ce) -> {
            if (ce instanceof RecordAttribute ra) {
                List<RecordComponentInfo> components = ra.components();
                components = components.stream().map(c -> RecordComponentInfo.of(TemporaryConstantPool.INSTANCE.utf8Entry(c.name().stringValue() + "XYZ"), c.descriptor(), List.of()))
                                       .toList();
                cb.with(RecordAttribute.of(components));
            }
            else
                cb.with(ce);
        };
        ClassModel newModel = cc.parse(cc.transformClass(cm, xform));
        RecordAttribute ra = newModel.findAttribute(Attributes.record()).orElseThrow();
        assertEquals(ra.components().size(), 2, "Should have two components");
        assertEquals(ra.components().get(0).name().stringValue(), "fooXYZ");
        assertEquals(ra.components().get(1).name().stringValue(), "barXYZ");
        assertTrue(ra.components().get(0).attributes().isEmpty());
        assertEquals(newModel.attributes().size(), cm.attributes().size());
    }

    @Test
    void testOptions() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(testClassPath));
        cm.forEach((ce) -> {
            if (ce instanceof RecordAttribute rm) {
                count.addAndGet(rm.components().size());
            }});
        assertEquals(count.get(), 2);
        assertEquals(cm.findAttribute(Attributes.record()).orElseThrow().components().size(), 2);

        count.set(0);
    }

    public static record TestRecord(@RC String foo, int bar) {}

    @Target(ElementType.RECORD_COMPONENT)
    public @interface RC {}
}
