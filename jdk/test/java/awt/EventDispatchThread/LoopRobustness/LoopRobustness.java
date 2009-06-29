/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 *
 * @bug 4023283
 * @summary Checks that an Error which propogate up to the EventDispatch
 * loop does not crash AWT.
 * @author Andrei Dmitriev: area=awt.event
 * @library ../../regtesthelpers
 * @build Util
 * @run main LoopRobustness
 */

import java.awt.*;
import java.awt.event.*;
import java.lang.Math;
import test.java.awt.regtesthelpers.Util;

public class LoopRobustness {
    static int clicks = 0;
    final static long TIMEOUT = 5000;
    final static Object LOCK = new Object();
    static volatile boolean notifyOccur = false;

    public static void main(String [] args)  {
        ThreadGroup mainThreadGroup = Thread.currentThread().getThreadGroup();

        long at;
        //wait for a TIMEOUT giving a chance to a new Thread above to accomplish its stuff.
        synchronized (LoopRobustness.LOCK){
            new Thread(new TestThreadGroup(mainThreadGroup, "TestGroup"), new Impl()).start();
            at = System.currentTimeMillis();
            try {
                while(!notifyOccur && System.currentTimeMillis() - at < TIMEOUT) {
                    LoopRobustness.LOCK.wait(1000);
                }
            } catch(InterruptedException e){
                throw new RuntimeException("Test interrupted.", e);
            }
        }

        if( !notifyOccur){
            //notify doesn't occur after a reasonable time.
            throw new RuntimeException("Test failed. Second Thread didn't notify MainThread.");
        }

        //now wait for two clicks
        at = System.currentTimeMillis();
        while(System.currentTimeMillis() - at < TIMEOUT && clicks < 2) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) {
                throw new RuntimeException("Test interrupted.", e);
            }
        }
        if (clicks != 2) {
            throw new RuntimeException("robot should press button twice");
        }
    }
}

class Impl implements Runnable{
    static Robot robot;
    public void run() {
        Button b = new Button("Press me to test the AWT-Event Queue thread");
        Frame lr = new Frame("ROBUST FRAME");
        /* Must load Toolkit on this thread only, rather then on Main.
           If load on Main (on the parent ThreadGroup of current ThreadGroup) then
           EDT will be created on Main thread and supplied with it's own exceptionHandler,
           which just throws an Exception and terminates current thread.
           The test implies that EDT is created on the child ThreadGroup (testThreadGroup)
           which is supplied with its own uncaughtException().
        */
        Toolkit.getDefaultToolkit();
        lr.setBounds(100, 100, 300, 100);

        b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    LoopRobustness.clicks++;
                    //throwing an exception in Static Initializer
                    System.out.println(HostileCrasher.aStaticMethod());
                }
            });
        lr.add(b);
        lr.setVisible(true);

        try {
            robot = new Robot();
        } catch(AWTException e){
            throw new RuntimeException("Test interrupted.", e);
        }
        Util.waitForIdle(robot);

        synchronized (LoopRobustness.LOCK){
            LoopRobustness.LOCK.notify();
            LoopRobustness.notifyOccur = true;
        }

        int i = 0;
        while(i < 2){
            robot.mouseMove(b.getLocationOnScreen().x + b.getWidth()/2,
                            b.getLocationOnScreen().y + b.getHeight()/2 );
            robot.mousePress(InputEvent.BUTTON1_MASK);
            //                robot.delay(10);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            i++;
            robot.delay(1000);
        }
    }
}

class TestThreadGroup extends ThreadGroup {
    TestThreadGroup(ThreadGroup threadGroup, String name){
        super(threadGroup, name);
    }

    public void uncaughtException(Thread exitedThread, Throwable e) {
        e.printStackTrace();
        if ((e instanceof ExceptionInInitializerError) || (e instanceof
                NoClassDefFoundError)){
            throw new RuntimeException("Test failed: other Exceptions were thrown ", e);
        }
    }
}

class HostileCrasher {
    static {
        if (Math.random() >= 0.0) {
            throw new RuntimeException("Die, AWT-Event Queue thread!");
        }
    }
    public static String aStaticMethod() {
        return "hello, world";
    }
}
