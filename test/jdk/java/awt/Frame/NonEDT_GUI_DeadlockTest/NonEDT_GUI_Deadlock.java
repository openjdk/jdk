/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug 4828019
  @summary Frame/Window deadlock
  @author yan@sparc.spb.su: area=
  @run applet NonEDT_GUI_Deadlock.html
*/

// Note there is no @ in front of test above.  This is so that the
//  harness will not mistake this file as a test file.  It should
//  only see the html file as a test file. (the harness runs all
//  valid test files, so it would run this test twice if this file
//  were valid as well as the html file.)
// Also, note the area= after Your Name in the author tag.  Here, you
//  should put which functional area the test falls in.  See the
//  AWT-core home page -> test areas and/or -> AWT team  for a list of
//  areas.
// Note also the 'AutomaticAppletTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * NonEDT_GUI_Deadlock.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;


//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class NonEDT_GUI_Deadlock extends Applet
{
    //Declare things used in the test, like buttons and labels here
    boolean bOK = false;
    Thread badThread = null;

    public void init()
    {
    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera

        setSize (200,300);
        setVisible(true);
        validate();

        final Frame theFrame = new Frame("Window test");
        theFrame.setSize(240, 200);

        Thread thKiller = new Thread() {
           public void run() {
              try {
                 Thread.sleep( 9000 );
              }catch( Exception ex ) {
              }
              if( !bOK ) {
                 // oops,
                 //System.out.println("Deadlock!");
                 Runtime.getRuntime().halt(0);
              }else{
                 //System.out.println("Passed ok.");
              }
           }
        };
        thKiller.setName("Killer thread");
        thKiller.start();
        Window w = new TestWindow(theFrame);
        theFrame.toBack();
        theFrame.setVisible(true);

        theFrame.setLayout(new FlowLayout(FlowLayout.CENTER));
        EventQueue.invokeLater(new Runnable() {
           public void run() {
               bOK = true;
           }
        });



    }// start()
    class TestWindow extends Window implements Runnable {

        TestWindow(Frame f) {
            super(f);

            //setSize(240, 75);
            setLocation(0, 75);

            show();
            toFront();

            badThread = new Thread(this);
            badThread.setName("Bad Thread");
            badThread.start();

        }

        public void paint(Graphics g) {
            g.drawString("Deadlock or no deadlock?",20,80);
        }

        public void run() {

            long ts = System.currentTimeMillis();

            while (true) {
                if ((System.currentTimeMillis()-ts)>3000) {
                    this.setVisible( false );
                    dispose();
                    break;
                }

                toFront();
                try {
                    Thread.sleep(80);
                } catch (Exception e) {
                }
            }
        }
    }



    public static void main(String args[]) {
       NonEDT_GUI_Deadlock imt = new NonEDT_GUI_Deadlock();
       imt.init();
       imt.start();
    }


}// class NonEDT_GUI_Deadlock
