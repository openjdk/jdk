/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8281323
 * @summary Check emission of LoadableDescriptors attribute to make sure javac does not emit unneeded entries.
 * @enablePreview
 * @run main NoUnnecessaryLoadableDescriptorsTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.LoadableDescriptorsAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;

public class NoUnnecessaryLoadableDescriptorsTest {

    public value class LoadableDescriptorsTest1 {
        byte b;
        public LoadableDescriptorsTest1(byte b) {
            this.b = b;
        }
    }

    public class LoadableDescriptorsTest2 {
        static class Inner1 {
            static value class Inner2 {}
            Inner2 inner;
        }
    }

    public static void main(String[] args) throws Exception {
        ClassModel cls;
        LoadableDescriptorsAttribute loadableDescriptors;

        // There should be no LoadableDescriptors attribute in NoUnnecessaryLoadableDescriptorsTest.class
        try (var in = NoUnnecessaryLoadableDescriptorsTest.class.getResourceAsStream("NoUnnecessaryLoadableDescriptorsTest.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        /* Check emission of LoadableDescriptors attribute */
        if (cls.findAttribute(Attributes.loadableDescriptors()).isPresent()) {
            throw new AssertionError("Unexpected LoadableDescriptors attribute!");
        }

        // There should be no LoadableDescriptors attribute in NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest1.class
        try (var in = NoUnnecessaryLoadableDescriptorsTest.class.getResourceAsStream("NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest1.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        /* Check emission of LoadableDescriptors attribute */
        if (cls.findAttribute(Attributes.loadableDescriptors()).isPresent()) {
            throw new AssertionError("Unexpected LoadableDescriptors attribute!");
        }

        // There should be no LoadableDescriptors attribute in NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2.class
        try (var in = NoUnnecessaryLoadableDescriptorsTest.class.getResourceAsStream("NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        /* Check emission of LoadableDescriptors attribute */
        if (cls.findAttribute(Attributes.loadableDescriptors()).isPresent()) {
            throw new AssertionError("Unexpected LoadableDescriptors attribute!");
        }

        // There should be no LoadableDescriptors attribute in NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2$Inner2.class
        try (var in = NoUnnecessaryLoadableDescriptorsTest.class.getResourceAsStream("NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2$Inner1$Inner2.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        /* Check emission of LoadableDescriptors attribute */
        if (cls.findAttribute(Attributes.loadableDescriptors()).isPresent()) {
            throw new AssertionError("Unexpected LoadableDescriptors attribute!");
        }

        // There should be ONE LoadableDescriptors attribute entry in NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2$Inner1.class
        try (var in = NoUnnecessaryLoadableDescriptorsTest.class.getResourceAsStream("NoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2$Inner1.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        if (cls == null) {
            throw new AssertionError("Could not locate the class files");
        }

        /* Check emission of LoadableDescriptors attribute */
        loadableDescriptors = cls.findAttribute(Attributes.loadableDescriptors()).orElseThrow();

        List<String> expected = List.of("LNoUnnecessaryLoadableDescriptorsTest$LoadableDescriptorsTest2$Inner1$Inner2;");
        List<String> found = loadableDescriptors.loadableDescriptors().stream().map(Utf8Entry::stringValue).toList();
        if (!expected.equals(found)) {
            throw new AssertionError("Expected one LoadableDescriptors class entry Inner2, but found " + found);
        }
    }
}
