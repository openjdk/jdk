/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

package heapdump.share;

import java.util.List;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import vm.share.ProcessUtils;

import java.util.LinkedList;

import nsk.share.gc.gp.classload.GeneratedClassProducer;

/**
 * This test eats memory by generating random garbage.
 * <p>
 * This program can eat either heap or metaspace using
 * interned strings depending on parameter metaspace. After this, it
 * can also force JVM to show dump, dump core or execute some command.
 * The following command line switches are supported:
 * <p>
 * "-sleepTime" time to sleep
 * "-signal" show dump after OOM
 * "-metaspace" eat metaspace
 * "-core" dump core after OOM
 * "-exec command" execute command after OOM
 */
public class EatMemory {
    private long sleepTime;
    private boolean signal;
    private boolean metaspace;
    private boolean core;
    private String exec;
    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private long initialFactor = 50;
    private long minChunk = 1000;
    private long factor = 5;
    private long chunk;
    private Runtime runtime = Runtime.getRuntime();
    private int n = 0;
    private final int arrayExtraSize = 12;
    private final int stringLength = 128;
    private byte[] reserved = new byte[(int) (runtime.maxMemory() / 20)];
    private List storage = new ArrayList();
    private List strings = new ArrayList();

    /**
     * @param sleepTime time to sleep
     * @param signal    true if need to force JVM to show dump (Ctrl-Break / Ctrl-/) after OOM
     * @param metaspace true if need to eat metaspace
     * @param core      true if need to force JVM to dump core
     * @param exec      command to execute after OOM
     */
    public EatMemory(long sleepTime, boolean signal, boolean metaspace, boolean core, String exec) {
        this.sleepTime = sleepTime;
        this.signal = signal;
        this.metaspace = metaspace;
        this.core = core;
        this.exec = exec;
    }

    private int getSize(long chunk, long factor) {
        return (int) Math.min(Integer.MAX_VALUE, (chunk - arrayExtraSize) / factor);
    }

    private Object create(long chunk) {
        switch (++n % 8) {
            case 0:
                return new byte[getSize(chunk, 1)];
            case 1:
                return new short[getSize(chunk, 2)];
            case 2:
                return new char[getSize(chunk, 2)];
            case 3:
                return new boolean[getSize(chunk, 1)];
            case 4:
                return new long[getSize(chunk, 8)];
            case 5:
                return new float[getSize(chunk, 4)];
            case 6:
                return new double[getSize(chunk, 8)];
            case 7:
                return new Object[getSize(chunk, 16)];
            default:
                // Should never happen
                return null;
        }
    }


    public void eatHeap() {
        try {
            int[][] arrays = new int[Integer.MAX_VALUE / 2][];
            for (int i = 0; ; ++i) {
                arrays[i] = new int[Integer.MAX_VALUE / 2];
            }
        } catch (OutOfMemoryError x) {
            reserved = null;
        }
    }

    public void eatMetaspace() {
        try {
            System.out.println("Starting eating metaspace...");
            GeneratedClassProducer gp = new GeneratedClassProducer();
            List<Class> lst = new LinkedList<Class>();
            System.out.println("... Oh, so tasty!");
            while (true) {
                lst.add(gp.create(0));
            }
        } catch (OutOfMemoryError e) {
        }
    }

    public void eatMemory() throws IOException {
        if (metaspace)
            eatMetaspace();
        else
            eatHeap();
        reserved = null;
    }

    /**
     * Sleep some time to give system time to process a signal, start
     * process, etc.
     */
    private void sleepSome() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Throws an exception if execution was not successful.
     */
    private void execute() throws IOException, InterruptedException {
        int pid = ProcessUtils.getPid();
        if (pid < 0) {
            throw new RuntimeException("Negative pid " + pid + "; Failed to executed " + exec);
        }
        exec = exec.replaceAll("%p", Integer.toString(pid));
        System.out.println("Executing " + exec);
        Process process = Runtime.getRuntime().exec(exec);
        sleepSome();
        process.waitFor();
        StringBuilder sb = copy(process.getInputStream(), System.out);
        sb.append(copy(process.getErrorStream(), System.out));
        if (process.exitValue() != 0) {
            // trying provide as much informative failure string
            // hoping, it will be the last line in the error stream...


            String failureCause = "Unknown";
            String allTheOutput = sb.toString();
            String[] lines = allTheOutput.split(System.getProperty("line.separator"));

            for (int i = lines.length - 1; i >= 0; i--) {
                // Check that string is not empty
                if (!lines[i].trim().equals("")) {
                    failureCause = lines[i];
                    break;
                }
            }
            throw new RuntimeException(failureCause);
        }
    }

    private StringBuilder copy(InputStream in, OutputStream out) throws IOException {
        byte[] buff = new byte[1000];
        StringBuilder sb = new StringBuilder();
        while (in.available() != 0) {
            n = in.read(buff, 0, buff.length);
            out.write(buff, 0, n);
            sb.append(new String(buff, 0, n));
        }
        return sb;
    }

    public void run() throws Exception {
        eatMemory();
        if (signal) {
            ProcessUtils.sendCtrlBreak();
            sleepSome();

        }
        if (exec != null) {
            execute();
        }
        if (core) {
            /*
             * We try to dump core here.
             * On Unix systems a signal is sent to the process. We need to wait some time
             * to give time to process it. On Windows systems, core dump is not supported
             * and we just do not do anything in this case.
             */
            boolean res = ProcessUtils.dumpCore();
            if (res) {
                sleepSome();
                throw new RuntimeException("Signal sent, but core was not dumped");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long sleepTime = 5000;
        boolean signal = false;
        boolean metaspace = false;
        boolean core = false;
        String exec = null;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equalsIgnoreCase("-sleepTime")) {
                if (++i < args.length) {
                    long time = Long.parseLong(args[i]);
                    if (time != 0)
                        sleepTime = time;
                } else
                    throw new RuntimeException("Argument expected after -sleepTime");
            }
            if (args[i].equalsIgnoreCase("-signal"))
                signal = true;
            if (args[i].equalsIgnoreCase("-metaspace"))
                metaspace = true;
            if (args[i].equalsIgnoreCase("-core"))
                core = true;
            if (args[i].equalsIgnoreCase("-exec")) {
                if (++i < args.length)
                    exec = args[i];
                else
                    throw new RuntimeException("Argument expected after -exec");
            }
        }
        new EatMemory(sleepTime, signal, metaspace, core, exec).run();
    }
}
