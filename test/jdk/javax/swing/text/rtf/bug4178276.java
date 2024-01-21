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

/*
 * @test
 * @bug 4178276
 * @key headful
 * @summary  RTFEditorkit.write(...) doesn't throw NPE when used in SecurityManager
 * @run main/othervm/secure=allow bug4178276
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

public class bug4178276 {

    public static void main(String[] argv) throws Exception {
        System.setSecurityManager(new SecurityManager());

        String test="{\\rtf1\\ansi\\deff0\\deftab720{\\fonttbl{\\f0\\f swiss MS Sans Serif;}}{\\colortbl\\red0\\green0\\blue0;}\\qc\\plain\\f0 Test 1 \\par \\ql\\plain\\f0 Test 2 \\par \\qr\\plain\\f0 Test 3 \\par \\qj\\plain\\f0 Test 4}";
        RTFEditorKit c = new RTFEditorKit();
        Document doc = c.createDefaultDocument();
        try {
            c.read(new ByteArrayInputStream(test.getBytes(
                                        StandardCharsets.ISO_8859_1)), doc, 0);
            ByteArrayOutputStream sw = new ByteArrayOutputStream();
            c.write(sw, doc, 0, 0);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected NPE exception...", e);
        }
    }
}
