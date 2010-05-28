/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 *
 *  This isn't the test case: ResourceCheckTest.sh is.
 *  Refer to ResourceCheckTest.sh when running this test.
 *
 *  @bug 5008856 5023573 5024917 5062569
 *  @summary 'missing resource key' error for key = "Operating system"
 */

import java.awt.event.KeyEvent;

import sun.tools.jconsole.Resources;

public class ResourceCheckTest {

    public static void main(String[] args){
        Object [][] testData = {
            {"<", "", "", "", ""},
            {"<<", "", "", "", ""},
            {">", "", "", "", ""},
            {" 1 day", "", "", "", ""},
            {" 1 hour", "", "", "", ""},
            {" 1 min", "", "", "", ""},
            {" 1 month", "", "", "", ""},
            {" 1 year", "", "", "", ""},
            {" 2 hours", "", "", "", ""},
            {" 3 hours", "", "", "", ""},
            {" 3 months", "", "", "", ""},
            {" 5 min", "", "", "", ""},
            {" 6 hours", "", "", "", ""},
            {" 6 months", "", "", "", ""},
            {" 7 days", "", "", "", ""},
            {"10 min", "", "", "", ""},
            {"12 hours", "", "", "", ""},
            {"30 min", "", "", "", ""},
            {"ACTION", "", "", "", ""},
            {"ACTION_INFO", "", "", "", ""},
            {"All", "", "", "", ""},
            {"Architecture", "", "", "", ""},
            {"Attribute", "", "", "", ""},
            {"Attribute value", "", "", "", ""},
            {"Attribute values", "", "", "", ""},
            {"Attributes", "", "", "", ""},
            {"Blank", "", "", "", ""},
            {"BlockedCount WaitedCount", "BlockedCount", "WaitedCount", "", ""},
            {"Boot class path", "", "", "", ""},
            {"BorderedComponent.moreOrLessButton.toolTip", "", "", "", ""},
            {"Close", "", "", "", ""},
            {"CPU Usage", "", "", "", ""},
            {"CPUUsageFormat","PhonyPercentage", "", "", ""},
            {"Cancel", "", "", "", ""},
            {"Cascade", "", "", "", ""},
            {"Cascade.mnemonic", "", "", "", ""},
            {"Chart:", "", "", "", ""},
            {"Chart:.mnemonic", "", "", "", ""},
            {"ClassTab.infoLabelFormat", "LoadedCount", "UnloadedCount", "TotalCount", ""},
            {"ClassTab.loadedClassesPlotter.accessibleName", "", "", "", ""},
            {"Class path", "", "", "", ""},
            {"Classes", "", "", "", ""},
            {"ClassName", "", "", "", ""},
            {"Column.Name", "", "", "", ""},
            {"Column.PID", "", "", "", ""},
            {"Committed", "", "", "", ""},
            {"Committed memory", "", "", "", ""},
            {"Committed virtual memory", "", "", "", ""},
            {"Compiler", "", "", "", ""},
            {"Connect...", "", "", "", ""},
            {"Connect", "", "", "", ""},
            {"Connect.mnemonic", "", "", "", ""},
            {"ConnectDialog.connectButton.toolTip", "", "", "", ""},
            {"ConnectDialog.accessibleDescription", "", "", "", ""},
            {"ConnectDialog.masthead.accessibleName", "", "", "", ""},
            {"ConnectDialog.masthead.title", "", "", "", ""},
            {"ConnectDialog.statusBar.accessibleName", "", "", "", ""},
            {"ConnectDialog.title", "", "", "", ""},
            {"Connected. Click to disconnect.", "", "", "", ""},
            {"connectingTo1", "PhonyConnectionName", "", "", ""},
            {"connectingTo2", "PhonyConnectionName", "", "", ""},
            {"connectionFailed1", "", "", "", ""},
            {"connectionFailed2", "PhonyConnectionName", "", "", ""},
            {"connectionLost1", "", "", "", ""},
            {"connectionLost2", "PhonyConnectionName", "", "", ""},
            {"Connection failed", "", "", "", ""},
            {"Connection", "", "", "", ""},
            {"Connection.mnemonic", "", "", "", ""},
            {"Connection name", "", "", "", ""},
            {"ConnectionName (disconnected)", "Phony", "Phony", "", ""},
            {"Constructor", "", "", "", ""},
            {"Create", "Phony", "Phony", "", ""},
            {"Current classes loaded", "", "", "", ""},
            {"Current heap size", "", "", "", ""},
            {"Current value", "PhonyValue", "", "", ""},
            {"Daemon threads", "", "", "", ""},
            {"deadlockAllTab", "", "", "", ""},
            {"deadlockTab", "", "", "", ""},
            {"deadlockTabN", "PhonyInt", "", "", ""},
            {"Description", "", "", "", ""},
            {"Descriptor", "", "", "", ""},
            {"Details", "", "", "", ""},
            {"Detect Deadlock", "", "", "", ""},
            {"Detect Deadlock.mnemonic", "", "", "", ""},
            {"Detect Deadlock.toolTip", "", "", "", ""},
            {"Dimension is not supported:", "", "", "", ""},
            {"Discard chart", "", "", "", ""},
            {"Disconnected. Click to connect.", "", "", "", ""},
            {"Double click to expand/collapse", "", "", "", ""},
            {"Double click to visualize", "", "", "", ""},
            {"DurationDaysHoursMinutes", 0, 13, 54, ""},
            {"DurationDaysHoursMinutes", 1, 13, 54, ""},
            {"DurationDaysHoursMinutes", 2, 13, 54, ""},
            {"DurationDaysHoursMinutes", 1024, 13, 45, ""},
            {"DurationHoursMinutes", 0, 13, "", ""},
            {"DurationHoursMinutes", 1, 0, "", ""},
            {"DurationHoursMinutes", 1, 1, "", ""},
            {"DurationHoursMinutes", 2, 42, "", ""},
            {"DurationMinutes", 0, "", "", ""},
            {"DurationMinutes", 1, "", "", ""},
            {"DurationMinutes", 2, "", "", ""},
            {"DurationSeconds", 0, "", "", ""},
            {"DurationSeconds", 1, "", "", ""},
            {"DurationSeconds", 2, "", "", ""},
            {"Empty array", "", "", "", ""},
            {"Error", "", "", "", ""},
            {"Error: MBeans already exist", "", "", "", ""},
            {"Error: MBeans do not exist", "", "", "", ""},
            {"Event", "", "", "", ""},
            {"Exit", "", "", "", ""},
            {"Exit.mnemonic", "", "", "", ""},
            {"expand", "", "", "", ""},
            {"Fail to load plugin", "", "", "", ""},
            {"FileChooser.fileExists.cancelOption", "", "", "", ""},
            {"FileChooser.fileExists.message", "PhonyFileName", "", "", ""},
            {"FileChooser.fileExists.okOption", "", "", "", ""},
            {"FileChooser.fileExists.title", "", "", "", ""},
            {"FileChooser.savedFile", "PhonyFilePath", "PhonyFileSize", "", ""},
            {"FileChooser.saveFailed.message", "PhonyFilePath", "PhonyMessage", "", ""},
            {"FileChooser.saveFailed.title", "", "", "", ""},
            {"Free physical memory", "", "", "", ""},
            {"Free swap space", "", "", "", ""},
            {"Garbage collector", "", "", "", ""},
            {"GC time", "", "", "", ""},
            {"GC time details", 54, "Phony", 11, ""},
            {"GcInfo", "Phony", -1, 768, ""},
            {"GcInfo", "Phony", 0, 768, ""},
            {"GcInfo", "Phony", 1, 768, ""},
            {"Heap", "", "", "", ""},
            {"Heap Memory Usage", "", "", "", ""},
            {"Help.AboutDialog.accessibleDescription", "", "", "", ""},
            {"Help.AboutDialog.jConsoleVersion", "DummyVersion", "", "", ""},
            {"Help.AboutDialog.javaVersion", "DummyVersion", "", "", ""},
            {"Help.AboutDialog.masthead.accessibleName", "", "", "", ""},
            {"Help.AboutDialog.masthead.title", "", "", "", ""},
            {"Help.AboutDialog.title", "", "", "", ""},
            {"Help.AboutDialog.userGuideLink", "DummyMessage", "", "", ""},
            {"Help.AboutDialog.userGuideLink.mnemonic", "", "", "", ""},
            {"Help.AboutDialog.userGuideLink.url", "DummyURL", "", "", ""},
            {"HelpMenu.About.title", "", "", "", ""},
            {"HelpMenu.About.title.mnemonic", "", "", "", ""},
            {"HelpMenu.UserGuide.title", "", "", "", ""},
            {"HelpMenu.UserGuide.title.mnemonic", "", "", "", ""},
            {"HelpMenu.title", "", "", "", ""},
            {"HelpMenu.title.mnemonic", "", "", "", ""},
            {"Hotspot MBeans...", "", "", "", ""},
            {"Hotspot MBeans....mnemonic", "", "", "", ""},
            {"Hotspot MBeans.dialog.accessibleDescription", "", "", "", ""},
            {"Impact", "", "", "", ""},
            {"Info", "", "", "", ""},
            {"INFO", "", "", "", ""},
            {"Invalid plugin path", "", "", "", ""},
            {"Invalid URL", "", "", "", ""},
            {"Is", "", "", "", ""},
            {"Java Monitoring & Management Console", "", "", "", ""},
            {"Java Virtual Machine", "", "", "", ""},
            {"JConsole: ", "", "", "", ""},
            {"JConsole.accessibleDescription", "", "", "", ""},
            {"JConsole version", "PhonyVersion", "", "", ""},
            {"JIT compiler", "", "", "", ""},
            {"Library path", "", "", "", ""},
            {"Live Threads", "", "", "", ""},
            {"Loaded", "", "", "", ""},
            {"Local Process:", "", "", "", ""},
            {"Local Process:.mnemonic", "", "", "", ""},
            {"Manage Hotspot MBeans in: ", "", "", "", ""},
            {"Management Not Enabled", "", "", "", ""},
            {"Management Will Be Enabled", "", "", "", ""},
            {"Masthead.font", "", "", "", ""},
            {"Max", "", "", "", ""},
            {"Max", "", "", "", ""},
            {"Maximum heap size", "", "", "", ""},
            {"MBeanAttributeInfo", "", "", "", ""},
            {"MBeanInfo", "", "", "", ""},
            {"MBeanNotificationInfo", "", "", "", ""},
            {"MBeanOperationInfo", "", "", "", ""},
            {"MBeans", "", "", "", ""},
            {"MBeansTab.clearNotificationsButton", "", "", "", ""},
            {"MBeansTab.clearNotificationsButton.mnemonic", "", "", "", ""},
            {"MBeansTab.clearNotificationsButton.toolTip", "", "", "", ""},
            {"MBeansTab.compositeNavigationMultiple", 0, 0, "", ""},
            {"MBeansTab.compositeNavigationSingle", "", "", "", ""},
            {"MBeansTab.refreshAttributesButton", "", "", "", ""},
            {"MBeansTab.refreshAttributesButton.mnemonic", "", "", "", ""},
            {"MBeansTab.refreshAttributesButton.toolTip", "", "", "", ""},
            {"MBeansTab.subscribeNotificationsButton", "", "", "", ""},
            {"MBeansTab.subscribeNotificationsButton.mnemonic", "", "", "", ""},
            {"MBeansTab.subscribeNotificationsButton.toolTip", "", "", "", ""},
            {"MBeansTab.tabularNavigationMultiple", 0, 0, "", ""},
            {"MBeansTab.tabularNavigationSingle", "", "", "", ""},
            {"MBeansTab.unsubscribeNotificationsButton", "", "", "", ""},
            {"MBeansTab.unsubscribeNotificationsButton.mnemonic", "", "", "", ""},
            {"MBeansTab.unsubscribeNotificationsButton.toolTip", "", "", "", ""},
            {"Memory", "", "", "", ""},
            {"MemoryPoolLabel", "PhonyMemoryPool", "", "", ""},
            {"MemoryTab.heapPlotter.accessibleName", "", "", "", ""},
            {"MemoryTab.infoLabelFormat", "UsedCount", "CommittedCount", "MaxCount", ""},
            {"MemoryTab.nonHeapPlotter.accessibleName", "", "", "", ""},
            {"MemoryTab.poolChart.aboveThreshold", "Threshold", "", "", ""},
            {"MemoryTab.poolChart.accessibleName", "", "", "", ""},
            {"MemoryTab.poolChart.belowThreshold", "Threshold", "", "", ""},
            {"MemoryTab.poolPlotter.accessibleName", "PhonyMemoryPool", "", "", ""},
            {"Message", "", "", "", ""},
            {"Method successfully invoked", "", "", "", ""},
            {"Monitor locked", "", "", "", ""},
            {"Minimize All", "", "", "", ""},
            {"Minimize All.mnemonic", "", "", "", ""},
            {"Name", "", "", "", ""},
            {"Name and Build", "PhonyName", "PhonyBuild", "", ""},
            {"Name Build and Mode", "PhonyName", "PhonyBuild", "PhonyMode", ""},
            {"Name State", "PhonyName", "PhonyState", "", ""},
            {"Name State LockName", "PhonyName", "PhonyState", "PhonyLock", ""},
            {"Name State LockName LockOwner", "PhonyName", "PhonyState", "PhonyLock", "PhonyOwner"},
            {"New Connection...", "", "", "", ""},
            {"New Connection....mnemonic", "", "", "", ""},
            {"No deadlock detected", "", "", "", ""},
            {"Non-Heap", "", "", "", ""},
            {"Non-Heap Memory Usage", "", "", "", ""},
            {"Notification", "", "", "", ""},
            {"Notification buffer", "", "", "", ""},
            {"Notifications", "", "", "", ""},
            {"NotifTypes", "", "", "", ""},
            {"Number of Loaded Classes", "", "", "", ""},
            {"Number of processors", "", "", "", ""},
            {"Number of Threads", "", "", "", ""},
            {"ObjectName", "", "", "", ""},
            {"Operating System", "", "", "", ""},
            {"Operation", "", "", "", ""},
            {"Operation invocation", "", "", "", ""},
            {"Operation return value", "", "", "", ""},
            {"Operations", "", "", "", ""},
            {"Overview", "", "", "", ""},
            {"OverviewPanel.plotter.accessibleName", "PhonyPlotter", "", "", ""},
            {"Parameter", "", "", "", ""},
            {"Password: ", "", "", "", ""},
            {"Password: .mnemonic", "", "", "", ""},
            {"Password.accessibleName", "", "", "", ""},
            {"Peak", "", "", "", ""},
            {"Perform GC", "", "", "", ""},
            {"Perform GC.mnemonic", "", "", "", ""},
            {"Perform GC.toolTip", "", "", "", ""},
            {"Plotter.accessibleName", "", "", "", ""},
            {"Plotter.accessibleName.keyAndValue", "Key", "Value", "", ""},
            {"Plotter.accessibleName.noData", "", "", "", ""},
            {"Plotter.saveAsMenuItem", "", "", "", ""},
            {"Plotter.saveAsMenuItem.mnemonic", "", "", "", ""},
            {"Plotter.timeRangeMenu", "", "", "", ""},
            {"Plotter.timeRangeMenu.mnemonic", "", "", "", ""},
            {"plot", "", "", "", ""},
            {"Problem adding listener", "", "", "", ""},
            {"Problem displaying MBean", "", "", "", ""},
            {"Problem invoking", "", "", "", ""},
            {"Problem removing listener", "", "", "", ""},
            {"Problem setting attribute", "", "", "", ""},
            {"Process CPU time", "", "", "", ""},
            {"Readable", "", "", "", ""},
            {"Reconnect", "", "", "", ""},
            {"Remote Process:", "", "", "", ""},
            {"Remote Process:.mnemonic", "", "", "", ""},
            {"Remote Process.textField.accessibleName", "", "", "", ""},
            {"remoteTF.usage", "", "", "", ""},
            {"Restore All", "", "", "", ""},
            {"Restore All.mnemonic", "", "", "", ""},
            {"ReturnType", "", "", "", ""},
            {"SeqNum", "", "", "", ""},
            {"Size Bytes", 512, "", "", ""},
            {"Size Gb", 512, "", "", ""},
            {"Size Kb", 512, "", "", ""},
            {"Size Mb", 512, "", "", ""},
            {"Source", "", "", "", ""},
            {"Stack trace", "", "", "", ""},
            {"SummaryTab.headerDateTimeFormat", "", "", "", ""},
            {"SummaryTab.pendingFinalization.label", "", "", "", ""},
            {"SummaryTab.pendingFinalization.value", "ObjectCount", "", "", ""},
            {"SummaryTab.tabName", "", "", "", ""},
            {"SummaryTab.vmVersion",  "VMName", "VMVersion", "", ""},
            {"ThreadTab.infoLabelFormat", "LiveCount", "PeakCount", "TotalCount", ""},
            {"ThreadTab.threadInfo.accessibleName", "", "", "", ""},
            {"ThreadTab.threadPlotter.accessibleName", "", "", "", ""},
            {"Threads", "", "", "", ""},
            {"Threshold", "", "", "", ""},
            {"Tile", "", "", "", ""},
            {"Tile.mnemonic", "", "", "", ""},
            {"Time", "", "", "", ""},
            {"Time Range:", "", "", "", ""},
            {"Time Range:.mnemonic", "", "", "", ""},
            {"TimeStamp", "", "", "", ""},
            {"Total classes loaded", "", "", "", ""},
            {"Total classes unloaded", "", "", "", ""},
            {"Total compile time", "", "", "", ""},
            {"Total Loaded", "", "", "", ""},
            {"Total physical memory", "", "", "", ""},
            {"Total swap space", "", "", "", ""},
            {"Total threads started", "", "", "", ""},
            {"Type", "", "", "", ""},
            {"Unavailable", "", "", "", ""},
            {"UNKNOWN", "", "", "", ""},
            {"Unregister", "", "", "", ""},
            {"Uptime", "", "", "", ""},
            {"Usage Threshold", "", "", "", ""},
            {"Used", "", "", "", ""},
            {"Username: ", "", "", "", ""},
            {"Username: .mnemonic", "", "", "", ""},
            {"Username.accessibleName", "", "", "", ""},
            {"UserData", "", "", "", ""},
            {"Value", "", "", "", ""},
            {"Vendor", "", "", "", ""},
            {"Verbose Output", "", "", "", ""},
            {"Verbose Output.toolTip", "", "", "", ""},
            {"visualize", "", "", "", ""},
            {"VM", "", "", "", ""},
            {"VMInternalFrame.accessibleDescription", "", "", "", ""},
            {"VM arguments", "", "", "", ""},
            {"Virtual Machine", "", "", "", ""},
            {"Window", "", "", "", ""},
            {"Window.mnemonic", "", "", "", ""},
            {"Writable", "", "", "", ""},
            {"zz usage text", "PhonyName", "", "", ""},
        };
        //boolean verbose = false;
        boolean verbose = true;

        long badLookups = 0;
        System.out.println("Start...");
        for (int ii = 0; ii < testData.length; ii++) {
            String key = (String)testData[ii][0];

            if (key.endsWith(".mnemonic")) {
                String baseKey = key.substring(0, key.length() - ".mnemonic".length());
                int mnemonic = Resources.getMnemonicInt(baseKey);
                if (mnemonic == 0) {
                    badLookups++;
                    System.out.println("****lookup failed for key = " + key);
                } else {
                    if (verbose) {
                        System.out.println("    mnemonic: " + KeyEvent.getKeyText(mnemonic));
                    }
                }
                continue;
            }

            String ss = Resources.getText(key,
                                          testData[ii][1],
                                          testData[ii][2],
                                          testData[ii][3],
                                          testData[ii][4]);
            if (ss.startsWith("missing resource key")) {
                badLookups++;
                System.out.println("****lookup failed for key = " + key);
            } else {
                if (verbose) {
                    System.out.println("  " + ss);
                }
            }
        }
        if (badLookups > 0) {
            throw new Error ("Resource lookup failed " + badLookups +
                             " time(s); Test failed");
        }
        System.out.println("...Finished.");
    }
}
