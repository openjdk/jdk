/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8081474
 * @summary  Verifies if SwingWorker calls 'done'
 *           before the 'doInBackground' is finished
 * @run main TestDoneBeforeDoInBackground
 */
import javax.swing.SwingWorker;

public class TestDoneBeforeDoInBackground {

    static boolean doInBackground = false;
    static final int WAIT_TIME = 2000;

    public static void main(String[] args) throws InterruptedException {
        SwingWorker<String, String> worker =
            new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        System.out.println("Working...");
                        Thread.sleep(WAIT_TIME);
                    }
                } catch (InterruptedException ex) {
                    System.out.println("Got interrupted!");
                }
                try {
                    System.out.println("Cleaning up");
                    Thread.sleep(WAIT_TIME);
                    System.out.println("Done cleaning");
                    doInBackground = true;
                } catch (InterruptedException ex) {
                    System.out.println("Got interrupted second time!");
                }
                return null;
            }

            @Override
            protected void done() {
                if (!doInBackground) {
                    throw new RuntimeException("done called before doInBackground");
                }
                System.out.println("Done");
            }
        };

        worker.execute();
        Thread.sleep(WAIT_TIME);

        worker.cancel(true);
        Thread.sleep(WAIT_TIME);
    }
}

