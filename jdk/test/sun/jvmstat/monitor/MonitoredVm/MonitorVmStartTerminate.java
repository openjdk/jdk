/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.*;
import java.util.*;
import java.net.URISyntaxException;
import java.io.IOException;
import sun.jvmstat.monitor.*;
import sun.jvmstat.monitor.event.*;

public class MonitorVmStartTerminate {

    private static final int SLEEPERS = 10;
    private static final int SLEEPTIME = 5000;     // sleep time for a sleeper
    private static final int EXECINTERVAL = 3000;   // wait time between exec's
    private static final int JOINTIME = (SLEEPERS * EXECINTERVAL)
                                        + SLEEPTIME * 2;

    public static void main(String args[]) throws Exception {

        long now = System.currentTimeMillis();

        String sleeperArgs = SLEEPTIME + " " + now;
        String sleeperPattern = "Sleeper " + sleeperArgs + " \\d+$";

        MonitoredHost host = MonitoredHost.getMonitoredHost("localhost");
        host.setInterval(200);

        SleeperListener listener = new SleeperListener(host, sleeperPattern);
        host.addHostListener(listener);

        SleeperStarter ss = new SleeperStarter(SLEEPERS, EXECINTERVAL,
                                               sleeperArgs);
        ss.start();

        System.out.println("Waiting for "
                           + SLEEPERS + " sleepers to terminate");
        try {
            ss.join(JOINTIME);
        } catch (InterruptedException e) {
            System.err.println("Timed out waiting for sleepers");
        }

        if (listener.getStarted() != SLEEPERS) {
            throw new RuntimeException(
                    "Too few sleepers started: "
                    + " started = " + listener.getStarted()
                    + " SLEEPERS = " + SLEEPERS);
        }

        if (listener.getStarted() != listener.getTerminated()) {
            throw new RuntimeException(
                    "Started count != terminated count: "
                    + " started = " + listener.getStarted()
                    + " terminated = " + listener.getTerminated());
        }
    }
}

class SleeperListener implements HostListener {
    private static final boolean DEBUG = false;

    int started;
    int terminated;
    MonitoredHost host;
    Matcher patternMatcher;
    ArrayList targets;

    public SleeperListener(MonitoredHost host, String sleeperPattern) {
        this.host = host;
        Pattern pattern = Pattern.compile(sleeperPattern);
        patternMatcher = pattern.matcher("");
        targets = new ArrayList();
    }

    private void printList(Iterator i, String msg) {
        System.out.println(msg + ":");
        while (i.hasNext()) {
            Integer lvmid = (Integer)i.next();
            try {
                VmIdentifier vmid = new VmIdentifier("//" + lvmid.intValue());
                MonitoredVm target = host.getMonitoredVm(vmid);

                StringMonitor cmdMonitor =
                        (StringMonitor)target.findByName("sun.rt.javaCommand");
                String cmd = cmdMonitor.stringValue();

                System.out.println("\t" + lvmid.intValue() + ": "
                                   + "\"" + cmd + "\"" + ": ");
            } catch (URISyntaxException e) {
                System.err.println("Unexpected URISyntaxException: "
                                   + e.getMessage());
            } catch (MonitorException e) {
                System.out.println("\t" + lvmid.intValue()
                                   + ": error reading monitoring data: "
                                   + " target possibly terminated?");
            }
        }
    }


    private int addStarted(Iterator i) {
        int found = 0;
        while (i.hasNext()) {
            try {
                Integer lvmid = (Integer)i.next();
                VmIdentifier vmid = new VmIdentifier("//" + lvmid.intValue());
                MonitoredVm target = host.getMonitoredVm(vmid);

                StringMonitor cmdMonitor =
                        (StringMonitor)target.findByName("sun.rt.javaCommand");
                String cmd = cmdMonitor.stringValue();

                patternMatcher.reset(cmd);
                System.out.print("Started: " + lvmid.intValue()
                                 + ": " + "\"" + cmd + "\"" + ": ");

                if (patternMatcher.matches()) {
                    System.out.println("matches pattern - recorded");
                    targets.add(lvmid);
                    found++;
                }
                else {
                    System.out.println("does not match pattern - ignored");
                }
            } catch (URISyntaxException e) {
                System.err.println("Unexpected URISyntaxException: "
                                   + e.getMessage());
            } catch (MonitorException e) {
                System.err.println("Unexpected MonitorException: "
                                   + e.getMessage());
            }
        }
        return found;
    }

    private int removeTerminated(Iterator i) {
        int found = 0;
        while (i.hasNext()) {
            Integer lvmid = (Integer)i.next();
            /*
             * we don't attempt to attach to the target here as it's
             * now dead and has no jvmstat share memory file. Just see
             * if the process id is among those that we saved when we
             * started the targets (note - duplicated allowed and somewhat
             * expected on windows);
             */
            System.out.print("Terminated: " + lvmid.intValue() + ": ");
            if (targets.contains(lvmid)) {
                System.out.println("matches pattern - termination recorded");
                targets.remove(lvmid);
                found++;
            }
            else {
                System.out.println("does not match pattern - ignored");
            }
        }
        return found;
    }

    public synchronized int getStarted() {
        return started;
    }

    public synchronized int getTerminated() {
        return terminated;
    }

    public void vmStatusChanged(VmStatusChangeEvent ev) {
        if (DEBUG) {
            printList(ev.getActive().iterator(), "Active");
            printList(ev.getStarted().iterator(), "Started");
            printList(ev.getTerminated().iterator(), "Terminated");
        }

        int recentlyStarted = addStarted(ev.getStarted().iterator());
        int recentlyTerminated = removeTerminated(
                ev.getTerminated().iterator());

        synchronized (this) {
            started += recentlyStarted;
            terminated += recentlyTerminated;
        }
    }

    public void disconnected(HostEvent ev) {
    }
}

class SleeperStarter extends Thread {

    JavaProcess[] processes;
    int execInterval;
    String args;

    public SleeperStarter(int sleepers, int execInterval, String args) {
        this.execInterval = execInterval;
        this.args = args;
        this.processes = new JavaProcess[sleepers];
    }

    private synchronized int active() {
        int active = processes.length;
        for(int i = 0; i < processes.length; i++) {
            try {
                int exitValue = processes[i].exitValue();
                active--;
            } catch (IllegalThreadStateException e) {
                // process hasn't exited yet
            }
        }
        return active;
    }

    public void run() {
       System.out.println("Starting " + processes.length + " sleepers");

       String[] classpath = {
           "-classpath",
           System.getProperty("java.class.path")
       };

       for (int i = 0; i < processes.length; i++) {
           try {
               System.out.println("Starting Sleeper " + i);
               synchronized(this) {
                   processes[i] = new JavaProcess("Sleeper", args + " " + i);
                   processes[i].addOptions(classpath);
               }
               processes[i].start();
               Thread.sleep(execInterval);
           } catch (InterruptedException ignore) {
           } catch (IOException e) {
               System.err.println(
                       "IOException trying to start Sleeper " + i + ": "
                       + e.getMessage());
           }
       }

       // spin waiting for the processes to terminate
       while (active() > 0) ;

       // give final termination event a change to propogate to
       // the HostListener
       try { Thread.sleep(2000); } catch (InterruptedException ignore) { }
    }
}
