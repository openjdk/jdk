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

package jdk.jpackage.internal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import org.junit.jupiter.api.Test;


public class DefaultBundlingEnvironmentTest {

    @Test
    void testDefaultBundlingOperation() {

        var executed = new int[1];

        var descriptor = new BundlingOperationDescriptor(OperatingSystem.current(), "foo", "build");

        var env = new DefaultBundlingEnvironment(DefaultBundlingEnvironment.build().defaultOperation(() -> {
            executed[0] = executed[0] + 1;
            return Optional.of(descriptor);
        }));

        // Assert the default bundling operation supplier is not called in the ctor.
        assertEquals(0, executed[0]);

        // Assert the default bundling operation is as expected.
        assertEquals(descriptor, env.defaultOperation().orElseThrow());
        assertEquals(1, executed[0]);

        // Assert the default bundling operation supplier is called only once.
        assertEquals(descriptor, env.defaultOperation().orElseThrow());
        assertEquals(1, executed[0]);
    }
}
