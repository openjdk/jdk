/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.SAXException;


class XmlUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "/,/",
        "/project,/project",
        "project,/project",
        "(//@a)[1],/project/foo/bar/@a",
        "(//@a)[last()],/project/foo[2]/buz/@a",
        "//bar/text(),",
        "(//foo)[last()],/project/foo[2]",
        "(//buz)[last()],/project/foo[2]/buz[3]",
    })
    void test_pathOf(String xPathExpr, String expected) throws SAXException, IOException, XPathExpressionException {

        var dom = XmlUtils.initDocumentBuilder().parse(new ByteArrayInputStream("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <name>Duke</name>
                  <version>1.0.0</version>
                  <active>true</active>
                  <active>false</active>
                  <foo>
                      <bar a="1">foo</bar>
                  </foo>
                  <foo>
                      <buz a="1"/>
                      <buz b=""/>
                      <buz b=""/>
                  </foo>
                </project>
                """.getBytes(StandardCharsets.UTF_8)));

        var xPath = XPathFactory.newInstance().newXPath();

        var nodes = XmlUtils.queryNodes(dom, xPath, xPathExpr).limit(2).toList();
        assertEquals(1, nodes.size());

        if (expected != null) {
            assertEquals(expected, XmlUtils.pathOf(nodes.getFirst()));
        } else {
            assertThrowsExactly(IllegalArgumentException.class, () -> {
                XmlUtils.pathOf(nodes.getFirst());
            });
        }
    }
}
