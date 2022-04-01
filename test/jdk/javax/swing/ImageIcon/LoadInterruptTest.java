<<<<<<< HEAD
/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8236987
 * @summary  Verifies if Print Function is removed and LoadStatus is changed to ABORTED when interrupted/ still LOADING.
 * @run main LoadInterruptTest
 */

import java.awt.MediaTracker;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class LoadInterruptTest {
    private static ByteArrayOutputStream testOut;
    private static PrintStream prevStatus;
    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    LoadAndSimulate();
                }
            });
        } finally {
            unsetOutput();
        }
    }

    public static void setUpOutput() {
        prevStatus = System.out;
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    public static void unsetOutput()
    {
        System.out.flush();
        if(prevStatus != null)
        {
            System.setOut(prevStatus);
        }
        testOut = null;
    }

    private static void LoadAndSimulate()
    {
        int status;
        setUpOutput();
        Thread.currentThread().interrupt();
        ImageIcon i = new ImageIcon("https://openjdk.java.net/images/openjdk.png");
        status = i.getImageLoadStatus();
        String outString = testOut.toString();


        if (((status & MediaTracker.ABORTED) == 0 ) || (!outString.isEmpty())) {
            throw new RuntimeException("Test Case Failed!!"+", Status : "+status+", Stream-Out :"+outString );
        }
    }
}
=======
/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8236987
 * @summary  Verifies if Print Function is removed and LoadStatus is changed to ABORTED when interrupted/ still LOADING.
 * @run main LoadInterruptTest
 */

import java.awt.MediaTracker;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class LoadInterruptTest {
    private static ByteArrayOutputStream testOut;
    private static PrintStream prevStatus;
    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    LoadAndSimulate();
                }
            });
        } finally {
            unsetOutput();
        }
    }

    public static void setUpOutput() {
        prevStatus = System.out;
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    public static void unsetOutput()
    {
        System.out.flush();
        if(prevStatus != null)
        {
            System.setOut(prevStatus);
        }
        testOut = null;
    }

    private static void LoadAndSimulate()
    {
        int status;
        setUpOutput();
        Thread.currentThread().interrupt();
        ImageIcon i = new ImageIcon("https://openjdk.java.net/images/openjdk.png");
        status = i.getImageLoadStatus();
        String outString = testOut.toString();


        if (((status & MediaTracker.ABORTED) == 0 ) || (!outString.isEmpty())) {
            throw new RuntimeException("Test Case Failed!!"+", Status : "+status+", Stream-Out :"+outString );
        }
    }
}
>>>>>>> 76c2c8169159f9904a83de267f125ea45dea5467
