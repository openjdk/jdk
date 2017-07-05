/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.net.MalformedURLException;
import java.net.URL;

public class Starter implements Runnable {

    private String id;
    private DelegatingLoader dl;
    private String startClass;

    private static DelegatingLoader saLoader, sbLoader;

    public static void log(String line) {
        System.out.println(line);
    }

    public static void main(String[] args) {
        URL[] urlsa = new URL[1];
        URL[] urlsb = new URL[1];
        try {
            String testDir = System.getProperty("test.classes", ".");
            String sep = System.getProperty("file.separator");
            urlsa[0] = new URL("file://" + testDir + sep + "SA" + sep);
            urlsb[0] = new URL("file://" + testDir + sep + "SB" + sep);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        // Set up Classloader delegation hierarchy
        saLoader = new DelegatingLoader(urlsa);
        sbLoader = new DelegatingLoader(urlsb);

        String[] saClasses = { "comSA.SupBob", "comSA.Alice" };
        String[] sbClasses = { "comSB.SupAlice", "comSB.Bob" };

        saLoader.setDelegate(sbClasses, sbLoader);
        sbLoader.setDelegate(saClasses, saLoader);

        // test one-way delegate
        String testType = args[0];
        if (testType.equals("one-way")) {
            test("comSA.Alice", "comSA.SupBob");
        } else if (testType.equals("cross")) {
            // test cross delegate
            test("comSA.Alice", "comSB.Bob");
        } else {
            System.out.println("ERROR: unsupported - " + testType);
        }
    }

    private static void test(String clsForSA, String clsForSB) {
        Starter ia = new Starter("SA", saLoader, clsForSA);
        Starter ib = new Starter("SB", sbLoader, clsForSB);
        new Thread(ia).start();
        new Thread(ib).start();
    }

    public static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            log("Thread interrupted");
        }
    }

    private Starter(String id, DelegatingLoader dl, String startClass) {
        this.id = id;
        this.dl = dl;
        this.startClass = startClass;
    }

    public void run() {
        log("Spawned thread " + id + " running");
        try {
            // To mirror the WAS deadlock, need to ensure class load
            // is routed via the VM.
            Class.forName(startClass, true, dl);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        log("Thread " + id + " terminating");
    }
}
