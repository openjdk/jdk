/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * A simple application used by unit tests. The first argument to this
 * class is the name of a file to which a TCP port number can be written.
 *
 * By default, this class does nothing other than bind to a TCP port,
 * write the TCP port number to a file, and wait for an incoming connection
 * in order to complete the application shutdown protocol.
 */
import java.net.Socket;
import java.net.ServerSocket;
import java.io.File;
import java.io.FileOutputStream;

public class SimpleApplication {
    private static SimpleApplication myApp;      // simple app or a subclass
    private static String            myAppName;  // simple app name
    private static int               myPort;     // coordination port #
    private static ServerSocket      mySS;       // coordination socket

    // protected so a subclass can extend it; not public so creation is
    // limited.
    protected SimpleApplication() {
        // save simple app (or subclass) name for messages
        myAppName = getClass().getName();
    }

    // return the simple application (or a subclass)
    final public static SimpleApplication getMyApp() {
        return myApp;
    }

    // set the simple application (for use by a subclass)
    final public static void setMyApp(SimpleApplication _myApp) {
        myApp = _myApp;
    }

    // execute the application finish protocol
    final public void doMyAppFinish(String[] args) throws Exception {
        System.out.println("INFO: " + myAppName + " is waiting on port: " +
            myPort);
        System.out.flush();

        // wait for test harness to connect
        Socket s = mySS.accept();
        s.close();
        mySS.close();

        System.out.println("INFO: " + myAppName + " is shutting down.");
        System.out.flush();
    }

    // execute the application start protocol
    final public void doMyAppStart(String[] args) throws Exception {
        if (args.length < 1) {
            throw new RuntimeException("Usage: " + myAppName +
                " port-file [arg(s)]");
        }

        // bind to a random port
        mySS = new ServerSocket(0);
        myPort = mySS.getLocalPort();

        // Write the port number to the given file
        File f = new File(args[0]);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write( Integer.toString(myPort).getBytes("UTF-8") );
        fos.close();

        System.out.println("INFO: " + myAppName + " created socket on port: " +
            myPort);
        System.out.flush();
    }

    // execute the app work (subclass can override this)
    public void doMyAppWork(String[] args) throws Exception {
    }

    public static void main(String[] args) throws Exception {
        if (myApp == null) {
            // create myApp since a subclass hasn't done so
            myApp = new SimpleApplication();
        }

        myApp.doMyAppStart(args);   // do the app start protocol

        System.out.println("INFO: " + myAppName + " is calling doMyAppWork()");
        System.out.flush();
        myApp.doMyAppWork(args);    // do the app work
        System.out.println("INFO: " + myAppName + " returned from" +
            " doMyAppWork()");
        System.out.flush();

        myApp.doMyAppFinish(args);  // do the app finish protocol

        System.exit(0);
    }
}
