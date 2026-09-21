/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.xpath.ptests;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathNodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 8183266
 * @summary verifies the specification of the XPathEvaluationResult API
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.xpath.ptests.XPathEvaluationResultTest
 */
public class XPathEvaluationResultTest {

    /*
     * Test getQNameType returns QName type for supported types and Number subtypes
     */
    @ParameterizedTest
    @MethodSource("getSupportedTypes")
    public void testQNameTypeSupportedTypes(QName expectedQName, Class<?> type) {
        QName qName = XPathEvaluationResult.XPathResultType.getQNameType(type);
        assertNotNull(qName);
        assertEquals(expectedQName, qName);
    }

    /*
     * Test getQNameType returns null when type is not supported
     */
    public static Object[][] getSupportedTypes() {
        return new Object[][] {
                { XPathConstants.STRING, String.class },
                { XPathConstants.BOOLEAN, Boolean.class },
                { XPathConstants.NODESET, XPathNodes.class },
                { XPathConstants.NODE, Node.class },
                { XPathConstants.NUMBER, Long.class },
                { XPathConstants.NUMBER, Integer.class },
                { XPathConstants.NUMBER, Double.class },
                { XPathConstants.NUMBER, Number.class }
        };
    }
}
