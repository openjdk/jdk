/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326908
 * @summary Verify an empty pattern does not cause an OutOfMemoryError when
 *          toPattern is invoked. Behavioral change of MAXIMUM_INTEGER_DIGITS
 *          replaced with DOUBLE_FRACTION_DIGITS for empty pattern initialization.
 *          In practice, this should cause minimal compatibility issues.
 * @run junit EmptyToPatternTest
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.text.DecimalFormat;

public class EmptyToPatternTest {

    // 8326908: Verify that invoking toPattern on a DecimalFormat created
    // with an empty String does not throw Out Of Memory Error.
    @Test
    public void emptyStringPatternTest() {
        assertDoesNotThrow(() -> new DecimalFormat("").toPattern());
    }
}
