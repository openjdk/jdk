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

/* @test
 * @bug 4820217
 * @summary Ensure that pending reads on stdout and stderr streams
 *          return -1 when the process is destroyed
 */

import java.io.*;


public class StreamsSurviveDestroy {

    private static class Copier extends Thread {

        String name;
        InputStream in;
        OutputStream out;
        boolean wantInterrupt;
        boolean acceptException;
        Exception exc = null;

        Copier(String name, InputStream in, OutputStream out,
               boolean ae, boolean wi)
        {
            this.name = name;
            this.in = in;
            this.out = out;
            this.acceptException = ae;
            this.wantInterrupt = wi;
            setName(name);
            start();
        }

        private void log(String s) {
            System.err.println("  " + name + ": " + s);
        }

        public void run() {
            byte[] buf = new byte[4242];
            for (;;) {
                try {
                    int n = in.read(buf);
                    if (n < 0) {
                        System.err.println("  EOF");
                        break;
                    }
                    out.write(buf, 0, n);
                } catch (IOException x) {
                    if (wantInterrupt) {
                        if (x instanceof InterruptedIOException) {
                            log("Interrupted as expected");
                            return;
                        }
                        exc = new Exception(name
                                            + ": Not interrupted as expected");
                        return;
                    }
                    exc = x;
                    if (acceptException) {
                        log("Thrown, but okay: " + x);
                        return;
                    }
                    return;
                }
            }
        }

        public void check() throws Exception {
            if (!acceptException && exc != null)
                throw new Exception(name + ": Exception thrown", exc);
        }

    }

    static void test() throws Exception {
        System.err.println("test");
        Process p = Runtime.getRuntime().exec("/bin/cat");
        Copier cp1 = new Copier("out", p.getInputStream(), System.err,
                               false, false);
        Copier cp2 = new Copier("err", p.getErrorStream(), System.err,
                               false, false);
        Thread.sleep(100);
        p.destroy();
        System.err.println("  exit: " + p.waitFor());
        cp1.join();
        cp1.check();
        cp2.join();
        cp2.check();
    }

    static void testCloseBeforeDestroy() throws Exception {
        System.err.println("testCloseBeforeDestroy");
        Process p = Runtime.getRuntime().exec("/bin/cat");
        Copier cp1 = new Copier("out", p.getInputStream(), System.err,
                                true, false);
        Copier cp2 = new Copier("err", p.getErrorStream(), System.err,
                                true, false);
        Thread.sleep(100);
        p.getInputStream().close();
        p.getErrorStream().close();
        p.destroy();
        System.err.println("  exit: " + p.waitFor());
        cp1.join();
        cp1.check();
        cp2.join();
        cp2.check();
    }

    static void testCloseAfterDestroy() throws Exception {
        System.err.println("testCloseAfterDestroy");
        Process p = Runtime.getRuntime().exec("/bin/cat");
        Copier cp1 = new Copier("out", p.getInputStream(), System.err,
                                true, false);
        Copier cp2 = new Copier("err", p.getErrorStream(), System.err,
                                true, false);
        Thread.sleep(100);
        p.destroy();
        p.getInputStream().close();
        p.getErrorStream().close();
        System.err.println("  exit: " + p.waitFor());
        cp1.join();
        cp1.check();
        cp2.join();
        cp2.check();
    }

    static void testInterrupt() throws Exception {
        System.err.println("testInterrupt");
        Process p = Runtime.getRuntime().exec("/bin/cat");
        Copier cp1 = new Copier("out", p.getInputStream(), System.err,
                                false, true);
        Copier cp2 = new Copier("err", p.getErrorStream(), System.err,
                                false, true);
        Thread.sleep(100);
        cp1.interrupt();
        cp2.interrupt();
        Thread.sleep(100);
        p.destroy();
        System.err.println("  exit: " + p.waitFor());
        cp1.join();
        cp1.check();
        cp2.join();
        cp2.check();
    }

    public static void main(String[] args) throws Exception {

        // Applies only to Solaris; Linux and Windows
        // behave a little differently
        if (!System.getProperty("os.name").equals("SunOS"))
            return;

        test();
        testCloseBeforeDestroy();
        testCloseAfterDestroy();
        testInterrupt();

    }

}
