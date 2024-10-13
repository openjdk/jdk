/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Reader;
import java.io.StringReader;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/*
 * @test
 * @bug 4238223
 * @summary Tests that HTMLEditorKit.ParserCallback methods receive
 *          correct 'pos' argument.
 */

public class bug4238223 {

    public static void main(String[] argv) throws Exception {
        TestParser parser = new TestParser();
        String testHTML = "<HTML><HEAD><TITLE>Text</TITLE></HEAD>" +
                "<BODY><WRONGTAG>Simple text<!--comment--></BODY></HTML>";
        parser.parse(testHTML);
    }

    static class TestCallback extends HTMLEditorKit.ParserCallback {
        String commentData = "comment";
        int commentIndex = 65;

        public void handleComment(char[] data, int pos) {
            if (!(new String(data)).equals(commentData)
                    || pos != commentIndex) {

                throw new RuntimeException("handleComment failed");
            }
        }

        HTML.Tag[] endTags = {HTML.Tag.TITLE, HTML.Tag.HEAD,
                HTML.Tag.BODY, HTML.Tag.HTML};
        int[] endTagPositions = {23, 31, 79, 86};
        int endTagIndex = 0;
        public void handleEndTag(HTML.Tag tag, int pos) {
            if (!tag.equals(endTags[endTagIndex])
                    || pos != endTagPositions[endTagIndex]) {

                throw new RuntimeException("handleEndTag failed");
            } else {
                endTagIndex++;
            }
        }

        int errorIndex = 54;
        public void handleError(String errorMsg, int pos) {
            if (pos != errorIndex) {
                throw new RuntimeException("handleError failed");
            }
        }

        int[] simpleTagPositions = {44, 93};
        int simpleTagIndex = 0;
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attr,
                                    int pos) {
            if (pos != simpleTagPositions[simpleTagIndex++]) {
                throw new RuntimeException("handleSimpleTag failed");
            }
        }

        HTML.Tag[] startTags = {HTML.Tag.HTML, HTML.Tag.HEAD,
                HTML.Tag.TITLE, HTML.Tag.BODY};
        int[] startTagPositions = {0, 6, 12, 38};
        int startTagIndex = 0;
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attr,
                                   int pos) {
            if (!tag.equals(startTags[startTagIndex])
                    || pos != startTagPositions[startTagIndex]) {

                throw new RuntimeException("handleStartTag failed");
            } else {
                startTagIndex++;
            }
        }

        String[] textData = {"Text", "Simple text"};
        int[] textPositions = {19, 54};
        int textIndex = 0;
        public void handleText(char[] data, int pos) {
            if (!textData[textIndex].equals(new String(data))
                    || pos != textPositions[textIndex]) {

                throw new RuntimeException("handleText failed");
            } else {
                textIndex++;
            }
        }
    }

    static class TestParser extends ParserDelegator {
        public void parse(String html) throws Exception {
            Reader r = new StringReader(html);
            super.parse(r, new TestCallback(), false);
            r.close();
        }
    }
}
