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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Basic tests for java.lang.IdentityException
 * @enablePreview
 * @run junit ${test.main.class}
 */
class IdentityExceptionBasicTest {

    /*
     * Verify the no-arg constructor
     */
    @Test
    void testNoArg() throws Exception {
        // verify that there's no exception message and no cause
        final IdentityException ex = new IdentityException();
        assertNull(ex.getMessage(), "unexpected exception message");
        assertNull(ex.getCause(), "unexpected cause");
    }

    /*
     * Verify the constructor which takes the String arg
     */
    @Test
    void testStringArg() throws Exception {
        // verify that null is allowed
        final IdentityException ex = new IdentityException((String) null);
        assertNull(ex.getMessage(), "unexpected exception message");
        assertNull(ex.getCause(), "unexpected cause");

        // verify custom exception message
        final IdentityException ex2 = new IdentityException("hello world");
        assertEquals("hello world", ex2.getMessage(), "unexpected exception message");
        assertNull(ex2.getCause(), "unexpected cause");
    }
}