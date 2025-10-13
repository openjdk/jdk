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
 * @bug 8289106 8293627
 * @summary Tests of AccessFlag.locations(ClassFileFormatVersion) and
 *          accessors on AccessFlag.Location
 */

import java.lang.reflect.AccessFlag;
import static java.lang.reflect.AccessFlag.*;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.HashSet;
import java.util.Set;

/*
 * There are several patterns of access flag applicability. First, an
 * access flag can be applied to the same set of locations for each
 * class file format version. This is "invariant" usage. Second, an
 * access flag can be defined for version N, therefore inapplicable
 * for earlier versions, and then applied to the same locations for
 * all subsequent versions. This is "step" usage. Finally, an access
 * flag to have a more complicated pattern, having multiple steps of
 * being allowed at more locations or even having locations removed if
 * the access flag is retired.
 *
 * List of access flags and how they are tested:
 *
 * PUBLIC       step
 * PRIVATE      step
 * PROTECTED    step
 * STATIC       step
 * FINAL        two-step
 * SUPER        invariant
 * OPEN         step
 * TRANSITIVE   step
 * SYNCHRONIZED invariant
 * STATIC_PHASE step
 * VOLATILE     invariant
 * BRIDGE       step
 * TRANSIENT    invariant
 * VARARGS      step
 * NATIVE       invariant
 * INTERFACE    step
 * ABSTRACT     step
 * STRICT       other
 * SYNTHETIC    other (three-step)
 * ANNOTATION   step
 * ENUM         step
 * MANDATED     two-step
 * MODULE       step
 */

public class VersionedLocationsTest {
    public static void main(String... args) throws Exception {
        testInvariantAccessFlags();
        testStepFunctionAccessFlags();
        testTwoStepAccessFlags();
        testSynthetic();
        testStrict();
        testLatestMatch();
        testFlagVersionConsistency();
        testLocationMaskFlagConsistency();
    }

    /**
     * Invariant access flags have the same set of locations for each
     * class file format version.
     */
    private static void testInvariantAccessFlags() {
        Set<AccessFlag> invariantAccessFlags =
            Set.of(SUPER, SYNCHRONIZED, VOLATILE, TRANSIENT, NATIVE);
        for(var accessFlag : invariantAccessFlags) {
            Set<AccessFlag.Location> expected = accessFlag.locations();

            for(var cffv : ClassFileFormatVersion.values()) {
                compareLocations(accessFlag.locations(), accessFlag, cffv);
            }
        }
    }

    private static void testStepFunctionAccessFlags() {
        StepFunctionTC[] testCases = {
            new StepFunctionTC(PUBLIC,
                               removeInnerClass(PUBLIC.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(PRIVATE,
                               removeInnerClass(PRIVATE.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(PROTECTED,
                               removeInnerClass(PROTECTED.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(STATIC,
                               removeInnerClass(STATIC.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(OPEN,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_9),

            new StepFunctionTC(TRANSITIVE,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_9),

            new StepFunctionTC(STATIC_PHASE,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_9),

            new StepFunctionTC(BRIDGE,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_5),

            new StepFunctionTC(VARARGS,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_5),

            new StepFunctionTC(INTERFACE,
                               removeInnerClass(INTERFACE.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(ABSTRACT,
                               removeInnerClass(ABSTRACT.locations()),
                               ClassFileFormatVersion.RELEASE_1),

            new StepFunctionTC(ANNOTATION,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_5),

            new StepFunctionTC(ENUM,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_5),

            new StepFunctionTC(MODULE,
                               Set.of(),
                               ClassFileFormatVersion.RELEASE_9)
        };

        for (var testCase : testCases) {
            for (var cffv : ClassFileFormatVersion.values()) {
                compareLocations(cffv.compareTo(testCase.transition()) >= 0 ?
                                 testCase.finalLocs() :
                                 testCase.initialLocs(),
                                 testCase.accessFlag, cffv);
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
                                  ClassFileFormatVersion transition) {

        public Set<AccessFlag.Location> finalLocs() {
            return accessFlag.locations();
        }
    }


    private record TwoStepFunctionTC(AccessFlag accessFlag,
                                     Set<AccessFlag.Location> initialLocs,
                                     ClassFileFormatVersion transition1,
                                     Set<AccessFlag.Location> firstLocs,
                                     ClassFileFormatVersion transition2) {

        public Set<AccessFlag.Location> secondLocs() {
            return accessFlag.locations();
        }
    }

    private static void testTwoStepAccessFlags() {
        TwoStepFunctionTC[] testCases = {
            new TwoStepFunctionTC(FINAL,
                                  Set.of(Location.CLASS, Location.FIELD, Location.METHOD),
                                  ClassFileFormatVersion.RELEASE_1,
                                  Set.of(Location.CLASS, Location.FIELD, Location.METHOD, Location.INNER_CLASS),
                                  ClassFileFormatVersion.RELEASE_8),

            new TwoStepFunctionTC(MANDATED,
                                  Set.of(),
                                  ClassFileFormatVersion.RELEASE_8,
                                  Set.of(Location.METHOD_PARAMETER),
                                  ClassFileFormatVersion.RELEASE_9),
        };

        for (var testCase : testCases) {
            for (var cffv : ClassFileFormatVersion.values()) {
                var transition1 = testCase.transition1();
                var transition2 = testCase.transition2();
                Set<AccessFlag.Location> expected;
                if (cffv.compareTo(transition1) < 0) {
                    expected = testCase.initialLocs();
                } else if (cffv.compareTo(transition1) >= 0 &&
                           cffv.compareTo(transition2) < 0) {
                    expected = testCase.firstLocs();
                } else { // cffv >= transition2
                    expected = testCase.secondLocs();
                }

                compareLocations(expected, testCase.accessFlag(), cffv);
            }
        }
    }

    private static void testSynthetic() {
        for (var cffv : ClassFileFormatVersion.values()) {
            Set<AccessFlag.Location> expected;
            if (cffv.compareTo(ClassFileFormatVersion.RELEASE_5) < 0) {
                expected = Set.of();
            } else {
                expected =
                    switch(cffv) {
                        case RELEASE_5, RELEASE_6,
                             RELEASE_7 -> Set.of(Location.CLASS, Location.FIELD,
                                                 Location.METHOD,
                                                 Location.INNER_CLASS);
                        case RELEASE_8 -> Set.of(Location.CLASS, Location.FIELD,
                                                 Location.METHOD,
                                                 Location.INNER_CLASS,
                                                 Location.METHOD_PARAMETER);
                        default        -> SYNTHETIC.locations();
                    };
            }
        compareLocations(expected, SYNTHETIC, cffv);
        }
    }

    private static void testStrict() {
        for (var cffv : ClassFileFormatVersion.values()) {
            Set<AccessFlag.Location> expected =
                (cffv.compareTo(ClassFileFormatVersion.RELEASE_2)  >= 0 &&
                 cffv.compareTo(ClassFileFormatVersion.RELEASE_16) <= 0) ?
                Set.of(Location.METHOD) :
                Set.of();
            compareLocations(expected, STRICT, cffv);
        }
    }

    private static void testLatestMatch() {
        // Verify accessFlag.locations() and
        // accessFlag.locations(ClassFileFormatVersion.latest()) are
        // consistent
        var LATEST = ClassFileFormatVersion.latest();
        for (var accessFlag : AccessFlag.values()) {
            var locationSet = accessFlag.locations();
            var locationLatestSet = accessFlag.locations(LATEST);
            if (!locationSet.equals(locationLatestSet)) {
                throw new RuntimeException("Unequal location sets for " + accessFlag);
            }
        }
    }

    private static void testFlagVersionConsistency() {
        for (var flag : AccessFlag.values()) {
            for (var location : AccessFlag.Location.values()) {
                if (location.flags().contains(flag) != flag.locations().contains(location)) {
                    throw new RuntimeException(String.format("AccessFlag and Location inconsistency:" +
                            "flag %s and location %s are inconsistent for the latest version"));
                }
            }
        }
        for (var cffv : ClassFileFormatVersion.values()) {
            for (var flag : AccessFlag.values()) {
                for (var location : AccessFlag.Location.values()) {
                    if (location.flags(cffv).contains(flag) != flag.locations(cffv).contains(location)) {
                        throw new RuntimeException(String.format("AccessFlag and Location inconsistency:" +
                                "flag %s and location %s are inconsistent for class file version %s"));
                    }
                }
            }
        }
    }

    private static void testLocationMaskFlagConsistency() {
        for (var location : AccessFlag.Location.values()) {
            if (!flagsAndMaskMatch(location.flags(), location.flagsMask())) {
                throw new RuntimeException(String.format("Flags and mask mismatch for %s", location));
            }
            for (var cffv : ClassFileFormatVersion.values()) {
                if (!flagsAndMaskMatch(location.flags(cffv), location.flagsMask(cffv))) {
                    throw new RuntimeException(String.format("Flags and mask mismatch for %s in %s", location, cffv));
                }
            }
        }
    }

    private static boolean flagsAndMaskMatch(Set<AccessFlag> flags, int mask) {
        for (var flag : flags) {
            int bit = flag.mask();
            if (((mask & bit) == 0))
                return false;
            mask &= ~bit;
        }
        return mask == 0;
    }
}
