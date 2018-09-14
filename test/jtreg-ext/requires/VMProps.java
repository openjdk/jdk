/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sun.hotspot.code.Compiler;
import sun.hotspot.cpuinfo.CPUInfo;
import sun.hotspot.gc.GC;
import sun.hotspot.WhiteBox;
import jdk.test.lib.Platform;

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
        map.put("vm.emulatedClient", vmEmulatedClient());
        // vm.hasSA is "true" if the VM contains the serviceability agent
        // and jhsdb.
        map.put("vm.hasSA", vmHasSA());
        // vm.hasSAandCanAttach is "true" if the VM contains the serviceability agent
        // and jhsdb and it can attach to the VM.
        map.put("vm.hasSAandCanAttach", vmHasSAandCanAttach());
        // vm.hasJFR is "true" if JFR is included in the build of the VM and
        // so tests can be executed.
        map.put("vm.hasJFR", vmHasJFR());
        map.put("vm.cpu.features", cpuFeatures());
        map.put("vm.rtm.cpu", vmRTMCPU());
        map.put("vm.rtm.compiler", vmRTMCompiler());
        map.put("vm.aot", vmAOT());
        // vm.cds is true if the VM is compiled with cds support.
        map.put("vm.cds", vmCDS());
        map.put("vm.cds.custom.loaders", vmCDSForCustomLoaders());
        map.put("vm.cds.archived.java.heap", vmCDSForArchivedJavaHeap());
        // vm.graal.enabled is true if Graal is used as JIT
        map.put("vm.graal.enabled", isGraalEnabled());
        map.put("vm.compiler1.enabled", isCompiler1Enabled());
        map.put("vm.compiler2.enabled", isCompiler2Enabled());
        map.put("docker.support", dockerSupport());
        map.put("release.implementor", implementor());
        vmGC(map); // vm.gc.X = true/false
        vmOptFinalFlags(map);

        VMProps.dump(map);
        return map;
    }

    /**
     * Prints a stack trace before returning null.
     * Used by the various helper functions which parse information from
     * VM properties in the case where they don't find an expected property
     * or a propoerty doesn't conform to an expected format.
     *
     * @return null
     */
    private String nullWithException(String message) {
        new Exception(message).printStackTrace();
        return null;
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
            return nullWithException("Can't get 'java.vm.name' property");
        }

        Pattern startP = Pattern.compile(".* (\\S+) VM");
        Matcher m = startP.matcher(vmName);
        if (m.matches()) {
            return m.group(1).toLowerCase();
        }
        return nullWithException("Can't get VM flavor from 'java.vm.name'");
    }

    /**
     * @return VM compilation mode extracted from the "java.vm.info" property.
     */
    protected String vmCompMode() {
        // E.g. "mixed mode"
        String vmInfo = System.getProperty("java.vm.info");
        if (vmInfo == null) {
            return nullWithException("Can't get 'java.vm.info' property");
        }
        if (vmInfo.toLowerCase().indexOf("mixed mode") != -1) {
            return "Xmixed";
        } else if (vmInfo.toLowerCase().indexOf("compiled mode") != -1) {
            return "Xcomp";
        } else if (vmInfo.toLowerCase().indexOf("interpreted mode") != -1) {
            return "Xint";
        } else {
            return nullWithException("Can't get compilation mode from 'java.vm.info'");
        }
    }

    /**
     * @return VM bitness, the value of the "sun.arch.data.model" property.
     */
    protected String vmBits() {
        String dataModel = System.getProperty("sun.arch.data.model");
        if (dataModel != null) {
            return dataModel;
        } else {
            return nullWithException("Can't get 'sun.arch.data.model' property");
        }
    }

    /**
     * @return "true" if Flight Recorder is enabled, "false" if is disabled.
     */
    protected String vmFlightRecorder() {
        Boolean isFlightRecorder = WB.getBooleanVMFlag("FlightRecorder");
        String startFROptions = WB.getStringVMFlag("StartFlightRecording");
        if (isFlightRecorder != null && isFlightRecorder) {
            return "true";
        }
        if (startFROptions != null && !startFROptions.isEmpty()) {
            return "true";
        }
        return "false";
    }

    /**
     * @return debug level value extracted from the "jdk.debug" property.
     */
    protected String vmDebug() {
        String debug = System.getProperty("jdk.debug");
        if (debug != null) {
            return "" + debug.contains("debug");
        } else {
            return nullWithException("Can't get 'jdk.debug' property");
        }
    }

    /**
     * @return true if VM supports JVMCI and false otherwise
     */
    protected String vmJvmci() {
        // builds with jvmci have this flag
        return "" + (WB.getBooleanVMFlag("EnableJVMCI") != null);
    }

    /**
     * @return true if VM runs in emulated-client mode and false otherwise.
     */
    protected String vmEmulatedClient() {
        String vmInfo = System.getProperty("java.vm.info");
        if (vmInfo == null) {
            return "false";
        }
        return "" + vmInfo.contains(" emulated-client");
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
    protected void vmGC(Map<String, String> map) {
        for (GC gc: GC.values()) {
            boolean isAcceptable = gc.isSupported() && (gc.isSelected() || GC.isSelectedErgonomically());
            map.put("vm.gc." + gc.name(), "" + isAcceptable);
        }
    }

    /**
     * Selected final flag.
     * @param map - property-value pairs
     * @param flagName - flag name
     */
    private void vmOptFinalFlag(Map<String, String> map, String flagName) {
        String value = String.valueOf(WB.getBooleanVMFlag(flagName));
        map.put("vm.opt.final." + flagName, value);
    }

    /**
     * Selected sets of final flags.
     * @param map - property-value pairs
     */
    protected void vmOptFinalFlags(Map<String, String> map) {
        vmOptFinalFlag(map, "ClassUnloading");
        vmOptFinalFlag(map, "UseCompressedOops");
        vmOptFinalFlag(map, "EnableJVMCI");
    }

    /**
     * @return "true" if VM has a serviceability agent.
     */
    protected String vmHasSA() {
        return "" + Platform.hasSA();
    }

    /**
     * @return "true" if VM has a serviceability agent and it can
     * attach to the VM.
     */
    protected String vmHasSAandCanAttach() {
        try {
            return "" + Platform.shouldSAAttach();
        } catch (IOException e) {
            System.out.println("Checking whether SA can attach to the VM failed.");
            e.printStackTrace();
            // Run the tests anyways.
            return "true";
        }
    }

    /**
     * @return "true" if the VM is compiled with Java Flight Recorder (JFR)
     * support.
     */
    protected String vmHasJFR() {
        return "" + WB.isJFRIncludedInVmBuild();
    }

    /**
     * @return true if compiler in use supports RTM and false otherwise.
     */
    protected String vmRTMCompiler() {
        boolean isRTMCompiler = false;

        if (Compiler.isC2Enabled() &&
            (Platform.isX86() || Platform.isX64() || Platform.isPPC())) {
            isRTMCompiler = true;
        }
        return "" + isRTMCompiler;
    }

    /**
     * @return true if VM runs RTM supported CPU and false otherwise.
     */
    protected String vmRTMCPU() {
        return "" + CPUInfo.hasFeature("rtm");
    }

    /**
     * @return true if VM supports AOT and false otherwise
     */
    protected String vmAOT() {
        // builds with aot have jaotc in <JDK>/bin
        Path bin = Paths.get(System.getProperty("java.home"))
                        .resolve("bin");
        Path jaotc;
        if (Platform.isWindows()) {
            jaotc = bin.resolve("jaotc.exe");
        } else {
            jaotc = bin.resolve("jaotc");
        }
        return "" + Files.exists(jaotc);
    }

    /**
     * Check for CDS support.
     *
     * @return true if CDS is supported by the VM to be tested.
     */
    protected String vmCDS() {
        if (WB.isCDSIncludedInVmBuild()) {
            return "true";
        } else {
            return "false";
        }
    }

    /**
     * Check for CDS support for custom loaders.
     *
     * @return true if CDS provides support for customer loader in the VM to be tested.
     */
    protected String vmCDSForCustomLoaders() {
        if (vmCDS().equals("true") && Platform.areCustomLoadersSupportedForCDS()) {
            return "true";
        } else {
            return "false";
        }
    }

    /**
     * Check for CDS support for archived Java heap regions.
     *
     * @return true if CDS provides support for archive Java heap regions in the VM to be tested.
     */
    protected String vmCDSForArchivedJavaHeap() {
      if (vmCDS().equals("true") && WB.isJavaHeapArchiveSupported()) {
            return "true";
        } else {
            return "false";
        }
    }

    /**
     * Check if Graal is used as JIT compiler.
     *
     * @return true if Graal is used as JIT compiler.
     */
    protected String isGraalEnabled() {
        return Compiler.isGraalEnabled() ? "true" : "false";
    }

    /**
     * Check if Compiler1 is present.
     *
     * @return true if Compiler1 is used as JIT compiler, either alone or as part of the tiered system.
     */
    protected String isCompiler1Enabled() {
        return Compiler.isC1Enabled() ? "true" : "false";
    }

    /**
     * Check if Compiler2 is present.
     *
     * @return true if Compiler2 is used as JIT compiler, either alone or as part of the tiered system.
     */
    protected String isCompiler2Enabled() {
        return Compiler.isC2Enabled() ? "true" : "false";
    }

   /**
     * A simple check for docker support
     *
     * @return true if docker is supported in a given environment
     */
    protected String dockerSupport() {
        boolean isSupported = false;
        if (Platform.isLinux()) {
           // currently docker testing is only supported for Linux,
           // on certain platforms

           String arch = System.getProperty("os.arch");

           if (Platform.isX64()) {
              isSupported = true;
           }
           else if (Platform.isAArch64()) {
              isSupported = true;
           }
           else if (Platform.isS390x()) {
              isSupported = true;
           }
           else if (arch.equals("ppc64le")) {
              isSupported = true;
           }
        }

        if (isSupported) {
           try {
              isSupported = checkDockerSupport();
           } catch (Exception e) {
              isSupported = false;
           }
         }

        return (isSupported) ? "true" : "false";
    }

    private boolean checkDockerSupport() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("docker", "ps");
        Process p = pb.start();
        p.waitFor(10, TimeUnit.SECONDS);

        return (p.exitValue() == 0);
    }


    private String implementor() {
        try (InputStream in = new BufferedInputStream(new FileInputStream(
                System.getProperty("java.home") + "/release"))) {
            Properties properties = new Properties();
            properties.load(in);
            String implementorProperty = properties.getProperty("IMPLEMENTOR");
            return (implementorProperty == null) ? "null" : implementorProperty.replace("\"", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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
