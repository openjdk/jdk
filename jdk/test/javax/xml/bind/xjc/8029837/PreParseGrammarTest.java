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

/*
 * @test
 * @bug 8029837
 * @summary Test simulates the partial call to xjc ant task that fails with
 *          NullPointer exception
 * @run main/othervm PreParseGrammarTest
 */

import com.sun.org.apache.xerces.internal.parsers.XMLGrammarPreparser;
import com.sun.org.apache.xerces.internal.xni.XNIException;
import com.sun.org.apache.xerces.internal.xni.grammars.Grammar;
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarDescription;
import com.sun.org.apache.xerces.internal.xni.parser.XMLInputSource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class PreParseGrammarTest {

    public static void main(String[] args) throws FileNotFoundException, XNIException, IOException {
        File xsdf = new File(System.getProperty("test.src", ".") + "/test.xsd");
        InputStream is = new BufferedInputStream(new FileInputStream(xsdf));
        XMLInputSource xis = new XMLInputSource(null, null, null, is, null);
        XMLGrammarPreparser gp = new XMLGrammarPreparser();
        gp.registerPreparser(XMLGrammarDescription.XML_SCHEMA, null);
        //The NullPointerException is observed on next call during ant task
        // execution
        Grammar res = gp.preparseGrammar(XMLGrammarDescription.XML_SCHEMA, xis);
        System.out.println("Grammar preparsed successfully:" + res);
        return;
    }
}
