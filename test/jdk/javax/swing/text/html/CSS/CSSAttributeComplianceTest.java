/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6245410
 * @summary  Verifies CSS.Attribute: BACKGROUND_POSITION is w3c spec compliant
 * @run main CSSAttributeComplianceTest
 */

import javax.swing.text.html.CSS.Attribute;

public class CSSAttributeComplianceTest {

    public static void main(String[] argv) {
        String val = Attribute.BACKGROUND_POSITION.getDefaultValue();
        if (!"0% 0%".equals(val)) {
            System.out.println("Attribute.BACKGROUND_POSITION value : " + val);
            throw new RuntimeException("Expected from w3c.background-position: " +
                                       " Initial: 0% 0%");
        }
    }
}
