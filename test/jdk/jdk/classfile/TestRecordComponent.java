/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing Classfile RecordComponent.
 * @run testng TestRecordComponent
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
import jdk.classfile.Attributes;
import jdk.classfile.ClassModel;
import jdk.classfile.ClassTransform;
import jdk.classfile.Classfile;
import jdk.classfile.attribute.RecordAttribute;
import jdk.classfile.attribute.RecordComponentInfo;
import jdk.classfile.impl.TemporaryConstantPool;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test()
public class TestRecordComponent {

    static final String testClassName = "TestRecordComponent$TestRecord";
    static final Path testClassPath = Paths.get(URI.create(ArrayTest.class.getResource(testClassName + ".class").toString()));

    public void testAdapt() throws Exception {
        ClassModel cm = Classfile.parse(Files.readAllBytes(testClassPath));
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
        ClassModel newModel = Classfile.parse(cm.transform(xform));
        ClassRecord.assertEquals(newModel, cm);
    }

    public void testPassThrough() throws Exception {
        ClassModel cm = Classfile.parse(Files.readAllBytes(testClassPath));
        ClassTransform xform = (cb, ce) -> cb.with(ce);
        ClassModel newModel = Classfile.parse(cm.transform(xform));
        ClassRecord.assertEquals(newModel, cm);
    }

    public void testChagne() throws Exception {
        ClassModel cm = Classfile.parse(Files.readAllBytes(testClassPath));
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
        ClassModel newModel = Classfile.parse(cm.transform(xform));
        RecordAttribute ra = newModel.findAttribute(Attributes.RECORD).orElseThrow();
        Assert.assertEquals(ra.components().size(), 2, "Should have two components");
        Assert.assertEquals(ra.components().get(0).name().stringValue(), "fooXYZ");
        Assert.assertEquals(ra.components().get(1).name().stringValue(), "barXYZ");
        Assert.assertTrue(ra.components().get(0).attributes().isEmpty());
        Assert.assertEquals(newModel.attributes().size(), cm.attributes().size());
    }

    public void testOptions() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        ClassModel cm = Classfile.parse(Files.readAllBytes(testClassPath));
        cm.forEachElement((ce) -> {
            if (ce instanceof RecordAttribute rm) {
                count.addAndGet(rm.components().size());
            }});
        Assert.assertEquals(count.get(), 2);
        Assert.assertEquals(cm.findAttribute(Attributes.RECORD).orElseThrow().components().size(), 2);

        count.set(0);
    }

    public static record TestRecord(@RC String foo, int bar) {}

    @Target(ElementType.RECORD_COMPONENT)
    public @interface RC {}
}
