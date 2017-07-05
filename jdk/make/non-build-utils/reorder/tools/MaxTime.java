/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Run an application (from a jar file) and terminate it after a given
 *  number of seconds.
 */

public class MaxTime {

    private static boolean debugFlag = false;


    private static void debug(String s) {
        if (debugFlag) {
            System.err.println(s);
        }
    }


    private static void usage() {
        System.err.println(
          "Usage:\n" +
          "  java  MaxTime  <jarfile> <timeout>\n" +
          "\n" +
          "  <jarfile> is a jar file specifying an application to run\n" +
          "  <timeout> is an integer number of seconds to allow the app to run." );
        System.exit(1);
    }


    public static void main(String[] args) {
        int timeoutPeriod = 0;
        String mainClass = null;

        if (args.length != 2)
            usage();

        // Identify the timeout value.
        try {
            timeoutPeriod = Integer.parseInt(args[1]);
        } catch (Exception e) {
            usage();
        }

        // Identify the application's main class.
        try {
            mainClass = new JarFile(args[0]).getManifest()
                .getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        } catch (FileNotFoundException e) {
            System.err.println("Can't find " + args[0]);
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }

        // Set the exit timer.
        final int timeout = timeoutPeriod * 1000;
        new Thread() {
            public void run() {
                try {
                    debug("Before timeout!");
                    Thread.sleep(timeout);
                    debug("After timeout!");
                } catch (InterruptedException e) {
                }
                debug("Exit caused by timeout!");
                System.exit(0);
            }
        }.start();


        // Start up the app.
        try {
            ClassLoader cl = new URLClassLoader(
                                   new URL[] {new URL("file:"+args[0])});
            Class clazz = cl.loadClass(mainClass);
            String[] objs = new String[0];
            Method mainMethod = clazz.getMethod("main", new Class[]{objs.getClass()});
            mainMethod.invoke(null, new Object[]{objs});
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
    }
}
