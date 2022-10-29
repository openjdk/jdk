/*
* Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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


import java.io.File;
import java.io.FileReader;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/* @test
   @bug 8078268
   @summary  javax.swing.text.html.parser.Parser parseScript incorrectly optimized
   @run main bug8078268
*/
public class bug8078268 {
    private static final long TIMEOUT = 10_000;

    private static final String FILENAME = "slowparse.html";

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static volatile Exception exception;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                HTMLEditorKit htmlKit = new HTMLEditorKit();
                Document doc = htmlKit.createDefaultDocument();
                try {
                    htmlKit.read(new FileReader(getAbsolutePath()), doc, 0);
                } catch (Exception e) {
                    exception = e;
                }
                latch.countDown();
            }
        });

        if (!latch.await(TIMEOUT, MILLISECONDS)) {
            throw new RuntimeException("Parsing takes too long.");
        }
        if (exception != null) {
            throw exception;
        }
    }

    private static String getAbsolutePath() {
        return System.getProperty("test.src", ".")
               + File.separator + FILENAME;
    }
}
