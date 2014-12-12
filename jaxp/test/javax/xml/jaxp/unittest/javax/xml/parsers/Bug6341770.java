/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.parsers;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import javax.xml.parsers.SAXParserFactory;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * @bug 6341770
 * @summary Test external entity linked to non-ASCII base URL.
 */
public class Bug6341770 {

    // naming a file "aux" would fail on windows.
    @Test
    public void testNonAsciiURI() {
        try {
            File dir = File.createTempFile("sko\u0159ice", null);
            dir.delete();
            dir.mkdir();
            File main = new File(dir, "main.xml");
            PrintWriter w = new PrintWriter(new FileWriter(main));
            w.println("<!DOCTYPE r [<!ENTITY aux SYSTEM \"aux1.xml\">]>");
            w.println("<r>&aux;</r>");
            w.flush();
            w.close();
            File aux = new File(dir, "aux1.xml");
            w = new PrintWriter(new FileWriter(aux));
            w.println("<x/>");
            w.flush();
            w.close();
            System.out.println("Parsing: " + main);
            SAXParserFactory.newInstance().newSAXParser().parse(main, new DefaultHandler() {
                public void startElement(String uri, String localname, String qname, Attributes attr) throws SAXException {
                    System.out.println("encountered <" + qname + ">");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception: " + e.getMessage());
        }
        System.out.println("OK.");
    }
}
