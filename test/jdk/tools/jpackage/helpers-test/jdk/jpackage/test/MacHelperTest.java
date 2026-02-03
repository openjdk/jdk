/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.XmlUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class MacHelperTest {

    @Test
    public void test_flatMapPList() {
        var props = MacHelper.flatMapPList(new PListReader(createXml(
                "<key>AppName</key>",
                "<string>Hello</string>",
                "<key>AppVersion</key>",
                "<real>1.0</real>",
                "<key>UserData</key>",
                "<dict>",
                "  <key>Foo</key>",
                "  <array>",
                "    <string>Str</string>",
                "    <array>",
                "      <string>Another Str</string>",
                "      <true/>",
                "      <false/>",
                "    </array>",
                "  </array>",
                "</dict>",
                "<key>Checksum</key>",
                "<data>7841ff0076cdde93bdca02cfd332748c40620ce4</data>",
                "<key>Plugins</key>",
                "<array>",
                "  <dict>",
                "    <key>PluginName</key>",
                "    <string>Foo</string>",
                "    <key>Priority</key>",
                "    <integer>13</integer>",
                "    <key>History</key>",
                "    <array>",
                "      <string>New File</string>",
                "      <string>Another New File</string>",
                "    </array>",
                "  </dict>",
                "  <dict>",
                "    <key>PluginName</key>",
                "    <string>Bar</string>",
                "    <key>Priority</key>",
                "    <real>23</real>",
                "    <key>History</key>",
                "    <array/>",
                "  </dict>",
                "  <dict/>",
                "</array>"
        )));

        assertEquals(Map.ofEntries(
                entry("/AppName", "Hello"),
                entry("/AppVersion", "1.0"),
                entry("/UserData/Foo[0]", "Str"),
                entry("/UserData/Foo[1][0]", "Another Str"),
                entry("/UserData/Foo[1][1]", "true"),
                entry("/UserData/Foo[1][2]", "false"),
                entry("/Checksum", "7841ff0076cdde93bdca02cfd332748c40620ce4"),
                entry("/Plugins[0]/PluginName", "Foo"),
                entry("/Plugins[0]/Priority", "13"),
                entry("/Plugins[0]/History[0]", "New File"),
                entry("/Plugins[0]/History[1]", "Another New File"),
                entry("/Plugins[1]/PluginName", "Bar"),
                entry("/Plugins[1]/Priority", "23"),
                entry("/Plugins[1]/History[]", ""),
                entry("/Plugins[2]{}", "")
        ), props);
    }

    private static String createPListXml(String ...xml) {
        final List<String> content = new ArrayList<>();
        content.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        content.add("<plist version=\"1.0\">");
        content.add("<dict>");
        content.addAll(List.of(xml));
        content.add("</dict>");
        content.add("</plist>");
        return String.join("", content.toArray(String[]::new));
    }

    private static Node createXml(String ...xml) {
        try {
            return XmlUtils.initDocumentBuilder().parse(new InputSource(new StringReader(createPListXml(xml))));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (SAXException ex) {
            throw new RuntimeException(ex);
        }
    }
}
