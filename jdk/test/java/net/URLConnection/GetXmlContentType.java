/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4160195
 * @summary Check for correct detection of XML content type
 */

import java.io.*;
import java.net.*;


public class GetXmlContentType {

    static final String XML_MIME_TYPE = "application/xml";

    // guess type from content and filename
    static final String goodFiles [] = {
        "xml1",         // US-ASCII and supersets
        "xml2.xml",     // typed inferred from filename
        "xml3",         // UTF-16 little endian (partial)
        "xml4"          // UTF-16 big endian (partial)
        };

    // some common non-XML examples
    static final String badFiles [] = {
        "not-xml1",
        "not-xml2"
        };

    public static void main(String[] args) throws Exception {
        boolean sawError = false;

        //
        // POSITIVE tests:  good data --> good result
        //
        for (int i = 0; i < goodFiles.length; i++) {
            String      result = getUrlContentType (goodFiles [i]);

            if (!XML_MIME_TYPE.equals (result)) {
                System.out.println ("Wrong MIME type: "
                    + goodFiles [i]
                    + " --> " + result
                    );
                sawError = true;
            }
        }

        //
        // NEGATIVE tests:  bad data --> correct diagnostic
        //
        for (int i = 0; i < badFiles.length; i++) {
            String      result = getUrlContentType (badFiles [i]);

            if (XML_MIME_TYPE.equals (result)) {
                System.out.println ("Wrong MIME type: "
                    + badFiles [i]
                    + " --> " + result
                    );
                sawError = true;
            }
        }

        if (sawError)
            throw new Exception (
                "GetXmlContentType Test failed; see diagnostics.");
    }

    static String getUrlContentType (String name) throws IOException {
        File            file = new File(System.getProperty("test.src", "."), "xml");
        URL             u = new URL ("file:"
                            + file.getCanonicalPath()
                            + file.separator
                            + name);
        URLConnection   conn = u.openConnection ();

        return conn.getContentType ();
    }

}
