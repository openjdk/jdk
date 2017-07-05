/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package validation;

import java.io.File;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.testng.annotations.Test;

/*
 * @summary Test Schema creation
 * @bug 8149915
 */
public class SchemaTest {

    /*
     * @bug 8149915
     * Verifies that the annotation validator is initialized with the security manager for schema
     * creation with http://apache.org/xml/features/validate-annotations=true.
     */
    @Test
    public void testValidation() throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature("http://apache.org/xml/features/validate-annotations", true);
        factory.newSchema(new File(getClass().getResource("Bug8149915.xsd").getFile()));
    }
}
