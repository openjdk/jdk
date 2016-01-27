/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package gc.g1.plab.lib;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LogParser class parses VM output to get PLAB and ConsumptionStats values.
 *
 * Typical GC log with PLAB statistics (options - -Xlog:gc=debug,gc+plab=debug) looks like:
 *
 * [2,244s][info   ][gc     ] GC(30) Concurrent Mark abort
 * [2,245s][debug  ][gc,plab] GC(33)  (allocated = 1 wasted = 0 unused = 0 used = 1 undo_waste = 0 region_end_waste = 0 regions filled = 0 direct_allocated = 0 failure_used = 0 failure_waste = 0)  (plab_sz = 0 desired_plab_sz = 258)
 * [2,245s][debug  ][gc,plab] GC(33)  (allocated = 1 wasted = 0 unused = 0 used = 1 undo_waste = 0 region_end_waste = 0 regions filled = 0 direct_allocated = 0 failure_used = 0 failure_waste = 0)  (plab_sz = 0 desired_plab_sz = 258)
 * [2,245s][info   ][gc     ] GC(33) Pause Young (G1 Evacuation Pause) 127M->127M(128M) (2,244s, 2,245s) 0,899ms
 * [2,246s][debug  ][gc,plab] GC(34)  (allocated = 1 wasted = 0 unused = 0 used = 1 undo_waste = 0 region_end_waste = 0 regions filled = 0 direct_allocated = 0 failure_used = 0 failure_waste = 0)  (plab_sz = 0 desired_plab_sz = 258)
 * [2,246s][debug  ][gc,plab] GC(34)  (allocated = 1 wasted = 0 unused = 0 used = 1 undo_waste = 0 region_end_waste = 0 regions filled = 0 direct_allocated = 0 failure_used = 0 failure_waste = 0)  (plab_sz = 0 desired_plab_sz = 258)
 * [2,246s][info   ][gc     ] GC(34) Pause Initial Mark (G1 Evacuation Pause) 127M->127M(128M) (2,245s, 2,246s) 0,907ms

 */
final public class LogParser {

    // Name for GC ID field in report.
    public final static String GC_ID = "gc_id";

    /**
     * Type of parsed log element.
     */
    public static enum ReportType {

        SURVIVOR_STATS,
        OLD_STATS
    }

    private final String log;

    private final Map<Long, Map<ReportType, Map<String,Long>>> reportHolder;

    // GC ID
    private static final Pattern GC_ID_PATTERN = Pattern.compile("\\[gc,plab\\s*\\] GC\\((\\d+)\\)");
    // Pattern for extraction pair <name>=<numeric value>
    private static final Pattern PAIRS_PATTERN = Pattern.compile("\\w+\\s+=\\s+\\d+");

    /**
     * Construct LogParser Object
     *
     * @param log - VM Output
     */
    public LogParser(String log) {
        if (log == null) {
            throw new IllegalArgumentException("Parameter log should not be null.");
        }
        this.log = log;
        reportHolder = parseLines();
    }

    /**
     * @return log which is being processed
     */
    public String getLog() {
        return log;
    }

    /**
     * Returns list of log entries.
     *
     * @return list of Pair with ReportType and Map of parameters/values.
     */
    public Map<Long,Map<ReportType, Map<String,Long>>> getEntries() {
        return reportHolder;
    }

    private Map<Long,Map<ReportType, Map<String,Long>>> parseLines() throws NumberFormatException {
        Scanner lineScanner = new Scanner(log);
        Map<Long,Map<ReportType, Map<String,Long>>> allocationStatistics = new HashMap<>();
        Optional<Long> gc_id;
        while (lineScanner.hasNextLine()) {
            String line = lineScanner.nextLine();
            gc_id = getGcId(line);
            if ( gc_id.isPresent() ) {
                Matcher matcher = PAIRS_PATTERN.matcher(line);
                if (matcher.find()) {
                    Map<ReportType,Map<String, Long>> oneReportItem;
                    ReportType reportType;
                    // Second line in log is statistics for Old PLAB allocation
                    if ( !allocationStatistics.containsKey(gc_id.get()) ) {
                        oneReportItem = new EnumMap<>(ReportType.class);
                        reportType = ReportType.SURVIVOR_STATS;
                        allocationStatistics.put(gc_id.get(), oneReportItem);
                    } else {
                        oneReportItem = allocationStatistics.get(gc_id.get());
                        reportType = ReportType.OLD_STATS;
                    }

                    // Extract all pairs from log.
                    HashMap<String, Long> plabStats = new HashMap<>();
                    do {
                        String pair = matcher.group();
                        String[] nameValue = pair.replaceAll(" ", "").split("=");
                        plabStats.put(nameValue[0], Long.parseLong(nameValue[1]));
                    } while (matcher.find());
                    oneReportItem.put(reportType,plabStats);
                }
            }
        }
        return allocationStatistics;
    }

    private Optional<Long> getGcId(String line) {
        Matcher number = GC_ID_PATTERN.matcher(line);
        if (number.find()) {
            return Optional.of(Long.parseLong(number.group(1)));
        }
        return Optional.empty();
    }
}
