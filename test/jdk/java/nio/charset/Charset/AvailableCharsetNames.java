/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4422044
 * @summary Ensure that keys in available-charset map
 *          are identical to canonical names
 * @run junit AvailableCharsetNames
 */

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AvailableCharsetNames {

    /**
     * Test that the keys in Charset.availableCharsets()
     * are equal to the associated Charset.name() value.
     */
    @Test
    public void canonicalNamesTest() {
        for (String charsetName : Charset.availableCharsets().keySet()) {
            Charset charset = Charset.forName(charsetName);
            assertEquals(charset.name(), charsetName, "Charset name mismatch");
        }
    }
}
