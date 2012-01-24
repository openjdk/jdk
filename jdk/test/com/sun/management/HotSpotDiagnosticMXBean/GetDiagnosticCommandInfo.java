/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     7104647
 * @summary Basic Test for HotSpotDiagnosticMXBean.getDiagnosticCommandInfo()
 * @author  Frederic Parain
 *
 * @run main GetDiagnosticCommandInfo
 */

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.DiagnosticCommandInfo;
import com.sun.management.DiagnosticCommandArgumentInfo;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;
import java.util.List;
import javax.management.MBeanServer;

public class GetDiagnosticCommandInfo {
    private static String HOTSPOT_DIAGNOSTIC_MXBEAN_NAME =
        "com.sun.management:type=HotSpotDiagnostic";

    public static void main(String[] args) throws Exception {
        HotSpotDiagnosticMXBean mbean =
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        checkDiagnosticCommandArguments(mbean);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbean = ManagementFactory.newPlatformMXBeanProxy(mbs,
                    HOTSPOT_DIAGNOSTIC_MXBEAN_NAME,
                    HotSpotDiagnosticMXBean.class);
        checkDiagnosticCommandArguments(mbean);
    }

    private static void checkDiagnosticCommandArguments(HotSpotDiagnosticMXBean mbean) {
        // check getDiagnosticCommandInfo()
        StringBuilder sb = new StringBuilder();
        List<DiagnosticCommandInfo> infoList = mbean.getDiagnosticCommandInfo();
        for(DiagnosticCommandInfo info : infoList) {
            printCommandInfo(info,sb);
        }
        // check getDiagnosticCommandInfo(List<String>)
        List<String> commands = mbean.getDiagnosticCommands();
        List<DiagnosticCommandInfo> list2 =
            mbean.getDiagnosticCommandInfo(commands);
        for(DiagnosticCommandInfo info : list2) {
            printCommandInfo(info,sb);
        }
        // check getDiagnosticCommandInfo(String)
        for(String cmd : commands) {
            DiagnosticCommandInfo info2 = mbean.getDiagnosticCommandInfo(cmd);
            printCommandInfo(info2,sb);
        }
        System.out.println(sb.toString());
    }

    private static void printCommandInfo(DiagnosticCommandInfo info,
                                         StringBuilder sb) {
        sb.append("\t").append(info.getName()).append(":\n");
        sb.append("\t\tDescription=").append(info.getDescription()).append("\n");
        sb.append("\t\tImpact=").append(info.getImpact()).append("\n");
        sb.append("\t\tStatus=");
        if (info.isEnabled()) {
            sb.append("Enabled\n");
        } else {
            sb.append("Disbled\n");
        }
        sb.append("\t\tArguments=");
        for(DiagnosticCommandArgumentInfo arg : info.getArgumentsInfo()) {
            printArgumentInfo(arg,sb);
        }
    }

    private static void printArgumentInfo(DiagnosticCommandArgumentInfo info,
                                          StringBuilder sb) {
        sb.append("\t\t\t").append(info.getName()).append(":\n");
        sb.append("\t\t\t\tType=").append(info.getType()).append("\n");
        sb.append("\t\t\t\tDescription=").append(info.getDescription()).append("\n");
        if(info.getDefault() != null) {
            sb.append("\t\t\t\tDefault=").append(info.getDefault()).append("\n");
        }
        if(info.isMandatory()) {
            sb.append("\t\t\t\tMandatory\n");
        } else {
            sb.append("\t\t\t\tOptional\n");
        }
        if(info.isOption()) {
            sb.append("\t\t\t\tIs an option\n");
        } else {
            sb.append("\t\t\t\tIs an argument expected at position");
            sb.append(info.getPosition());
            sb.append("\n");
        }
    }
}
