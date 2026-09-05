/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package javax.xml.transform.ptests.othervm;

import org.junit.jupiter.api.Test;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Negative test for set invalid TransformerFactory property.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.transform.ptests.othervm.TFCErrorTest
 */
public class TFCErrorTest {
    private static final String TRANSFORMER_FACTORY = "javax.xml.transform.TransformerFactory";

    @Test
    public void tfce01() {
        System.setProperty(TRANSFORMER_FACTORY, "xx");
        try {
            TransformerFactoryConfigurationError e = assertThrows(
                    TransformerFactoryConfigurationError.class,
                    TransformerFactory::newInstance);
            assertInstanceOf(ClassNotFoundException.class, e.getException());
        } finally {
            System.clearProperty(TRANSFORMER_FACTORY);
        }
    }
}
