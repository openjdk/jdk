/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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


/* @test
   @bug 6203576 4700020
   @summary checks if the output of exportSubtree() is identical to
            the output from previous release.
 */

import java.io.*;
import java.util.prefs.*;

public class ExportSubtree {
   public static void main(String[] args) throws Exception {
      try
      {
          //File f = new File(System.getProperty("test.src", "."), "TestPrefs.xml");
          ByteArrayInputStream bais = new ByteArrayInputStream(importPrefs.getBytes("utf-8"));
          Preferences.importPreferences(bais);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          Preferences.userRoot().node("testExportSubtree").exportSubtree(baos);
          Preferences.userRoot().node("testExportSubtree").removeNode();
          if (!expectedResult.equals(baos.toString())) {
              //System.out.print(baos.toString());
              //System.out.print(expectedResult);
              throw new IOException("exportSubtree does not output expected result");
          }
      }
      catch( Exception e ) {
         e.printStackTrace();
      }
   }

   static String ls = System.getProperty("line.separator");
   static String importPrefs =
       "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">"
        + "<preferences EXTERNAL_XML_VERSION=\"1.0\">"
        + "  <root type=\"user\">"
        + "    <map>"
        + "      <entry key=\"key1\" value=\"value1\"/>"
        + "    </map>"
        + "    <node name=\"testExportSubtree\">"
        + "      <map>"
        + "        <entry key=\"key2\" value=\"value2\"/>"
        + "      </map>"
        + "      <node name=\"test\">"
        + "        <map>"
        + "          <entry key=\"key3\" value=\"value3\"/>"
        + "        </map>"
        + "      </node>"
        + "    </node>"
        + "  </root>"
        + "</preferences>";

   static String expectedResult =
       "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + ls    +  "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">"
        + ls    +  "<preferences EXTERNAL_XML_VERSION=\"1.0\">"
        + ls    +  "  <root type=\"user\">"
        + ls    +  "    <map/>"
        + ls    +  "    <node name=\"testExportSubtree\">"
        + ls    +  "      <map>"
        + ls    +  "        <entry key=\"key2\" value=\"value2\"/>"
        + ls    +  "      </map>"
        + ls    +  "      <node name=\"test\">"
        + ls    +  "        <map>"
        + ls    +  "          <entry key=\"key3\" value=\"value3\"/>"
        + ls    +  "        </map>"
        + ls    +  "      </node>"
        + ls    +  "    </node>"
        + ls    +  "  </root>"
        + ls    +  "</preferences>"     + ls;
}
