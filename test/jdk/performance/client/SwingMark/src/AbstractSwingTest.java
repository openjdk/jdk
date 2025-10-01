/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.awt.ActiveEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.PaintEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
  * Derive from this abstract base class when ever
  * you want to create a new benchmark test which
  * will be run in the SwingMark harness
  *.
  * The SwingMarkPanel class requires that tests subclass AbstractSwingTest.
  *
  * @see SwingMarkPanel
  *
  */

public abstract class AbstractSwingTest {

   public int paintCount = 0;

        /**
          * Override this when subclassing AbstractSwingTest.
          *
          * @return A very short description of the test
          */
        public abstract String getTestName();

        /**
          * Override this when subclassing AbstractSwingTest.
          * Here you create and initialize the component, or
          * group of components, which will be exercised by
          * your test.
          *
          * Typically you will create a JPanel and insert
          * components into the panel.
          *
          * @return The JComponent which will be made visible for your test
          */
        public abstract JComponent getTestComponent();

        /**
          * Override this when subclassing AbstractSwingTest.
          * Here you create the code to automate your test,
          * This code can be written in many ways.  For example, you
          * could directly modify the component, or its' model.
          * You could also post events to the EventQueue.
          * It's up to you
          */
        public abstract void runTest();

   public int getPaintCount() {
      return paintCount;
   }
        /**
          * This static method is used to run your test as a stand-alone
          * application.  Just pass an instance of your test to this function.
          * This is especially useful when you're developing your test, or
          * when you want to concentrate on a single area.
          *
          * To allow your test to be run stand-alone, you need to add a main() that
          * looks like this
          *
          * public static void main(String[] args) {
          *     runStandAloneTest( new MyTestName() );
          * }
          */
        @SuppressWarnings("deprecation")
        public static void runStandAloneTest(AbstractSwingTest test) {
            long startTime = System.currentTimeMillis();
            JFrame f = new JFrame();
            f.addWindowListener( new WindowAdapter(){
                            public void windowClosing(WindowEvent e){
                            System.exit(0);}} );
            f.getContentPane().add( test.getTestComponent() );
            f.pack();
            f.show();
            rest();
            syncRam();
            long endStartup = System.currentTimeMillis();
            test.runTest();
            rest();
            long endTests = System.currentTimeMillis();
            System.out.println("Startup Time: " + (endStartup - startTime));
            System.out.println("Test Time: " + (endTests - endStartup));

            if (test.getPaintCount() > 0) {
               System.out.println("Called Paint: " + test.getPaintCount() + " times");
            } else {
               System.out.println("Painting calls not counted.");
            }

        }

        @SuppressWarnings("removal")
        public static void syncRam() {
            System.gc();
            System.runFinalization();
            System.gc();
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                System.out.println( "failed sleeping " + e);
            }
        }

        private static Component BOGUS_COMPONENT = new JPanel();
        private static EventQueue Q = Toolkit.getDefaultToolkit().getSystemEventQueue();

        // Invoked during tests to wait until the current event has finished
        // processing.  At the time of this writing AWT's EventQueue has 3
        // queues: LOW, NORMAL and HIGH.  Swing's repaint events end up on
        // the NORMAL queue, AWT's paint events end up on the LOW queue.  Runnable
        // events end up on the NORMAL queue.  The code to rest blocks until ALL
        // queues are empty.  This is accomplished by adding an event
        // (NotifyingPaintEvent) to the EventQueue.  When the event is dispatched
        // the EventQueue is checked, if the EQ is empty rest exits, otherwise
        // rest loops through adding another event to the EQ.

        public static void rest() {
            Thread.yield();
            boolean qEmpty = false;
            while (!qEmpty) {
                NotifyingPaintEvent e = new NotifyingPaintEvent(BOGUS_COMPONENT);
                Q.postEvent(e);
                synchronized(e) {
                    // Wait until the event has been dispatched
                    while (!e.isDispatched()) {
                        try {
                            e.wait();
                    } catch (InterruptedException ie) {
                        System.out.println("IE: " + ie);
                    }
                }
                // Check if the q is empty
                qEmpty = e.qEmpty();
            }
        }
        Toolkit.getDefaultToolkit().sync();
    }


    private static class NotifyingPaintEvent extends PaintEvent
                                 implements ActiveEvent {
        private static int nextLocation;

        boolean dispatched = false;
        boolean qEmpty;
        private int location;

        NotifyingPaintEvent(Component x) {
            super(x, PaintEvent.UPDATE, null);
            synchronized(NotifyingPaintEvent.class) {
                location = nextLocation++;
            }
        }

        // 1.3 uses this for coalescing.  To avoid having these events
        // coalesce return a new location for each event.
        public Rectangle getUpdateRect() {
            return new Rectangle(location, location, 1, 1);
        }

        public synchronized boolean isDispatched() {
            return dispatched;
        }

        public synchronized boolean qEmpty() {
            return qEmpty;
        }

        public void dispatch() {
            qEmpty = (Q.peekEvent() == null);
            synchronized(this) {
                dispatched = true;
                notifyAll();
            }
        }
    }
}
