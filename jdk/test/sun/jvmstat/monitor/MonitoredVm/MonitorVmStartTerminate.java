/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CountDownLatch;
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

    public static void main(String args[]) throws Exception {

        long now = System.currentTimeMillis();

        String sleeperArgs = SLEEPTIME + " " + now;
        String sleeperPattern = "Sleeper " + sleeperArgs + " \\d+$";

        MonitoredHost host = MonitoredHost.getMonitoredHost("localhost");
        host.setInterval(200);

        Matcher matcher = Pattern.compile(sleeperPattern).matcher("");
        SleeperListener listener = new SleeperListener(host, matcher, SLEEPERS);
        host.addHostListener(listener);

        SleeperStarter ss = new SleeperStarter(SLEEPERS, EXECINTERVAL,
                                               sleeperArgs);
        ss.start();

        System.out.println("Waiting for "
                           + SLEEPERS + " sleepers to terminate");
        try {
            ss.join();
        } catch (InterruptedException e) {
            throw new Exception("Timed out waiting for sleepers");
        }
        listener.waitForSleepersToStart();
        listener.waitForSleepersToTerminate();
    }

    public static class SleeperListener implements HostListener {

        private final List<Integer> targets =  new ArrayList<>();
        private final CountDownLatch terminateLatch;
        private final CountDownLatch startLatch;
        private final MonitoredHost host;
        private final Matcher patternMatcher;

        public SleeperListener(MonitoredHost host, Matcher matcher, int count) {
            this.host = host;
            this.patternMatcher = matcher;
            this.terminateLatch = new CountDownLatch(count);
            this.startLatch = new CountDownLatch(count);
        }

        public void waitForSleepersToTerminate() throws InterruptedException {
            terminateLatch.await();
        }

        public void waitForSleepersToStart() throws InterruptedException {
            startLatch.await();
        }

        private void printList(Set<Integer> list, String msg) {
            System.out.println(msg + ":");
            for (Integer lvmid : list) {
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


        private int addStarted(Set<Integer> started) {
            int found = 0;
            for (Integer lvmid : started) {
                try {
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

        private int removeTerminated(Set<Integer> terminated) {
            int found = 0;
            for (Integer lvmid : terminated) {
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

        @SuppressWarnings("unchecked")
        public void vmStatusChanged(VmStatusChangeEvent ev) {
            printList(ev.getActive(), "Active");
            printList(ev.getStarted(), "Started");
            printList(ev.getTerminated(), "Terminated");

            int recentlyStarted = addStarted(ev.getStarted());
            int recentlyTerminated = removeTerminated(ev.getTerminated());

            for (int i = 0; i < recentlyTerminated; i++) {
                terminateLatch.countDown();
            }
            for (int i = 0; i < recentlyStarted; i++) {
                startLatch.countDown();
            }
        }

        public void disconnected(HostEvent ev) {
        }
    }

    public static class SleeperStarter extends Thread {

        private final JavaProcess[] processes;
        private final int execInterval;
        private final String args;

        public SleeperStarter(int sleepers, int execInterval, String args) {
            this.execInterval = execInterval;
            this.args = args;
            this.processes = new JavaProcess[sleepers];
        }

        private synchronized int active() {
            int active = processes.length;
            for(JavaProcess jp : processes) {
                try {
                    jp.exitValue();
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
        }
    }
}

