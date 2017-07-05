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

/**
 * @test
 * @bug 8134111
 * @summary test that elements without namespace is ignored by unmarshaller
 *          when elementFormDefault is set to QUALIFIED.
 * @compile testTypes/package-info.java testTypes/Root.java
 *          testTypes/WhenType.java testTypes/ObjectFactory.java
 * @modules java.xml.bind
 * @run testng/othervm UnmarshalTest
 */

import java.io.StringReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.testng.annotations.Test;
import static org.testng.Assert.assertNull;
import org.xml.sax.InputSource;
import testTypes.Root;

public class UnmarshalTest {

    @Test
    public void unmarshalUnexpectedNsTest() throws Exception {
        JAXBContext context;
        Unmarshaller unm;
        // Create JAXB context from testTypes package
        context = JAXBContext.newInstance("testTypes");
        // Create unmarshaller from JAXB context
        unm = context.createUnmarshaller();
        // Unmarshall xml document with unqualified dtime element
        Root r = (Root) unm.unmarshal(new InputSource(new StringReader(DOC)));
        // Print dtime value and check if it is null
        System.out.println("dtime is:"+r.getWhen().getDtime());
        assertNull(r.getWhen().getDtime());
    }

    //Xml document to unmarshall with unqualified dtime element
    private final String DOC =
            "<tns:root xmlns:tns=\"http://www.example.org/testNamespace/\">" +
            "<tns:when>" +
            "<dtime>2015-06-24T13:16:14.933-04:00</dtime>" +
            "</tns:when>" +
            "</tns:root>";
}
