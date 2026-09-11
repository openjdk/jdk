/*
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

import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import jdk.internal.value.ValueClass;


/*
 * @test
 * @summary Test of diagnostic command GC.class_histogram for arrays with different layouts
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main ${test.main.class}
 */
public class ClassHistogramFlattenedArrayTest {

    @jdk.internal.vm.annotation.LooselyConsistentValue
    public static value record TestClassNA(short s1, short s2) {}

    @jdk.internal.vm.annotation.LooselyConsistentValue
    public static value record TestClassNRA(short s1, short s2) {}

    @jdk.internal.vm.annotation.LooselyConsistentValue
    public static value record TestClassNRNA(short s1, short s2) {}


    public static Object[] arrays = {
        ValueClass.newNullableAtomicArray(TestClassNA.class, 1024),
        ValueClass.newNullRestrictedAtomicArray(TestClassNRA.class, 1024, new TestClassNRA((short)1, (short)1)),
        ValueClass.newNullRestrictedNonAtomicArray(TestClassNRNA.class, 1024, new TestClassNRNA((short)1, (short)1)),
        ValueClass.newReferenceArray(TestClassR.class, 1024),
    };

    public static void main(String[] args) {
        CommandExecutor executor = new JMXExecutor();
        OutputAnalyzer output = executor.execute("GC.class_histogram ");

        assertType(output, TestClassNA[].class, "flat, nullable, atomic");
        assertType(output, TestClassNRA[].class, "flat, null-restricted, atomic");
        assertType(output, TestClassNRNA[].class, "flat, null-restricted, non-atomic");
    }

    private static void assertType(OutputAnalyzer output, Class<?> arrayClass, String type) {
        output.shouldMatch("^\\s+\\d+:\\s+\\d+\\s+\\d+\\s+" +
            Pattern.quote(arrayClass.getName()) +
            "\\s+\\[" + Pattern.quote(type) + "\\](?:\\s+\\(.*\\))?\\s*$");
    }

}
