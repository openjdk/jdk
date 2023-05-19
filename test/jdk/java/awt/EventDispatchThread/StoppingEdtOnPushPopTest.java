/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4848555
  @summary Popping an event queue could cause its thread to restart inadvertently
  @run main StoppingEdtOnPushPopTest
*/

import java.awt.AWTEvent;
import java.awt.ActiveEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;

public class StoppingEdtOnPushPopTest implements Runnable {
    public void start() {
        int before = countEventQueues();
        try {
            for (int i = 0; i < 10; i++) {
                EventQueue.invokeAndWait(this);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("Test was interrupted");
        } catch (java.lang.reflect.InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("InvocationTargetException occurred");
        }
        pause(1000);
        int after = countEventQueues();
        if (before < after && after > 1) {
            throw new RuntimeException("Test failed (before=" + before
                    + "; after=" + after + ")");
        }
        System.out.println("Test passed");
    }

    public void run() {
        System.out.println("push/pop");
        MyEventQueue queue = new MyEventQueue();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue);
        Toolkit.getDefaultToolkit().getSystemEventQueue()
                .postEvent(new EmptyEvent());
        queue.pop();
    }

    public int countEventQueues() {
        int count = 0;
        System.out.println("All threads currently running in the system");
        Thread threads[] = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        for (int i = 0; i < threads.length; ++i) {
            Thread thread = threads[i];
            if (thread != null) {
                System.out.println(thread.getName());
                if (thread.getName().startsWith("AWT-EventQueue")) {
                    count++;
                }
            }
        }
        return count;
    }

    public void pause(long aMillis) {
        try {
            Thread.sleep(aMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("Test was interrupted");
        }
    }

    public static void main(String[] args) {
        StoppingEdtOnPushPopTest test = new StoppingEdtOnPushPopTest();
        test.start();
    }
}

class MyEventQueue extends EventQueue {
    public MyEventQueue() {
        super();
    }

    public void pop() {
        super.pop();
    }
}

class EmptyEvent extends AWTEvent implements ActiveEvent {
    public EmptyEvent() {
        super(new Object(), 0);
    }

    public void dispatch() {
        System.out.println("one more EmptyEvent");
    }
}
