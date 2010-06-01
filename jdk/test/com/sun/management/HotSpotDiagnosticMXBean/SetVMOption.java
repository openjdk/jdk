/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     6314913
 * @summary Basic Test for HotSpotDiagnosticMXBean.setVMOption()
 *          and getDiagnosticOptions().
 * @author  Mandy Chung
 *
 * @run main/othervm -XX:+PrintGCDetails SetVMOption
 */

import java.lang.management.ManagementFactory;
import java.util.*;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import com.sun.management.VMOption.Origin;
import sun.misc.Version;

public class SetVMOption {
    private static String PRINT_GC_DETAILS = "PrintGCDetails";
    private static String EXPECTED_VALUE = "true";
    private static String BAD_VALUE = "yes";
    private static String NEW_VALUE = "false";
    private static String MANAGEMENT_SERVER = "ManagementServer";
    private static HotSpotDiagnosticMXBean mbean;

    public static void main(String[] args) throws Exception {
        List<HotSpotDiagnosticMXBean> list =
            ManagementFactory.getPlatformMXBeans(HotSpotDiagnosticMXBean.class);

        // The following test is transitional only and should be removed
        // once build 52 is promoted.
        int build = Version.jvmBuildNumber();
        if (build > 0 && build < 52) {
             // JVM support is integrated in build 52
             // this test is skipped if running with VM earlier than 52
             return;
        }

        VMOption option = findPrintGCDetailsOption();
        if (!option.getValue().equalsIgnoreCase(EXPECTED_VALUE)) {
            throw new RuntimeException("Unexpected value: " +
                option.getValue() + " expected: " + EXPECTED_VALUE);
        }
        if (option.getOrigin() != Origin.VM_CREATION) {
            throw new RuntimeException("Unexpected origin: " +
                option.getOrigin() + " expected: VM_CREATION");
        }
        if (!option.isWriteable()) {
            throw new RuntimeException("Expected " + PRINT_GC_DETAILS +
                " to be writeable");
        }

        // set VM option to a new value
        mbean.setVMOption(PRINT_GC_DETAILS, NEW_VALUE);

        option = findPrintGCDetailsOption();
        if (!option.getValue().equalsIgnoreCase(NEW_VALUE)) {
            throw new RuntimeException("Unexpected value: " +
                option.getValue() + " expected: " + NEW_VALUE);
        }
        if (option.getOrigin() != Origin.MANAGEMENT) {
            throw new RuntimeException("Unexpected origin: " +
                option.getOrigin() + " expected: MANAGEMENT");
        }
        VMOption o = mbean.getVMOption(PRINT_GC_DETAILS);
        if (!option.getValue().equals(o.getValue())) {
            throw new RuntimeException("Unmatched value: " +
                option.getValue() + " expected: " + o.getValue());
        }
        if (!option.getValue().equals(o.getValue())) {
            throw new RuntimeException("Unmatched value: " +
                option.getValue() + " expected: " + o.getValue());
        }
        if (option.getOrigin() != o.getOrigin()) {
            throw new RuntimeException("Unmatched origin: " +
                option.getOrigin() + " expected: " + o.getOrigin());
        }
        if (option.isWriteable() != o.isWriteable()) {
            throw new RuntimeException("Unmatched writeable: " +
                option.isWriteable() + " expected: " + o.isWriteable());
        }

        // check if ManagementServer is not writeable
        List<VMOption> options = mbean.getDiagnosticOptions();
        VMOption mgmtServerOption = null;
        for (VMOption o1 : options) {
            if (o1.getName().equals(MANAGEMENT_SERVER)) {
                 mgmtServerOption = o1;
                 break;
            }
        }
        if (mgmtServerOption != null) {
            throw new RuntimeException(MANAGEMENT_SERVER +
                " is not expected to be writeable");
        }
        mgmtServerOption = mbean.getVMOption(MANAGEMENT_SERVER);
        if (mgmtServerOption == null) {
            throw new RuntimeException(MANAGEMENT_SERVER +
                " should exist.");
        }
        if (mgmtServerOption.getOrigin() != Origin.DEFAULT) {
            throw new RuntimeException(MANAGEMENT_SERVER +
                " should have the default value.");
        }
        if (mgmtServerOption.isWriteable()) {
            throw new RuntimeException(MANAGEMENT_SERVER +
                " is not expected to be writeable");
        }
    }

    public static VMOption findPrintGCDetailsOption() {
        List<VMOption> options = mbean.getDiagnosticOptions();
        VMOption gcDetails = null;
        for (VMOption o : options) {
            if (o.getName().equals(PRINT_GC_DETAILS)) {
                 gcDetails = o;
                 break;
            }
        }
        if (gcDetails == null) {
            throw new RuntimeException("VM option " + PRINT_GC_DETAILS +
                " not found");
        }
        return gcDetails;
    }
}
