/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4463560
 * @requires (os.family == "windows")
 * @summary Tests that datatransfer doesn't take too much time to complete
 * @key headful
 * @library /test/lib
 * @run main/timeout=300 ClipboardPerformanceTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ClipboardPerformanceTest {
    public static final int CODE_FAILURE = 1;
    public static final int CODE_OTHER_FAILURE = 2;
    static String eoln;
    static char[] text;
    public static final int ARRAY_SIZE = 100000;
    public static final int RATIO_THRESHOLD = 10;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ClipboardPerformanceTest clipboardPerformanceTest = new ClipboardPerformanceTest();
            clipboardPerformanceTest.initialize();
            return;
        }

        long before, after, oldTime, newTime;
        float ratio;

        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            before = System.currentTimeMillis();
            String ss = (String) t.getTransferData(new DataFlavor("text/plain; class=java.lang.String"));
            after = System.currentTimeMillis();

            System.err.println("Size: " + ss.length());
            newTime = after - before;
            System.err.println("Time consumed: " + newTime);

            initArray();

            StringBuffer buf = new StringBuffer(new String(text));
            int eoln_len = eoln.length();
            before = System.currentTimeMillis();

            for (int i = 0; i + eoln_len <= buf.length(); i++) {
                if (eoln.equals(buf.substring(i, i + eoln_len))) {
                    buf.replace(i, i + eoln_len, "\n");
                }
            }

            after = System.currentTimeMillis();
            oldTime = after - before;
            System.err.println("Old algorithm: " + oldTime);
            ratio = oldTime / newTime;
            System.err.println("Ratio: " + ratio);

            if (ratio < RATIO_THRESHOLD) {
                System.out.println("Time ratio failure!!");
                System.exit(CODE_FAILURE);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(CODE_OTHER_FAILURE);
        }
        System.out.println("Test Pass!");
    }

    public static void initArray() {
        text = new char[ARRAY_SIZE + 2];

        for (int i = 0; i < ARRAY_SIZE; i += 3) {
            text[i] = '\r';
            text[i + 1] = '\n';
            text[i + 2] = 'a';
        }
        eoln = "\r\n";
    }

    public void initialize() throws Exception {
        initArray();
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection(new String(text)), null);

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                ClipboardPerformanceTest.class.getName(),
                "child"
        );

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for Child");
        }

        outputAnalyzer.shouldHaveExitValue(0);
    }
}
