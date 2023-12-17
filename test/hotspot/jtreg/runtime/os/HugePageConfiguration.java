/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc.
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

import jdk.test.lib.process.OutputAnalyzer;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This class allows us to parse system hugepage config from
// - a) the Operating System (the truth)
// - b) the JVM log (-Xlog:pagesize)
// This is used e.g. in TestHugePageDetection to determine if the JVM detects the correct settings from the OS.
class HugePageConfiguration {

    public static class StaticHugePageConfig implements Comparable<StaticHugePageConfig> {
        public long pageSize = -1;
        public long nr_hugepages = -1;
        public long nr_overcommit_hugepages = -1;

        @Override
        public int hashCode() {
            return Objects.hash(pageSize);
        }

        @Override
        public String toString() {
            return "StaticHugePageConfig{" +
                    "pageSize=" + pageSize +
                    ", nr_hugepages=" + nr_hugepages +
                    ", nr_overcommit_hugepages=" + nr_overcommit_hugepages +
                    '}';
        }

        @Override
        public int compareTo(StaticHugePageConfig o) {
            return (int) (pageSize - o.pageSize);
        }
    }

    Set<StaticHugePageConfig> _staticHugePageConfigurations;
    long _staticDefaultHugePageSize = -1;

    enum THPMode {always, never, madvise}
    THPMode _thpMode;
    long _thpPageSize;

    enum ShmemTHPMode {always, within_size, advise, never, deny, force, unknown}
    ShmemTHPMode _shmemThpMode;

    public Set<StaticHugePageConfig> getStaticHugePageConfigurations() {
        return _staticHugePageConfigurations;
    }

    public long getStaticDefaultHugePageSize() {
        return _staticDefaultHugePageSize;
    }

    public THPMode getThpMode() {
        return _thpMode;
    }

    public long getThpPageSize() {
        return _thpPageSize;
    }

    // Returns true if the THP support is enabled
    public boolean supportsTHP() {
        return _thpMode == THPMode.always || _thpMode == THPMode.madvise;
    }

    public ShmemTHPMode getShmemThpMode() {
        return _shmemThpMode;
    }

    // Returns true if static huge pages are supported (whether or not we have configured the pools)
    public boolean supportsStaticHugePages() {
        return _staticDefaultHugePageSize > 0 && _staticHugePageConfigurations.size() > 0;
    }

    public HugePageConfiguration(Set<StaticHugePageConfig> _staticHugePageConfigurations, long _staticDefaultHugePageSize, THPMode _thpMode, long _thpPageSize, ShmemTHPMode _shmemThpMode) {
        this._staticHugePageConfigurations = _staticHugePageConfigurations;
        this._staticDefaultHugePageSize = _staticDefaultHugePageSize;
        this._thpMode = _thpMode;
        this._thpPageSize = _thpPageSize;
        this._shmemThpMode = _shmemThpMode;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "_staticHugePageConfigurations=" + _staticHugePageConfigurations +
                ", _staticDefaultHugePageSize=" + _staticDefaultHugePageSize +
                ", _thpMode=" + _thpMode +
                ", _thpPageSize=" + _thpPageSize +
                ", _shmemThpMode=" + _shmemThpMode +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HugePageConfiguration that = (HugePageConfiguration) o;
        return _staticDefaultHugePageSize == that._staticDefaultHugePageSize && _thpPageSize == that._thpPageSize &&
                Objects.equals(_staticHugePageConfigurations, that._staticHugePageConfigurations) && _thpMode == that._thpMode &&
                _shmemThpMode == that._shmemThpMode;
    }

    private static long readDefaultHugePageSizeFromOS() {
        Pattern pat = Pattern.compile("Hugepagesize: *(\\d+) +kB");
        try (Scanner scanner = new Scanner(new File("/proc/meminfo"))) {
            while (scanner.hasNextLine()) {
                Matcher mat = pat.matcher(scanner.nextLine());
                if (mat.matches()) {
                    scanner.close();
                    return Long.parseLong(mat.group(1)) * 1024;
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not open /proc/meminfo");
        }
        return 0;
    }

    private static Set<StaticHugePageConfig> readSupportedHugePagesFromOS() throws IOException {
        TreeSet<StaticHugePageConfig> hugePageConfigs = new TreeSet<>();
        Pattern pat = Pattern.compile("hugepages-(\\d+)kB");
        File[] subdirs = new File("/sys/kernel/mm/hugepages").listFiles();
        if (subdirs != null) {
            for (File subdir : subdirs) {
                String name = subdir.getName();
                Matcher mat = pat.matcher(name);
                if (mat.matches()) {
                    StaticHugePageConfig config = new StaticHugePageConfig();
                    config.pageSize = Long.parseLong(mat.group(1)) * 1024;
                    try (FileReader fr = new FileReader(subdir.getAbsolutePath() + "/nr_hugepages");
                         BufferedReader reader = new BufferedReader(fr)) {
                        String s = reader.readLine();
                        config.nr_hugepages = Long.parseLong(s);
                    }
                    try (FileReader fr = new FileReader(subdir.getAbsolutePath() + "/nr_overcommit_hugepages");
                         BufferedReader reader = new BufferedReader(fr)) {
                        String s = reader.readLine();
                        config.nr_overcommit_hugepages = Long.parseLong(s);
                    }
                    hugePageConfigs.add(config);
                }
            }
        }
        return hugePageConfigs;
    }

    private static THPMode readTHPModeFromOS() {
        THPMode mode = THPMode.never;
        String file = "/sys/kernel/mm/transparent_hugepage/enabled";
        try (FileReader fr = new FileReader(file);
             BufferedReader reader = new BufferedReader(fr)) {
            String s = reader.readLine();
            if (s.contains("[never]")) {
                mode = THPMode.never;
            } else if (s.contains("[always]")) {
                mode = THPMode.always;
            } else if (s.contains("[madvise]")) {
                mode = THPMode.madvise;
            } else {
                throw new RuntimeException("Unexpected content of " + file + ": " + s);
            }
        } catch (IOException e) {
            System.out.println("Failed to read " + file);
            // Happens when the kernel is not built to support THPs.
            mode = THPMode.never;
        }
        return mode;
    }

    private static long readTHPPageSizeFromOS() {
        long pagesize = 0;
        String file = "/sys/kernel/mm/transparent_hugepage/hpage_pmd_size";
        try (FileReader fr = new FileReader(file);
             BufferedReader reader = new BufferedReader(fr)) {
            String s = reader.readLine();
            pagesize = Long.parseLong(s);
        } catch (IOException | NumberFormatException e) { } // ignored
        return pagesize;
    }

    private static ShmemTHPMode readShmemTHPModeFromOS() {
        ShmemTHPMode mode = ShmemTHPMode.unknown;
        String file = "/sys/kernel/mm/transparent_hugepage/shmem_enabled";
        try (FileReader fr = new FileReader(file);
             BufferedReader reader = new BufferedReader(fr)) {
            String s = reader.readLine();
            if (s.contains("[always]")) {
                mode = ShmemTHPMode.always;
            } else if (s.contains("[within_size]")) {
                mode = ShmemTHPMode.within_size;
            } else if (s.contains("[advise]")) {
                mode = ShmemTHPMode.advise;
            } else if (s.contains("[never]")) {
                mode = ShmemTHPMode.never;
            } else if (s.contains("[deny]")) {
                mode = ShmemTHPMode.deny;
            } else if (s.contains("[force]")) {
                mode = ShmemTHPMode.force;
            } else {
                throw new RuntimeException("Unexpected content of " + file + ": " + s);
            }
        } catch (IOException e) {
            System.out.println("Failed to read " + file);
            // Happens when the kernel is not built to support THPs.
        }
        return mode;
    }

    // Fill object with info read from proc file system
    public static HugePageConfiguration readFromOS() throws IOException {
        return new HugePageConfiguration(readSupportedHugePagesFromOS(),
                readDefaultHugePageSizeFromOS(),
                readTHPModeFromOS(),
                readTHPPageSizeFromOS(),
                readShmemTHPModeFromOS());
    }

    public static long parseSIUnit(String num, String unit) {
        long n = Long.parseLong(num);
        return switch (unit) {
            case "K" -> n * 1024;
            case "M" -> n * 1024 * 1024;
            case "G" -> n * 1024 * 1024 * 1024;
            default -> throw new RuntimeException("Invalid unit " + unit);
        };
    }

    public static HugePageConfiguration readFromJVMLog(OutputAnalyzer output) {
        // Expects output from -Xlog:pagesize
        // Example:
        // [0.001s][info][pagesize] Static hugepage support:
        // [0.001s][info][pagesize]   hugepage size: 2M
        // [0.001s][info][pagesize]   hugepage size: 1G
        // [0.001s][info][pagesize]   default hugepage size: 2M
        // [0.001s][info][pagesize] Transparent hugepage (THP) support:
        // [0.001s][info][pagesize]   THP mode: madvise
        // [0.001s][info][pagesize]   THP pagesize: 2M
        // [0.001s][info][pagesize] Shared memory transparent hugepage (THP) support:
        // [0.001s][info][pagesize]   Shared memory THP mode: always
        TreeSet<StaticHugePageConfig> staticHugePageConfigs = new TreeSet<>();
        long defaultHugepageSize = 0;
        THPMode thpMode = THPMode.never;
        ShmemTHPMode shmemThpMode = ShmemTHPMode.unknown;
        long thpPageSize = 0;
        Pattern patternHugepageSize = Pattern.compile(".*\\[pagesize] *hugepage size: (\\d+)([KMG])");
        Pattern patternDefaultHugepageSize = Pattern.compile(".*\\[pagesize] *default hugepage size: (\\d+)([KMG]) *");
        Pattern patternTHPPageSize = Pattern.compile(".*\\[pagesize] *  THP pagesize: (\\d+)([KMG])");
        Pattern patternTHPMode = Pattern.compile(".*\\[pagesize] *  THP mode: (\\S+)");
        Pattern patternShmemTHPMode = Pattern.compile(".*\\[pagesize] *Shared memory THP mode: (\\S+)");
        List<String> lines = output.asLines();
        for (String s : lines) {
            Matcher mat = patternHugepageSize.matcher(s);
            if (mat.matches()) {
                StaticHugePageConfig config = new StaticHugePageConfig();
                config.pageSize = parseSIUnit(mat.group(1), mat.group(2));
                staticHugePageConfigs.add(config);
                continue;
            }
            if (defaultHugepageSize == 0) {
                mat = patternDefaultHugepageSize.matcher(s);
                if (mat.matches()) {
                    defaultHugepageSize = parseSIUnit(mat.group(1), mat.group(2));
                    continue;
                }
            }
            if (thpPageSize == 0) {
                mat = patternTHPPageSize.matcher(s);
                if (mat.matches()) {
                    thpPageSize = parseSIUnit(mat.group(1), mat.group(2));
                    continue;
                }
            }
            mat = patternTHPMode.matcher(s);
            if (mat.matches()) {
                thpMode = THPMode.valueOf(mat.group(1));
            }
            mat = patternShmemTHPMode.matcher(s);
            if (mat.matches()) {
                shmemThpMode = ShmemTHPMode.valueOf(mat.group(1));
            }
        }

        return new HugePageConfiguration(staticHugePageConfigs, defaultHugepageSize, thpMode, thpPageSize, shmemThpMode);
    }

}
