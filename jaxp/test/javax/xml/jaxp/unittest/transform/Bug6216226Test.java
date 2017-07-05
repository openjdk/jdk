/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package transform;

import java.io.File;
import java.io.StringReader;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @bug 6216226
 * @summary Test StreamResult(File) is closed after transform().
 */
public class Bug6216226Test {

    @Test
    public final void test() {
        try {
            File test = new File("bug6216226.txt");
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer xformer = tf.newTransformer();
            StringReader st = new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?><doc></doc>");
            StreamSource s = new StreamSource(st);
            StreamResult r = new StreamResult(test);
            xformer.transform(s, r);
            if (!test.delete()) {
                Assert.fail("cannot delete file: " + test.getPath());
            }
        } catch (Exception e) {
            // unexpected failure
            e.printStackTrace();
            Assert.fail(e.toString());
        }
    }
}
