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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestDoneBeforeDoInBackground {

    private static boolean doInBackground = false;
    private static final int WAIT_TIME = 200;
    private static final long CLEANUP_TIME = 1000;
    private static final CountDownLatch doneLatch = new CountDownLatch(1);

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
                    Thread.sleep(CLEANUP_TIME);
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
                doneLatch.countDown();
            }
        };

        worker.execute();
        Thread.sleep(WAIT_TIME * 3);

        final long start = System.currentTimeMillis();
        worker.cancel(true);
        final long end = System.currentTimeMillis();

        if ((end - start) > 100) {
            throw new RuntimeException("Cancel took too long: "
                                       + ((end - start) / 1000.0d) + " s");
        }
        if (!doneLatch.await(CLEANUP_TIME + 1000L, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("done didn't complete in time");
        }
        System.out.println("doInBackground " + doInBackground +
                           " getState " + worker.getState());
        if (doInBackground && worker.getState() != SwingWorker.StateValue.DONE) {
            throw new RuntimeException("doInBackground is finished " +
                                       " but State is not DONE");
        }
    }
}

