/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6448069
  @summary namefilter is not called for file dialog on windows
  @author oleg.sukhodolsky: area= awt.filedialog
  @run applet FilenameFilterTest.html
*/

/**
 * FilenameFilterTest.java
 *
 * summary: namefilter is not called for file dialog on windows
 */

import java.applet.Applet;
import java.awt.*;

import java.io.File;
import java.io.FilenameFilter;

import test.java.awt.regtesthelpers.Util;

public class FilenameFilterTest extends Applet
{
    //Declare things used in the test, like buttons and labels here
    volatile boolean filter_was_called = false;
    FileDialog fd;

    public void init()
    {
        // Set up the environment -- set the layout manager, add
        // buttons, etc.

        setLayout (new BorderLayout ());

    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera
        setSize (200,200);
        setVisible(true);
        validate();

        EventQueue.invokeLater(new Runnable() {
                public void run() {
                    fd = new FileDialog(new Frame(""), "hello world", FileDialog.LOAD);
                    fd.setFilenameFilter(new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                filter_was_called = true;
                                System.out.println(Thread.currentThread() + " name = " + name );
                                return true;
                            }
                        });
                    fd.setDirectory(System.getProperty("test.src"));
                    fd.setVisible(true);
                }
            });
        Util.waitForIdle(null);
        if (fd == null) {
            throw new RuntimeException("fd is null (very unexpected thing :(");
        }
        //Wait a little; some native dialog implementations may take a while
        //to initialize and call the filter. See 6959787 for an example.
        try {
            Thread.sleep(5000);
        } catch (Exception ex) {
        }
        fd.dispose();
        if (!filter_was_called) {
            throw new RuntimeException("Filter was not called");
        }
    }// start()

}// class FilenameFilterTest
