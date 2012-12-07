/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * The main command line driver of a parser for LogCompilation output.
 * @author never
 */

package com.sun.hotspot.tools.compiler;

import java.io.PrintStream;
import java.util.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

public class LogCompilation extends DefaultHandler implements ErrorHandler, Constants {

    public static void usage(int exitcode) {
        System.out.println("Usage: LogCompilation [ -v ] [ -c ] [ -s ] [ -e | -n ] file1 ...");
        System.out.println("  -c:   clean up malformed 1.5 xml");
        System.out.println("  -i:   print inlining decisions");
        System.out.println("  -S:   print compilation statistics");
        System.out.println("  -s:   sort events by start time");
        System.out.println("  -e:   sort events by elapsed time");
        System.out.println("  -n:   sort events by name and start");
        System.exit(exitcode);
    }

    public static void main(String[] args) throws Exception {
        Comparator<LogEvent> defaultSort = LogParser.sortByStart;
        boolean statistics = false;
        boolean printInlining = false;
        boolean cleanup = false;
        int index = 0;

        while (args.length > index) {
            if (args[index].equals("-e")) {
                defaultSort = LogParser.sortByElapsed;
                index++;
            } else if (args[index].equals("-n")) {
                defaultSort = LogParser.sortByNameAndStart;
                index++;
            } else if (args[index].equals("-s")) {
                defaultSort = LogParser.sortByStart;
                index++;
            } else if (args[index].equals("-c")) {
                cleanup = true;
                index++;
            } else if (args[index].equals("-S")) {
                statistics = true;
                index++;
            } else if (args[index].equals("-h")) {
                usage(0);
            } else if (args[index].equals("-i")) {
                printInlining = true;
                index++;
            } else {
                break;
            }
        }

        if (index >= args.length) {
            usage(1);
        }

        while (index < args.length) {
            ArrayList<LogEvent> events = LogParser.parse(args[index], cleanup);

            if (statistics) {
                printStatistics(events, System.out);
            } else {
                Collections.sort(events, defaultSort);
                for (LogEvent c : events) {
                    if (printInlining && c instanceof Compilation) {
                        Compilation comp = (Compilation)c;
                        comp.print(System.out, true);
                    } else {
                        c.print(System.out);
                    }
                }
            }
            index++;
        }
    }

    public static void printStatistics(ArrayList<LogEvent> events, PrintStream out) {
        long cacheSize = 0;
        long maxCacheSize = 0;
        int nmethodsCreated = 0;
        int nmethodsLive = 0;
        int[] attempts = new int[32];
        double regallocTime = 0;
        int maxattempts = 0;

        LinkedHashMap<String, Double> phaseTime = new LinkedHashMap<String, Double>(7);
        LinkedHashMap<String, Integer> phaseNodes = new LinkedHashMap<String, Integer>(7);
        double elapsed = 0;

        for (LogEvent e : events) {
            if (e instanceof Compilation) {
                Compilation c = (Compilation) e;
                c.printShort(out);
                out.printf(" %6.4f\n", c.getElapsedTime());
                attempts[c.getAttempts()]++;
                maxattempts = Math.max(maxattempts,c.getAttempts());
                elapsed += c.getElapsedTime();
                for (Phase phase : c.getPhases()) {
                    Double v = phaseTime.get(phase.getName());
                    if (v == null) {
                        v = Double.valueOf(0.0);
                    }
                    phaseTime.put(phase.getName(), Double.valueOf(v.doubleValue() + phase.getElapsedTime()));

                    Integer v2 = phaseNodes.get(phase.getName());
                    if (v2 == null) {
                        v2 = Integer.valueOf(0);
                    }
                    phaseNodes.put(phase.getName(), Integer.valueOf(v2.intValue() + phase.getNodes()));
                    /* Print phase name, elapsed time, nodes at the start of the phase,
                       nodes created in the phase, live nodes at the start of the phase,
                       live nodes added in the phase.
                    */
                    out.printf("\t%s %6.4f %d %d %d %d\n", phase.getName(), phase.getElapsedTime(), phase.getStartNodes(), phase.getNodes(), phase.getStartLiveNodes(), phase.getLiveNodes());
                }
            } else if (e instanceof MakeNotEntrantEvent) {
                MakeNotEntrantEvent mne = (MakeNotEntrantEvent) e;
                NMethod nm = mne.getNMethod();
                if (mne.isZombie()) {
                    if (nm == null) {
                        System.err.println(mne.getId());
                    }
                    cacheSize -= nm.getSize();
                    nmethodsLive--;
                }
            } else if (e instanceof NMethod) {
                nmethodsLive++;
                nmethodsCreated++;
                NMethod nm = (NMethod) e;
                cacheSize += nm.getSize();
                maxCacheSize = Math.max(cacheSize, maxCacheSize);
            }
        }
        out.printf("NMethods: %d created %d live %d bytes (%d peak) in the code cache\n",
                          nmethodsCreated, nmethodsLive, cacheSize, maxCacheSize);
        out.println("Phase times:");
        for (String name : phaseTime.keySet()) {
            Double v = phaseTime.get(name);
            Integer v2 = phaseNodes.get(name);
            out.printf("%20s %6.4f %d\n", name, v.doubleValue(), v2.intValue());
        }
        out.printf("%20s %6.4f\n", "total", elapsed);

        if (maxattempts > 0) {
            out.println("Distribution of regalloc passes:");
            for (int i = 0; i <= maxattempts; i++) {
                out.printf("%2d %8d\n", i, attempts[i]);
            }
        }
    }
}
