/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.annotations;

import java.util.HashMap;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.MetadataDefinition;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestDynamicAnnotation
 */


public class TestDynamicAnnotation {
    @MetadataDefinition
    @interface CustomAnnotation {
        String value();
        int intValue();
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("value", "MyValue");
        values.put("intValue", 1);
        new AnnotationElement(CustomAnnotation.class, values);
    }
}
