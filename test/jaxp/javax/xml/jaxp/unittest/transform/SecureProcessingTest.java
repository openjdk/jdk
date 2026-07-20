/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

package transform;

import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import static jaxp.library.JAXPTestUtilities.SRC_DIR;
import static jaxp.library.JAXPTestUtilities.assertDoesNotThrow;
import static jaxp.library.JAXPTestUtilities.getSystemId;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static transform.XSLTFunctionsTest.SP_ENABLE_EXTENSION_FUNCTION_SPEC;

/*
 * @test
 * @bug 8343001 8343001
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm transform.SecureProcessingTest
 * @summary Verifies that XSLT reports TransformerException as it processes xsl
 * using extension functions while FEATURE_SECURE_PROCESSING is set to true.
 */
public class SecureProcessingTest {
    /**
     * Test state
     */
    public static enum TestState {
        DEFAULT,  // the default state
        SETFSP,   // set FEATURE_SECURE_PROCESSING
        SETPROPERTY; // set the enalbeExtensionFunctions property
    }

    @DataProvider(name = "extFunc")
    public Object[][] getExtFuncSettings() throws Exception {
        return new Object[][] {
            // by default, Extension Functions are disallowed
            { TestState.DEFAULT, true, null, false, TransformerException.class},
            // set FSP=true, Extension Functions are disallowed
            { TestState.SETFSP, true, null, false, TransformerException.class},
            // turning off FSP does not enable Extension Functions
            { TestState.SETFSP, false, null, false, TransformerException.class},
            // between FSP and the Extension Functions property (jdk.xml.enableExtensionFunctions),
            // the later takes precedence
            { TestState.SETPROPERTY, true, SP_ENABLE_EXTENSION_FUNCTION_SPEC, false, TransformerException.class},
            { TestState.SETPROPERTY, true, SP_ENABLE_EXTENSION_FUNCTION_SPEC, true, null},
        };
    }
    /**
     * Verifies the effect of FEATURE_SECURE_PROCESSING (FSP) and the precedence
     * between FSP and the Extension Functions property.
     *
     * @param testState the state of the test
     * @param fspValue the FSP value to be set
     * @param property the Extension Functions property
     * @param propertyValue the property value
     * @param expectedThrow the expected throw if the specified DTD can not be
     *                      resolved.
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "extFunc")
    public void testFSP(TestState testState, boolean fspValue, String property,
            boolean propertyValue, Class<Throwable> expectedThrow)
            throws Exception {
        final TransformerFactory tf = TransformerFactory.newInstance();
        switch (testState) {
            case DEFAULT:
                break;
            case SETFSP:
                tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, fspValue);
                break;
            case SETPROPERTY:
                tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, fspValue);
                tf.setFeature(property, propertyValue);
                break;
        }
        if (expectedThrow == null) {
            assertDoesNotThrow(() -> runTransform(tf), "Unexpected exception.");
        } else {
            Assert.assertThrows(expectedThrow, () -> runTransform(tf));
        }
    }

    private void runTransform(TransformerFactory tf)
            throws Exception {
        StreamSource xslSource = new StreamSource(getSystemId(SRC_DIR + "/SecureProcessingTest.xsl"));
        StreamSource xmlSource = new StreamSource(getSystemId(SRC_DIR + "/SecureProcessingTest.xml"));

        // the xml result
        StringWriter xmlResultString = new StringWriter();
        StreamResult xmlResultStream = new StreamResult(xmlResultString);
        Transformer transformer = tf.newTransformer(xslSource);
        transformer.transform(xmlSource, xmlResultStream);
    }
}
