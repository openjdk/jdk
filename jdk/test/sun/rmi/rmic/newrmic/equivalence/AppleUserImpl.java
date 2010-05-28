/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.RemoteException;
import java.rmi.Naming;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.util.Random;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The AppleUserImpl class implements the behavior of the remote
 * "apple user" objects exported by the server.  The application server
 * passes each of its remote "apple" objects to an apple user, and an
 * AppleUserThread is created for each apple.
 */
public class AppleUserImpl
    extends UnicastRemoteObject
    implements AppleUser
{
    private static Logger logger = Logger.getLogger("reliability.appleuser");
    private static int threadNum = 0;
    private static long testDuration = 0;
    private static int maxLevel = 7;
    private static Thread server = null;
    private static Exception status = null;
    private static Random random = new Random();

    public AppleUserImpl() throws RemoteException {
    }

    /**
     * Allows the other server process to indicate that it is ready
     * to start "juicing".
     */
    public synchronized void startTest() throws RemoteException {
        this.notifyAll();
    }

    /**
     * Allows the other server process to report an exception to this
     * process and thereby terminate the test.
     */
    public void reportException(Exception status) throws RemoteException {
        synchronized (AppleUserImpl.class) {
            this.status = status;
            AppleUserImpl.class.notifyAll();
        }
    }

    /**
     * "Use" supplied apple object.  Create an AppleUserThread to
     * stress it out.
     */
    public synchronized void useApple(Apple apple) throws RemoteException {
        String threadName = Thread.currentThread().getName();
        logger.log(Level.FINEST,
            threadName + ": AppleUserImpl.useApple(): BEGIN");

        AppleUserThread t =
            new AppleUserThread("AppleUserThread-" + (++threadNum), apple);
        t.start();

        logger.log(Level.FINEST,
            threadName + ": AppleUserImpl.useApple(): END");
    }

    /**
     * The AppleUserThread class repeatedly invokes calls on its associated
     * Apple object to stress the RMI system.
     */
    class AppleUserThread extends Thread {

        Apple apple;

        public AppleUserThread(String name, Apple apple) {
            super(name);
            this.apple = apple;
        }

        public void run() {
            int orangeNum = 0;
            long stopTime = System.currentTimeMillis() + testDuration;
            Logger logger = Logger.getLogger("reliability.appleuserthread");

            try {
                do { // loop until stopTime is reached

                    /*
                     * Notify apple with some apple events.  This tests
                     * serialization of arrays.
                     */
                    int numEvents = Math.abs(random.nextInt() % 5);
                    AppleEvent[] events = new AppleEvent[numEvents];
                    for (int i = 0; i < events.length; ++ i) {
                        events[i] = new AppleEvent(orangeNum % 3);
                    }
                    apple.notify(events);

                    /*
                     * Request a new orange object be created in
                     * the application server.
                     */
                    Orange orange = apple.newOrange(
                        "Orange(" + getName() + ")-" + (++orangeNum));

                    /*
                     * Create a large message of random ints to pass to orange.
                     */
                    int msgLength = 1000 + Math.abs(random.nextInt() % 3000);
                    int[] message = new int[msgLength];
                    for (int i = 0; i < message.length; ++ i) {
                        message[i] = random.nextInt();
                    }

                    /*
                     * Invoke recursive call on the orange.  Base case
                     * of recursion inverts messgage.
                     */
                    OrangeEchoImpl echo = new OrangeEchoImpl(
                        "OrangeEcho(" + getName() + ")-" + orangeNum);
                    int[] response = orange.recurse(echo, message,
                        2 + Math.abs(random.nextInt() % (maxLevel + 1)));

                    /*
                     * Verify message was properly inverted and not corrupted
                     * through all the recursive method invocations.
                     */
                    if (response.length != message.length) {
                        throw new RuntimeException(
                            "ERROR: CORRUPTED RESPONSE: " +
                            "wrong length of returned array " + "(should be " +
                            message.length + ", is " + response.length + ")");
                    }
                    for (int i = 0; i < message.length; ++ i) {
                        if (~message[i] != response[i]) {
                            throw new RuntimeException(
                                "ERROR: CORRUPTED RESPONSE: " +
                                "at element " + i + "/" + message.length +
                                " of returned array (should be " +
                                Integer.toHexString(~message[i]) + ", is " +
                                Integer.toHexString(response[i]) + ")");
                        }
                    }

                    try {
                        Thread.sleep(Math.abs(random.nextInt() % 10) * 1000);
                    } catch (InterruptedException e) {
                    }

                } while (System.currentTimeMillis() < stopTime);

            } catch (Exception e) {
                status = e;
            }
            synchronized (AppleUserImpl.class) {
                AppleUserImpl.class.notifyAll();
            }
        }
    }

    private static void usage() {
        System.out.println("Usage: AppleUserImpl [-hours <hours> | " +
                                                 "-seconds <seconds>]");
        System.out.println("                     [-maxLevel <maxLevel>]");
        System.out.println("  hours    The number of hours to run the juicer.");
        System.out.println("           The default is 0 hours.");
        System.out.println("  seconds  The number of seconds to run the juicer.");
        System.out.println("           The default is 0 seconds.");
        System.out.println("  maxLevel The maximum number of levels to ");
        System.out.println("           recurse on each call.");
        System.out.println("           The default is 7 levels.");
        //TestLibrary.bomb("Bad argument");
    }

    /**
     * Entry point for the "juicer" server process.  Create and export
     * an apple user implementation in an rmiregistry running on localhost.
     */
    public static void main(String[] args)

    {
        //TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");
        long startTime = 0;
        String durationString = null;

        // parse command line args
        try {
            for (int i = 0; i < args.length ; i++ ) {
                String arg = args[i];
                if (arg.equals("-hours")) {
                    if (durationString != null) {
                        usage();
                    }
                    i++;
                    int hours = Integer.parseInt(args[i]);
                    durationString = hours + " hours";
                    testDuration = hours * 60 * 60 * 1000;
                } else if (arg.equals("-seconds")) {
                    if (durationString != null) {
                        usage();
                    }
                    i++;
                    long seconds = Long.parseLong(args[i]);
                    durationString = seconds + " seconds";
                    testDuration = seconds * 1000;
                } else if (arg.equals("-maxLevel")) {
                    i++;
                    maxLevel = Integer.parseInt(args[i]);
                } else {
                    usage();
                }
            }
            if (durationString == null) {
                durationString = testDuration + " milliseconds";
            }
        } catch (Throwable t) {
            usage();
        }

        AppleUserImpl user = null;
        try {
            user = new AppleUserImpl();
        } catch (RemoteException e) {
            //TestLibrary.bomb("Failed to create AppleUser", e);
        }

        synchronized (user) {
            // create new registry and bind new AppleUserImpl in registry
            try {
                LocateRegistry.createRegistry(1099); //TestLibrary.REGISTRY_PORT);
                Naming.rebind("rmi://localhost:1099/AppleUser",user);
                              //TestLibrary.REGISTRY_PORT + "/AppleUser", user);
            } catch (RemoteException e) {
                //TestLibrary.bomb("Failed to bind AppleUser", e);
            } catch (java.net.MalformedURLException e) {
                //TestLibrary.bomb("Failed to bind AppleUser", e);
            }

            // start the other server if available
            try {
                Class app = Class.forName("ApplicationServer");
                server = new Thread((Runnable) app.newInstance());
                logger.log(Level.INFO, "Starting application server " +
                    "in same process");
                server.start();
            } catch (ClassNotFoundException e) {
                // assume the other server is running in a separate process
                logger.log(Level.INFO, "Application server must be " +
                    "started in separate process");
            } catch (Exception ie) {
                //TestLibrary.bomb("Could not instantiate server", ie);
            }

            // wait for other server to call startTest method
            try {
                logger.log(Level.INFO, "Waiting for application server " +
                    "process to start");
                user.wait();
            } catch (InterruptedException ie) {
                //TestLibrary.bomb("AppleUserImpl interrupted", ie);
            }
        }

        startTime = System.currentTimeMillis();
        logger.log(Level.INFO, "Test starting");

        // wait for exception to be reported or first thread to complete
        try {
            logger.log(Level.INFO, "Waiting " + durationString + " for " +
                "test to complete or exception to be thrown");

            synchronized (AppleUserImpl.class) {
                AppleUserImpl.class.wait();
            }

            if (status != null) {
                //TestLibrary.bomb("juicer server reported an exception", status);
            } else {
                logger.log(Level.INFO, "TEST PASSED");
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "TEST FAILED");
            //TestLibrary.bomb("unexpected exception", e);
        } finally {
            logger.log(Level.INFO, "Test finished");
            long actualDuration = System.currentTimeMillis() - startTime;
            logger.log(Level.INFO, "Test duration was " +
                (actualDuration/1000) + " seconds " +
                "(" + (actualDuration/3600000) + " hours)");
        }
    }
}
