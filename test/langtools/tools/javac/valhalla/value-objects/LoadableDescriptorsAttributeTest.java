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
 * @bug 8280164 8334313
 * @summary Check emission of LoadableDescriptors attribute
 * @enablePreview
 * @run main LoadableDescriptorsAttributeTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Set;
import java.util.stream.Collectors;

public class LoadableDescriptorsAttributeTest {

    value class V1 {}
    value class V2 {}
    value class V3 {}
    value class V4 {}
    value class V5 {}
    value class V6 {}
    value class V7 {}
    value class V8 {}
    value class V9 {}
    abstract value class V10 {}

    static final value class X {
        final V1 [] v1 = null; // field descriptor, encoding array type - no LoadableDescriptors.
        V2 foo() {  // method descriptor encoding value type, to be preloaded
            return null;
        } // return type is value type so should be preloaded
        void foo(V3 v3) { // method descriptor encoding value type, to be preloaded
        }
        void foo(int x) {
            V4 [] v4 = null; // local variable encoding array type - no preload.
        }
        void goo(V6[] v6) { // parameter uses value type but as array component - no preload.
            V5 v5 = null;  // no preload as value type is used for local type.
            if (v5 == null) {
                // ...
            } else {
               V5 [] v52 = null;
            }
        }
        final V7 v7 = null; // field descriptor uses value type - to be preloaded.
        V8 [] goo(V9 [] v9) { // neither V8 nor V9 call for preload being array component types
            return null;
        }
        V10 v10 = null; // abstract shouldn't be in the loadable descriptors attr
    }
    // So we expect ONLY V2, V3, V7 to be in LoadableDescriptors list

    public static void main(String[] args) throws Exception {
        ClassModel cls;
        try (var in = LoadableDescriptorsAttributeTest.class.getResourceAsStream("LoadableDescriptorsAttributeTest$X.class")) {
            cls = ClassFile.of().parse(in.readAllBytes());
        }

        /* Check emission of LoadableDescriptors attribute */
        var descriptors = cls.findAttribute(Attributes.loadableDescriptors()).orElseThrow();
        if (descriptors.loadableDescriptors().size() != 3) {
            throw new AssertionError("Expected 3 loadable descriptors, found: " + descriptors.loadableDescriptors());
        }

        Set<String> expected = Set.of(
                "LLoadableDescriptorsAttributeTest$V2;",
                "LLoadableDescriptorsAttributeTest$V3;",
                "LLoadableDescriptorsAttributeTest$V7;"
        );

        Set<String> found = descriptors.loadableDescriptors()
                .stream()
                .map(Utf8Entry::stringValue)
                .collect(Collectors.toSet());

        if (!expected.equals(found)) {
            throw new AssertionError("LoadableDescriptors mismatch, found: " + found);
        }
    }
}
