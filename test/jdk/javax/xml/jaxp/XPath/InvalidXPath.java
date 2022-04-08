/*
 * Copyright (c) 2022, SAP SE. All rights reserved.
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
 * @bug 8284548
 * @summary Test whether the expected exception is thrown when
 *           trying to compile an invalid XPath expression.
 * @run main InvalidXPath
 */

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class InvalidXPath {

    public static void main(String... args) {
        // define an invalid XPath expression
        final String invalidXPath = ">>";

        // expect XPathExpressionException when the invalid XPath expression is compiled
        try {
            XPathFactory.newInstance().newXPath().compile(invalidXPath);
        } catch (XPathExpressionException e) {
            System.out.println("Caught expected exception: " + e.getClass().getName() +
                    "(" + e.getMessage() + ").");
        } catch (Exception e) {
            System.out.println("Caught unexpected exception: " + e.getClass().getName() +
                    "(" + e.getMessage() + ")!");
            throw e;
        }
    }
}
