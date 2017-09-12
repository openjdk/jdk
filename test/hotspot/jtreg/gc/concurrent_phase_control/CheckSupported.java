/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package gc.concurrent_phase_control;

/*
 * Utility class that provides verification of expected behavior of
 * the Concurrent GC Phase Control WB API when the current GC supports
 * phase control.  The invoking test must provide WhiteBox access.
 */

import sun.hotspot.WhiteBox;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CheckSupported {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static Set<String> toSet(List<String> list, String which)
        throws Exception
    {
        Set<String> result = new HashSet<String>(list);
        if (result.size() < list.size()) {
            throw new RuntimeException(which + " phases contains duplicates");
        }
        return result;
    }

    private static void checkPhases(String[] expectedPhases) throws Exception {
        String[] actualPhases = WB.getConcurrentGCPhases();

        List<String> expectedList = Arrays.asList(expectedPhases);
        List<String> actualList = Arrays.asList(actualPhases);

        Set<String> expected = toSet(expectedList, "Expected");
        Set<String> actual = toSet(actualList, "Actual");

        expected.removeAll(actualList);
        actual.removeAll(expectedList);

        boolean match = true;
        if (!expected.isEmpty()) {
            match = false;
            System.out.println("Unexpected phases:");
            for (String s: expected) {
                System.out.println("  " + s);
            }
        }
        if (!actual.isEmpty()) {
            match = false;
            System.out.println("Expected but missing phases:");
            for (String s: actual) {
                System.out.println("  " + s);
            }
        }
        if (!match) {
            throw new RuntimeException("Mismatch between expected and actual phases");
        }
    }

    public static void check(String gcName, String[] phases) throws Exception {
        // Verify supported.
        if (!WB.supportsConcurrentGCPhaseControl()) {
            throw new RuntimeException(
                gcName + " unexpectedly missing phase control support");
        }

        checkPhases(phases);

        // Verify IllegalArgumentException thrown by request attempt
        // with unknown phase.
        boolean illegalArgumentThrown = false;
        try {
            WB.requestConcurrentGCPhase("UNKNOWN PHASE");
        } catch (IllegalArgumentException e) {
            // Expected.
            illegalArgumentThrown = true;
        } catch (Exception e) {
            throw new RuntimeException(
                gcName + ": Unexpected exception when requesting unknown phase: " + e.toString());
        }
        if (!illegalArgumentThrown) {
            throw new RuntimeException(
                gcName + ": No exception when requesting unknown phase");
        }
    }
}

