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
package requires;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sun.hotspot.cpuinfo.CPUInfo;
import sun.hotspot.gc.GC;
import sun.hotspot.WhiteBox;

/**
 * The Class to be invoked by jtreg prior Test Suite execution to
 * collect information about VM.
 * Do not use any API's that may not be available in all target VMs.
 * Properties set by this Class will be available in the @requires expressions.
 */
public class VMProps implements Callable<Map<String, String>> {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    /**
     * Collects information about VM properties.
     * This method will be invoked by jtreg.
     *
     * @return Map of property-value pairs.
     */
    @Override
    public Map<String, String> call() {
        Map<String, String> map = new HashMap<>();
        map.put("vm.flavor", vmFlavor());
        map.put("vm.compMode", vmCompMode());
        map.put("vm.bits", vmBits());
        map.put("vm.flightRecorder", vmFlightRecorder());
        map.put("vm.simpleArch", vmArch());
        map.put("vm.debug", vmDebug());
        map.put("vm.jvmci", vmJvmci());
        map.put("vm.cpu.features", cpuFeatures());
        vmGC(map); // vm.gc.X = true/false

        VMProps.dump(map);
        return map;
    }

    /**
     * @return vm.simpleArch value of "os.simpleArch" property of tested JDK.
     */
    protected String vmArch() {
        String arch = System.getProperty("os.arch");
        if (arch.equals("x86_64") || arch.equals("amd64")) {
            return "x64";
        }
        else if (arch.contains("86")) {
            return "x86";
        } else {
            return arch;
        }
    }



    /**
     * @return VM type value extracted from the "java.vm.name" property.
     */
    protected String vmFlavor() {
        // E.g. "Java HotSpot(TM) 64-Bit Server VM"
        String vmName = System.getProperty("java.vm.name");
        if (vmName == null) {
            return null;
        }

        Pattern startP = Pattern.compile(".* (\\S+) VM");
        Matcher m = startP.matcher(vmName);
        if (m.matches()) {
            return m.group(1).toLowerCase();
        }
        return null;
    }

    /**
     * @return VM compilation mode extracted from the "java.vm.info" property.
     */
    protected String vmCompMode() {
        // E.g. "mixed mode"
        String vmInfo = System.getProperty("java.vm.info");
        if (vmInfo == null) {
            return null;
        }
        int k = vmInfo.toLowerCase().indexOf(" mode");
        if (k < 0) {
            return null;
        }
        vmInfo = vmInfo.substring(0, k);
        switch (vmInfo) {
            case "mixed" : return "Xmixed";
            case "compiled" : return "Xcomp";
            case "interpreted" : return "Xint";
            default: return null;
        }
    }

    /**
     * @return VM bitness, the value of the "sun.arch.data.model" property.
     */
    protected String vmBits() {
        return System.getProperty("sun.arch.data.model");
    }

    /**
     * @return "true" if Flight Recorder is enabled, "false" if is disabled.
     */
    protected String vmFlightRecorder() {
        Boolean isUnlockedCommercialFatures = WB.getBooleanVMFlag("UnlockCommercialFeatures");
        Boolean isFlightRecorder = WB.getBooleanVMFlag("FlightRecorder");
        String startFROptions = WB.getStringVMFlag("StartFlightRecording");
        if (isUnlockedCommercialFatures != null && isUnlockedCommercialFatures) {
            if (isFlightRecorder != null && isFlightRecorder) {
                return "true";
            }
            if (startFROptions != null && !startFROptions.isEmpty()) {
                return "true";
            }
        }
        return "false";
    }

    /**
     * @return debug level value extracted from the "jdk.debug" property.
     */
    protected String vmDebug() {
        return "" + System.getProperty("jdk.debug").contains("debug");
    }

    /**
     * @return true if VM supports JVMCI and false otherwise
     */
    protected String vmJvmci() {
        // builds with jvmci have this flag
        return "" + (WB.getBooleanVMFlag("EnableJVMCI") != null);
    }

    /**
     * @return supported CPU features
     */
    protected String cpuFeatures() {
        return CPUInfo.getFeatures().toString();
    }

    /**
     * For all existing GC sets vm.gc.X property.
     * Example vm.gc.G1=true means:
     *    VM supports G1
     *    User either set G1 explicitely (-XX:+UseG1GC) or did not set any GC
     * @param map - property-value pairs
     */
    protected void vmGC(Map<String, String> map){
        GC currentGC = GC.current();
        boolean isByErgo = GC.currentSetByErgo();
        List<GC> supportedGC = GC.allSupported();
        for (GC gc: GC.values()) {
            boolean isSupported = supportedGC.contains(gc);
            boolean isAcceptable = isSupported && (gc == currentGC || isByErgo);
            map.put("vm.gc." + gc.name(), "" + isAcceptable);
        }
    }

    /**
     * Dumps the map to the file if the file name is given as the property.
     * This functionality could be helpful to know context in the real
     * execution.
     *
     * @param map
     */
    protected static void dump(Map<String, String> map) {
        String dumpFileName = System.getProperty("vmprops.dump");
        if (dumpFileName == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        map.forEach((k, v) -> lines.add(k + ":" + v));
        try {
            Files.write(Paths.get(dumpFileName), lines, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to dump properties into '"
                    + dumpFileName + "'", e);
        }
    }

    /**
     * This method is for the testing purpose only.
     * @param args
     */
    public static void main(String args[]) {
        Map<String, String> map = new VMProps().call();
        map.forEach((k, v) -> System.out.println(k + ": '" + v + "'"));
    }
}
