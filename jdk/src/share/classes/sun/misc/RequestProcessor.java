/*
 * Copyright 1996 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;

/**
 * The request processor allows functors (Request instances) to be created
 * in arbitrary threads, and to be posted for execution in a non-restricted
 * thread.
 *
 * @author      Steven B. Byrne
 */


public class RequestProcessor implements Runnable {

    private static Queue requestQueue;
    private static Thread dispatcher;

    /**
     * Queues a Request instance for execution by the request procesor
     * thread.
     */
    public static void postRequest(Request req) {
        lazyInitialize();
        requestQueue.enqueue(req);
    }

    /**
     * Process requests as they are queued.
     */
    public void run() {
        lazyInitialize();
        while (true) {
            try {
                Object obj = requestQueue.dequeue();
                if (obj instanceof Request) { // ignore bogons
                    Request req = (Request)obj;
                    try {
                        req.execute();
                    } catch (Throwable t) {
                        // do nothing at the moment...maybe report an error
                        // in the future
                    }
                }
            } catch (InterruptedException e) {
                // do nothing at the present time.
            }
        }
    }


    /**
     * This method initiates the request processor thread.  It is safe
     * to call it after the thread has been started.  It provides a way for
     * clients to deliberately control the context in which the request
     * processor thread is created
     */
    public static synchronized void startProcessing() {
        if (dispatcher == null) {
            dispatcher = new Thread(new RequestProcessor(), "Request Processor");
            dispatcher.setPriority(Thread.NORM_PRIORITY + 2);
            dispatcher.start();
        }
    }


    /**
     * This method performs lazy initialization.
     */
    private static synchronized void lazyInitialize() {
        if (requestQueue == null) {
            requestQueue = new Queue();
        }
    }

}
