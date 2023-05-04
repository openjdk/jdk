/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @modules java.base/jdk.internal.module
 * @run testng ClassFileVersionsTest
 * @summary Test parsing of module-info.class with different class file versions
 */

import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.nio.ByteBuffer;
import java.util.Set;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

import jdk.internal.module.ModuleInfoWriter;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ClassFileVersionsTest {
    private static final int FEATURE;
    static {
        FEATURE = Runtime.version().feature();
        assert FEATURE >= 10;
    }

    // major, minor, modifiers for requires java.base
    @DataProvider(name = "supported")
    public Object[][] supported() {
        /*
         * There are four test cases for JDK 9 and then one test case
         * for each subsequent JDK version from JDK 10 to the current
         * feature release for a total of (4 + (FEATURE - 9) ) =>
         * (feature - 5) rows.
         */
        Object[][] result = new Object[(FEATURE - 5)][];

        // Class file version of JDK 9 is 53.0
        result[0] = new Object[]{ 53, 0, Set.of()};
        result[1] = new Object[]{ 53, 0, Set.of(STATIC) };
        result[2] = new Object[]{ 53, 0, Set.of(TRANSITIVE) };
        result[3] = new Object[]{ 53, 0, Set.of(STATIC, TRANSITIVE) };

        // Major class file version of JDK N is 44 + n. Create rows
        // for JDK 10 through FEATURE.
        for (int i = 4; i < (FEATURE - 5) ; i++) {
            result[i] = new Object[]{i + 50, 0, Set.of()};
        }

        return result;
    }

    // major, minor, modifiers for requires java.base
    @DataProvider(name = "unsupported")
    public Object[][] unsupported() {
        /*
         * There are three test cases for releases prior to JDK 9,
         * three test cases for each JDK version from JDK 10 to the
         * current feature release, plus one addition test case for
         * the next release for a total of (3 + (FEATURE - 9) * 3 + 1)
         * rows.
         */
        int unsupportedCount = 3 + (FEATURE - 9)*3 + 1;
        Object[][] result = new Object[unsupportedCount][];

        result[0] = new Object[]{50, 0, Set.of()}; // JDK 6
        result[1] = new Object[]{51, 0, Set.of()}; // JDK 7
        result[2] = new Object[]{52, 0, Set.of()}; // JDK 8

        for (int i = 10; i <= FEATURE ; i++) {
            int base = 3 + (i-10)*3;
            // Major class file version of JDK N is 44+n
            result[base]     = new Object[]{i + 44, 0, Set.of(STATIC)};
            result[base + 1] = new Object[]{i + 44, 0, Set.of(TRANSITIVE)};
            result[base + 2] = new Object[]{i + 44, 0, Set.of(STATIC, TRANSITIVE)};
        }

        result[unsupportedCount - 1] = new Object[]{FEATURE+1+44, 0, Set.of()};
        return result;
    }

    @Test(dataProvider = "supported")
    public void testSupported(int major, int minor, Set<Modifier> ms) {
        ModuleDescriptor descriptor = ModuleDescriptor.newModule("foo")
                .requires(ms, "java.base")
                .build();
        ByteBuffer bb = ModuleInfoWriter.toByteBuffer(descriptor);
        classFileVersion(bb, major, minor);
        descriptor = ModuleDescriptor.read(bb);
        assertEquals(descriptor.name(), "foo");
    }

    @Test(dataProvider = "unsupported",
          expectedExceptions = InvalidModuleDescriptorException.class)
    public void testUnsupported(int major, int minor, Set<Modifier> ms) {
        ModuleDescriptor descriptor = ModuleDescriptor.newModule("foo")
                .requires(ms, "java.base")
                .build();
        ByteBuffer bb = ModuleInfoWriter.toByteBuffer(descriptor);
        classFileVersion(bb, major, minor);

        // throws InvalidModuleDescriptorException
        ModuleDescriptor.read(bb);
    }

    private void classFileVersion(ByteBuffer bb, int major, int minor) {
        bb.putShort(4, (short) minor);
        bb.putShort(6, (short) major);
    }
}
