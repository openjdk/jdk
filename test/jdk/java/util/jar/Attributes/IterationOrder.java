/*
 * Copyright 2014 Google, Inc.  All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8062194
 * @summary Ensure Attribute iteration order is the insertion order.
 * @run junit IterationOrder
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class IterationOrder {

    @ParameterizedTest
    @MethodSource
    void checkOrderTest(Attributes.Name k0, String v0,
                           Attributes.Name k1, String v1,
                           Attributes.Name k2, String v2) {
        Attributes x = new Attributes();
        x.put(k0, v0);
        x.put(k1, v1);
        x.put(k2, v2);
        Map.Entry<?,?>[] entries
            = x.entrySet().toArray(new Map.Entry<?,?>[3]);
        if (!(entries.length == 3
              && entries[0].getKey() == k0
              && entries[0].getValue() == v0
              && entries[1].getKey() == k1
              && entries[1].getValue() == v1
              && entries[2].getKey() == k2
              && entries[2].getValue() == v2)) {
            fail(Arrays.toString(entries));
        }

        Object[] keys = x.keySet().toArray();
        if (!(keys.length == 3
              && keys[0] == k0
              && keys[1] == k1
              && keys[2] == k2)) {
             fail(Arrays.toString(keys));
        }
    }

    static Stream<Arguments> checkOrderTest() {
        Attributes.Name k0 = Name.MANIFEST_VERSION;
        Attributes.Name k1 = Name.MAIN_CLASS;
        Attributes.Name k2 = Name.SEALED;
        String v0 = "42.0";
        String v1 = "com.google.Hello";
        String v2 = "yes";
        return Stream.of(
                Arguments.of(k0, v0, k1, v1, k2, v2),
                Arguments.of(k1, v1, k0, v0, k2, v2),
                Arguments.of(k2, v2, k1, v1, k0, v0)
        );
    }
}
