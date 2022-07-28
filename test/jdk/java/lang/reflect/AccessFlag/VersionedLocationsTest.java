/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289106
 * @summary Tests of AccessFlag.locations(ClassFileFormatVersion)
 */

import java.lang.reflect.AccessFlag;
import static java.lang.reflect.AccessFlag.*;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.HashSet;
import java.util.Set;

public class VersionedLocationsTest {
    public static void main(String... args) throws Exception {
        testInvariantAccessFlags();
        testStepFunctionAccessFlags();
    }

    /**
     * Invariant access flags have the same set of locations for each
     * class file format version.
     */
    private static void testInvariantAccessFlags() {
        Set<AccessFlag> invariantAccessFlags =
            Set.of(SUPER, SYNCHRONIZED, VOLATILE, NATIVE);
        for(var accessFlag : invariantAccessFlags) {
            Set<AccessFlag.Location> expected = accessFlag.locations();

            for(var cffv : ClassFileFormatVersion.values()) {
                Set<AccessFlag.Location> actual = accessFlag.locations(cffv);
                if (!expected.equals(actual)) {
                    throw new RuntimeException("Unexpected locations for " +
                                               accessFlag  + " on " + cffv);
                }
            }
        }
    }

    private static void testStepFunctionAccessFlags() {
        StepFunctionTC[] testCases = {
            new StepFunctionTC(PUBLIC, removeInnerClass(PUBLIC.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               PUBLIC.locations()),

            new StepFunctionTC(PRIVATE, removeInnerClass(PRIVATE.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               PRIVATE.locations()),

            new StepFunctionTC(PROTECTED, removeInnerClass(PROTECTED.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               PROTECTED.locations()),

            new StepFunctionTC(STATIC, removeInnerClass(STATIC.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               STATIC.locations()),

//             new StepFunctionTC(FINAL, removeInnerClass(FINAL.locations()), // two-phase
//                                ClassFileFormatVersion.RELEASE_1,
//                                FINAL.locations()),

            new StepFunctionTC(OPEN, Set.of(),
                               ClassFileFormatVersion.RELEASE_9,
                               OPEN.locations()),

            new StepFunctionTC(TRANSITIVE, Set.of(),
                               ClassFileFormatVersion.RELEASE_9,
                               TRANSITIVE.locations()),

            new StepFunctionTC(STATIC_PHASE, Set.of(),
                               ClassFileFormatVersion.RELEASE_9,
                               STATIC_PHASE.locations()),

            new StepFunctionTC(BRIDGE, Set.of(),
                               ClassFileFormatVersion.RELEASE_5,
                               BRIDGE.locations()),

            new StepFunctionTC(VARARGS, Set.of(),
                               ClassFileFormatVersion.RELEASE_5,
                               VARARGS.locations()),

            new StepFunctionTC(INTERFACE, removeInnerClass(INTERFACE.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               INTERFACE.locations()),

            new StepFunctionTC(ABSTRACT, removeInnerClass(ABSTRACT.locations()),
                               ClassFileFormatVersion.RELEASE_1,
                               ABSTRACT.locations()),

            new StepFunctionTC(ANNOTATION, Set.of(),
                               ClassFileFormatVersion.RELEASE_5,
                               ANNOTATION.locations()),

            new StepFunctionTC(ENUM, Set.of(),
                               ClassFileFormatVersion.RELEASE_5,
                               ENUM.locations()),

            new StepFunctionTC(MODULE, Set.of(),
                               ClassFileFormatVersion.RELEASE_9,
                               MODULE.locations())
        };

        for (var testCase : testCases) {
            var accessFlag  = testCase.accessFlag();
            var initialLocs = testCase.initialLocs();
            var transition   =testCase.transition();
            var finalLocs  = testCase.finalLocs();
            for (var cffv : ClassFileFormatVersion.values()) {
                compareLocations(cffv.compareTo(transition) >= 0 ? finalLocs : initialLocs,
                                  accessFlag, cffv);
            }
        }
    }

    private static void compareLocations(Set<AccessFlag.Location> expected,
                                         AccessFlag accessFlag,
                                         ClassFileFormatVersion cffv) {
        var actual = accessFlag.locations(cffv);
        if (!expected.equals(actual)) {
            throw new RuntimeException("Unexpected locations for " +
                                       accessFlag  + " on " + cffv + "\n" +
                                       "Expected " + expected + "; got \t" + actual);
        }
    }

    private static Set<AccessFlag.Location> removeInnerClass(Set<AccessFlag.Location> locations) {
        var s = new HashSet<>(locations);
        s.remove(Location.INNER_CLASS);
        return s;
    }

    private record StepFunctionTC(AccessFlag accessFlag,
                                  Set<AccessFlag.Location> initialLocs,
                                  ClassFileFormatVersion transition,
                                  Set<AccessFlag.Location> finalLocs){}
}
