/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4302894
 * @summary check that bad X.509 encoded certificate data throws
 *      CertificateParsingException
 */

import java.io.*;
import java.security.cert.*;

public class BadX509CertData {

    private static final String data = "\211\0\225\3\5\0\70\154\157\231";

    public static void main(String[] args) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        InputStream is = new ByteArrayInputStream(data.getBytes("ISO8859_1"));
        try {
            Certificate cert = factory.generateCertificate(is);
        } catch (CertificateException ce) {
            return;
        }
        throw new Exception("CertificateFactory.generateCertificate() did "
            + "not throw CertificateParsingException on bad X.509 cert data");
    }
}
