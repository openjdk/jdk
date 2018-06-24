/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4051487 4145670 8062021
  @summary Tests that disposing of an empty Frame or a Frame with a MenuBar
           while it is being created does not crash the VM.
  @author dpm area=Threads
  @run applet/timeout=7200 DisposeStressTest.html
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
// Note also the 'DisposeStressTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * DisposeStressTest.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;


//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class DisposeStressTest extends Applet
 {
   //Declare things used in the test, like buttons and labels here

   public void init()
    {
      //Create instructions for the user here, as well as set up
      // the environment -- set the layout manager, add buttons,
      // etc.

      this.setLayout (new BorderLayout ());


    }//End  init()

   public void start ()
    {
        for (int i = 0; i < 1000; i++) {
            Frame f = new Frame();
            f.setBounds(10, 10, 10, 10);
            f.show();
            f.dispose();

            Frame f2 = new Frame();
            f2.setBounds(10, 10, 100, 100);
            MenuBar bar = new MenuBar();
            Menu menu = new Menu();
            menu.add(new MenuItem("foo"));
            bar.add(menu);
            f2.setMenuBar(bar);
            f2.show();
            f2.dispose();
        }
    }// start()

 }// class DisposeStressTest
