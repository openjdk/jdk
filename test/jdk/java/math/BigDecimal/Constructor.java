/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4259453 8200698
 * @summary Test constructors of BigDecimal
 * @library ..
 * @run junit Constructor
 */

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class Constructor {
    @Test
    public void stringConstructor() {
        Assertions.assertThrows(NumberFormatException.class, () -> {
            BigDecimal bd = new BigDecimal("1.2e");
        });
    }

    @Test
    public void charArrayConstructorNegativeOffset() {
        Assertions.assertThrows(NumberFormatException.class, () -> {
            BigDecimal bd = new BigDecimal(new char[5], -1, 4, null);
        });
    }

    @Test
    public void charArrayConstructorNegativeLength() {
        Assertions.assertThrows(NumberFormatException.class, () -> {
            BigDecimal bd = new BigDecimal(new char[5], 0, -1, null);
        });
    }

    @Test
    public void charArrayConstructorIntegerOverflow() {
        Assertions.assertThrows(NumberFormatException.class, () -> {
            try {
                BigDecimal bd = new BigDecimal(new char[5], Integer.MAX_VALUE - 5,
                        6, null);
            } catch (NumberFormatException nfe) {
                if (nfe.getCause() instanceof IndexOutOfBoundsException) {
                    throw new RuntimeException
                            ("NumberFormatException should not have a cause");
                } else {
                    throw nfe;
                }
            }
        });
    }

    @Test
    public void charArrayConstructorIndexOutOfBounds() {
        Assertions.assertThrows(NumberFormatException.class, () -> {
            BigDecimal bd = new BigDecimal(new char[5], 1, 5, null);
        });
    }
}
