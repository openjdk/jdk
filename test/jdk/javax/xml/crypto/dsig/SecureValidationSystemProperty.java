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

/**
 * @test
 * @bug 8301260
 * @summary Check that secureValidation system property works correctly
 * @library /test/lib
 * @run main/othervm -Dorg.jcp.xml.dsig.secureValidation=true SecureValidationSystemProperty
 */

/**
 * @test
 * @bug 8301260
 * @summary Check that secureValidation system property works correctly
 * @library /test/lib
 * @run main/othervm -Dorg.jcp.xml.dsig.secureValidation=false SecureValidationSystemProperty
 */


import java.io.File;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

import jdk.test.lib.security.SecurityUtils;
import jdk.test.lib.security.XMLUtils;
import static jdk.test.lib.security.XMLUtils.Validator;

public class SecureValidationSystemProperty {

    private final static String DIR = System.getProperty("test.src", ".");
    private final static String DATA_DIR = 
        DIR + System.getProperty("file.separator") + "data";

    public static void main(String[] args) throws Exception{
        // Re-enable sha1 algs. We just want to make sure DSA keys less than
        // 1024 bits are rejected correctly.
        SecurityUtils.removeAlgsFromDSigPolicy("sha1");

        String prop = System.getProperty("org.jcp.xml.dsig.secureValidation");
        if (prop == null) {
            throw new Exception("Secure validation system property not set");
        }
        boolean isSet = Boolean.parseBoolean(prop);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder()
            .parse(new File(DATA_DIR, "signature-enveloped-dsa-512.xml"));
        Validator validator = XMLUtils.validator();

        // try again and make sure system property supersedes
        // XMLContext property
        validator.secureValidation(!isSet);
        validate(validator, doc, isSet);
    }

    private static void validate(Validator validator, Document doc,
            boolean isSet) throws Exception {
        try {
            validator.validate(doc);
            if (isSet) {
                throw new Exception("signature expected to be rejected " +
                    "because it was signed with a 512-bit DSA key which is " +
                    "restricted");
            }
        } catch (XMLSignatureException e) {
            if (!isSet) {
                throw new Exception("signature not expected to be rejected " +
                    "because secure validation mode is not enabled");
            } else {
                Throwable cause = e.getCause();
                if (cause == null || !cause.getMessage().equals(
                        "DSA keys less than 1024 bits are forbidden when " +
                        "secure validation is enabled")) {
                    throw new Exception("signature rejected for wrong reason", e);
                }
            }
        }
    }
}
