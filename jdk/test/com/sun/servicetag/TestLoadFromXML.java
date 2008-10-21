/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * @bug     6622366
 * @summary Basic Test for RegistrationData.loadFromXML
 * @author  Mandy Chung
 *
 * @run build TestLoadFromXML
 * @run main TestLoadFromXML
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class TestLoadFromXML {
    public static void main(String[] argv) throws Exception {
        String registrationDir = System.getProperty("test.classes");
        String servicetagDir = System.getProperty("test.src");

        File inFile = new File(servicetagDir, "registration.xml");
        File outFile = new File(registrationDir, "out.xml");
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(inFile));
        RegistrationData regData = RegistrationData.loadFromXML(in);
        boolean closed = false;
        try {
           in.read();
        } catch (IOException e) {
           // expect the InputStream is closed
           closed = true;
           System.out.println("*** Expected IOException ***");
           e.printStackTrace();
        }
        if (!closed) {
           throw new RuntimeException("InputStream not closed after " +
               "RegistrationData.loadFromXML() call");
        }

        BufferedOutputStream out =
            new BufferedOutputStream(new FileOutputStream(outFile));
        regData.storeToXML(out);
        // should be able to write to the OutputStream
        out.write(0);
    }
}
