/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4185854
  @summary Checks that constructors do not accept nulls and throw NPE
  @run applet ConstructorsNullTest.html
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
// Note also the 'ConstructorsNullTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * ConstructorsNullTest.java
 *
 * summary:
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.image.*;
import java.awt.color.*;


//Automated tests should run as applet tests if possible because they
// get their environments cleaned up, including AWT threads, any
// test created threads, and any system resources used by the test
// such as file descriptors.  (This is normally not a problem as
// main tests usually run in a separate VM, however on some platforms
// such as the Mac, separate VMs are not possible and non-applet
// tests will cause problems).  Also, you don't have to worry about
// synchronisation stuff in Applet tests they way you do in main
// tests...


public class ConstructorsNullTest extends Applet
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
      //Get things going.  Request focus, set size, et cetera
      setSize (200,200);
      show();

      ColorConvertOp gp;
      boolean passed = false;
      try {
          gp = new ColorConvertOp((ColorSpace)null, (RenderingHints)null);
      } catch (NullPointerException e) {
          try {
              gp = new ColorConvertOp((ColorSpace)null, null, null);
          } catch (NullPointerException e1) {
              try {
                  gp = new ColorConvertOp((ICC_Profile[])null, null);
              } catch (NullPointerException e2) {
                  passed = true;
              }
          }
      }

      if (!passed) {
          System.out.println("Test FAILED: one of constructors didn't throw NullPointerException.");
          throw new RuntimeException("Test FAILED: one of constructors didn't throw NullPointerException.");
      }
      System.out.println("Test PASSED: all constructors threw NullPointerException.");

      //What would normally go into main() will probably go here.
      //Use System.out.println for diagnostic messages that you want
      //to read after the test is done.
      //Use System.out.println for messages you want the tester to read.

    }// start()

 }// class ConstructorsNullTest
