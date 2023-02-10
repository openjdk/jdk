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
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.SwingWorker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestDoneBeforeDoInBackground {

    private static final int WAIT_TIME = 200;
    private static final long CLEANUP_TIME = 1000;

    private static final AtomicBoolean doInBackgroundStarted = new AtomicBoolean(false);
    private static final AtomicBoolean doInBackgroundFinished = new AtomicBoolean(false);
    private static final CountDownLatch doneLatch = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        SwingWorker<String, String> worker = new SwingWorker<>() {
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
                    doInBackgroundStarted.set(true);
                    System.out.println("Cleaning up");
                    Thread.sleep(CLEANUP_TIME);
                    System.out.println("Done cleaning");
                    doInBackgroundFinished.set(true);
                } catch (InterruptedException ex) {
                    System.out.println("Got interrupted second time!");
                }

                return null;
            }

            @Override
            protected void done() {
                if (!doInBackgroundFinished.get()) {
                    throw new RuntimeException("done called before " +
                                               "doInBackground is finished");
                }
                System.out.println("Done");
                doneLatch.countDown();
            }
        };

        worker.addPropertyChangeListener(
            new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    // Before doInBackground method is invoked,
                    // SwingWorker notifies PropertyChangeListeners about the
                    // property change to StateValue.STARTED
                    if (doInBackgroundFinished.get()
                        && worker.getState() == SwingWorker.StateValue.STARTED) {
                        throw new RuntimeException(
                               "PropertyChangeListeners called with " +
                               "state STARTED after doInBackground is invoked");
                    }

                    // Ensure the STARTED state is notified
                    // before doInBackground started running
                    if (doInBackgroundStarted.get()
                        && worker.getState() == SwingWorker.StateValue.STARTED) {
                        throw new RuntimeException(
                               "PropertyChangeListeners called with " +
                               "state STARTED before doInBackground is finished");
                    }

                    // After the doInBackground method is finished
                    // SwingWorker notifies PropertyChangeListeners
                    // property change to StateValue.DONE
                    if (worker.getState() != SwingWorker.StateValue.DONE
                          && doInBackgroundFinished.get()) {
                        throw new RuntimeException(
                            "PropertyChangeListeners called after " +
                            " doInBackground is finished but before State changed to DONE");
                    }
                }
            });
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
        System.out.println("doInBackground " + doInBackgroundFinished.get() +
                           " getState " + worker.getState());
        if (worker.getState() != SwingWorker.StateValue.DONE
            && doInBackgroundFinished.get()) {
              throw new RuntimeException("doInBackground is finished " +
                                         "but State is not DONE");
        }
    }
}

