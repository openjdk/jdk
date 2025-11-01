/*
 * Copyright (c) 2018, Google LLC. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8211138 8362885
 * @summary Missing Flag enum constants
 * @library /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.code
 * @compile FlagsTest.java
 * @run main/manual FlagsTest
 */
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Flags.FlagTarget;
import com.sun.tools.javac.code.Flags.NotFlag;
import com.sun.tools.javac.code.Flags.Use;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlagsTest {

    private static final int U2_SIZE = 16;

    public static void main(String[] args) throws Throwable {
        findFreeFlags();
    }

    private static void findFreeFlags() throws Throwable {
        Map<FlagTarget, Map<Long, List<Field>>> target2Flag2Fields = computeTarget2Flag2Fields();

        for (FlagTarget target : FlagTarget.values()) {
            long freeFlags = ~collectFlags(target2Flag2Fields, target);

            printFreeFlags(target.name(), freeFlags);
        }
    }

    private static Map<FlagTarget, Map<Long, List<Field>>> computeTarget2Flag2Fields() throws Throwable {
        Map<FlagTarget, Map<Long, List<Field>>> target2Flag2Fields = new HashMap<>();
        for (Field f : Flags.class.getFields()) {
            if (f.isAnnotationPresent(NotFlag.class)) {
                continue;
            }

            Use use = f.getAnnotation(Use.class);

            if (use == null) {
                throw new AssertionError("No @Use and no @NotFlag for: " + f.getName());
            }

            long flagValue = ((Number) f.get(null)).longValue();

            for (FlagTarget target : use.value()) {
                target2Flag2Fields.computeIfAbsent(target, _ -> new HashMap<>())
                        .computeIfAbsent(flagValue, _ -> new ArrayList<>())
                        .add(f);
            }
        }
        return target2Flag2Fields;
    }

    private static void printFreeFlags(String comment, long freeFlags) {
            System.err.print("free flags for " + comment + ": ");
            for (int bit = U2_SIZE; bit < Long.SIZE; bit++) { //lowest 16 bits are used in classfiles, never suggest adding anything there
                if ((freeFlags & (1L << bit)) != 0) {
                    System.err.print("1L<<" + bit + " ");
                }
            }
            System.err.println();
    }

    private static long collectFlags(Map<FlagTarget, Map<Long, List<Field>>> target2Flag2Fields, FlagTarget... forTargets) {
        long flags = 0;

        for (FlagTarget target : forTargets) {
            for (long used : target2Flag2Fields.get(target).keySet()) {
                flags |= used;
            }
        }

        return flags;
    }
}
