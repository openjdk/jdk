/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sax;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 6889654
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.Bug6889654Test
 * @summary Test SAXException includes whole information.
 */
public class Bug6889654Test {
    @Test
    public void testException() {
        RuntimeException cause = new RuntimeException("<cause message>");
        // The toString() of a SAXException includes the message of its cause.
        SAXException wrapped = new SAXException("<wrapped message>", cause);

        assertTrue(wrapped.toString().contains("<wrapped message>"));
        assertTrue(wrapped.toString().contains("<cause message>"));
    }
}
