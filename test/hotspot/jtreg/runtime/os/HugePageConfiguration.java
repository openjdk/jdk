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
import java.util.Set;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HugePageConfiguration {

    Set<Long> _staticHugePageSizes;
    long _staticDefaultHugePageSize;

    enum THPMode {always, never, madvise, unknown}
    THPMode _thpMode;
    long _thpPageSize;

    public Set<Long> getStaticHugePageSizes() {
        return _staticHugePageSizes;
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

    public HugePageConfiguration(Set<Long> _staticHugePageSizes, long _staticDefaultHugePageSize, THPMode _thpMode, long _thpPageSize) {
        this._staticHugePageSizes = _staticHugePageSizes;
        this._staticDefaultHugePageSize = _staticDefaultHugePageSize;
        this._thpMode = _thpMode;
        this._thpPageSize = _thpPageSize;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "_staticHugePageSizes=" + _staticHugePageSizes +
                ", _staticDefaultHugePageSize=" + _staticDefaultHugePageSize +
                ", _thpMode=" + _thpMode +
                ", _thpPageSize=" + _thpPageSize +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HugePageConfiguration that = (HugePageConfiguration) o;
        return _staticDefaultHugePageSize == that._staticDefaultHugePageSize && _thpPageSize == that._thpPageSize && Objects.equals(_staticHugePageSizes, that._staticHugePageSizes) && _thpMode == that._thpMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_staticHugePageSizes, _staticDefaultHugePageSize, _thpMode, _thpPageSize);
    }

    private static long readDefaultHugePageSizeFromOS() {
        Pattern pat = Pattern.compile("Hugepagesize: *(\\d+) +kB");
        long result = 0;
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

    private static Set<Long> readSupportedHugePagesFromOS() {
        TreeSet<Long> pagesizes = new TreeSet<>();
        Pattern pat = Pattern.compile("hugepages-(\\d+)kB");
        File[] subdirs = new File("/sys/kernel/mm/hugepages").listFiles();
        if (subdirs != null) {
            for (File f : subdirs) {
                String name = f.getName();
                Matcher mat = pat.matcher(name);
                if (mat.matches()) {
                    long pagesize = Long.parseLong(mat.group(1)) * 1024;
                    pagesizes.add(pagesize);
                }
            }
        }
        return pagesizes;
    }

    private static THPMode readTHPModeFromOS() {
        THPMode mode = THPMode.unknown;
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
            mode = THPMode.unknown;
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
        } catch (IOException | NumberFormatException e) { /* ignored */ }
        return pagesize;
    }

    // Fill object with info read from proc file system
    public static HugePageConfiguration readFromOS() {
        return new HugePageConfiguration(readSupportedHugePagesFromOS(),
                readDefaultHugePageSizeFromOS(),
                readTHPModeFromOS(),
                readTHPPageSizeFromOS());
    }

    private static long parseSIUnit(String num, String unit) {
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
        TreeSet<Long> hugepages = new TreeSet<>();
        long defaultHugepageSize = 0;
        THPMode thpMode = THPMode.never;
        long thpPageSize = 0;
        Pattern patternHugepageSize = Pattern.compile(".*\\[pagesize] *hugepage size: (\\d+)([KMG])");
        Pattern patternDefaultHugepageSize = Pattern.compile(".*\\[pagesize] *default hugepage size: (\\d+)([KMG]) *");
        Pattern patternTHPPageSize = Pattern.compile(".*\\[pagesize] *THP pagesize: (\\d+)([KMG])");
        Pattern patternTHPMode = Pattern.compile(".*\\[pagesize] *THP mode: (\\S+)");
        List<String> lines = output.asLines();
        for (String s : lines) {
            Matcher mat = patternHugepageSize.matcher(s);
            if (mat.matches()) {
                hugepages.add(parseSIUnit(mat.group(1), mat.group(2)));
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
        }

        return new HugePageConfiguration(hugepages, defaultHugepageSize, thpMode, thpPageSize);
    }

}
