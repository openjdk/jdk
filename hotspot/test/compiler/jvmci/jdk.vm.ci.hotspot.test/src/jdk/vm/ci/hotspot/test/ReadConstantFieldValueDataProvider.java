/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.vm.ci.hotspot.test;

import static jdk.vm.ci.hotspot.test.TestHelper.DUMMY_CLASS_CONSTANT;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_STABLE_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_FINAL_DEFAULT_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_FINAL_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_STABLE_DEFAULT_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STATIC_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STATIC_FINAL_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STATIC_STABLE_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STATIC_STABLE_DEFAULT_FIELDS_MAP;

import java.util.LinkedList;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import org.testng.annotations.DataProvider;


public class ReadConstantFieldValueDataProvider {

    @DataProvider(name = "readConstantFieldValueDataProvider")
    public static Object[][] readConstantFieldValueDataProvider() {
        LinkedList<Object[]> cfgSet = new LinkedList<>();
        // Testing static final fields
        STATIC_FINAL_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), null, field.getValue(), "static final field"});
        });
        // Testing static stable fields
        STATIC_STABLE_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), null, field.getValue(), "static stable field"});
        });
        // Testing instance final non-default fields
        INSTANCE_FINAL_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(),
                    DUMMY_CLASS_CONSTANT,
                    field.getValue(),
                    "instance final field"});
        });
        // Testing instance final default fields.
        boolean trustDefFinal = HotSpotJVMCIRuntime.Option.TrustFinalDefaultFields.getBoolean();
        INSTANCE_FINAL_DEFAULT_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            JavaConstant expected = trustDefFinal ? field.getValue() : null;
            cfgSet.add(new Object[]{field.getKey(),
                    DUMMY_CLASS_CONSTANT,
                    expected,
                    "instance final default field"});
        });
        // Testing instance stable non-default fields
        INSTANCE_STABLE_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(),
                    DUMMY_CLASS_CONSTANT,
                    field.getValue(),
                    "instance stable field"});
        });
        // Testing instance stable default fields
        INSTANCE_STABLE_DEFAULT_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(),
                    DUMMY_CLASS_CONSTANT,
                    null,
                    "instance stable default field"});
        });
        // Testing regular instance fields
        INSTANCE_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), DUMMY_CLASS_CONSTANT, null, "instance field"});
        });
        // Testing regular static fields
        STATIC_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), null, null, "static field"});
        });
        // Testing static stable fields
        STATIC_STABLE_DEFAULT_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), null, null, "static stable default field"});
        });
        return cfgSet.toArray(new Object[0][0]);
    }

    @DataProvider(name = "readConstantFieldValueNegativeDataProvider")
    public static Object[][] readConstantFieldValueNegativeDataProvider() {
        LinkedList<Object[]> cfgSet = new LinkedList<>();
        // Testing instance fields with null as receiver
        INSTANCE_FIELDS_MAP.entrySet().stream().forEach((field) -> {
            cfgSet.add(new Object[]{field.getKey(), null});
        });
        // Testing null as a field argument
        cfgSet.add(new Object[]{null, null});
        return cfgSet.toArray(new Object[0][0]);
    }
}
