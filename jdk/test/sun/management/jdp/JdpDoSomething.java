/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;

import sun.management.jdp.JdpJmxPacket;
import sun.management.jdp.JdpException;

public class JdpDoSomething {

    private static final String lockFileName = "JdpDoSomething.lck";
    private static final boolean verbose = false;

    public static boolean getVerbose(){
        return verbose;
    }

    public static void printJdpPacket(JdpJmxPacket p) {
        if (getVerbose()) {
            try {
                RandomAccessFile f = new RandomAccessFile("out.dmp", "rw");
                f.write(p.getPacketData());
                f.close();
            } catch (IOException e) {
                System.out.println("Can't write a dump file: " + e);
            }

            System.out.println("Id: " + p.getId());
            System.out.println("Jmx: " + p.getJmxServiceUrl());
            System.out.println("Main: " + p.getMainClass());
            System.out.println("InstanceName: " + p.getInstanceName());
            System.out.println("ProccessId: " + p.getProcessId());
            System.out.println("BroadcastInterval: " + p.getBroadcastInterval());
            System.out.println("Rmi Hostname: " + p.getRmiHostname());

            System.out.flush();
        }
    }

    public static void compaireJdpPacketEx(JdpJmxPacket p1, JdpJmxPacket p2)
    throws JdpException {

        if (!Objects.equals(p1, p1)) {
            throw new JdpException("Packet mismatch error");
        }

        if (!Objects.equals(p1.getMainClass(), p2.getMainClass())) {
            throw new JdpException("Packet mismatch error (main class)");
        }

        if (!Objects.equals(p1.getInstanceName(), p2.getInstanceName())) {
            throw new JdpException("Packet mismatch error (instance name)");
        }
    }

    public static void doSomething() {
        try {
            File lockFile = new File(lockFileName);
            lockFile.createNewFile();

            while (lockFile.exists()) {
                long datetime = lockFile.lastModified();
                long epoch = System.currentTimeMillis() / 1000;

                // Don't allow test app to run more than an hour
                if (epoch - datetime > 3600) {
                    System.err.println("Lock is too old. Aborting");
                    return;
                }
                Thread.sleep(1);
            }

        } catch (Throwable e) {
            System.err.println("Something bad happens:" + e);
        }
    }

    public static void main(String args[]) throws Exception {
        System.err.println("main enter");
        doSomething();
        System.err.println("main exit");
    }
}
