/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.jconsole.resources;

import java.util.*;

import static java.awt.event.KeyEvent.*;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the following package(s):
 *
 * <ol>
 * <li> sun.tools.jconsole
 * </ol>
 *
 * <P>
 * Subclasses must override <code>getContents0</code> and provide an array,
 * where each item in the array consists of a <code>String</code> key,
 * and either a <code>String</code> value associated with that key,
 * or if the keys ends with ".mnemonic", an element
 * representing a mnemomic keycode <code>int</code> or <code>char</code>.
 */
public class JConsoleResources extends ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    protected Object[][] getContents0() {
        Object[][] temp = new Object[][] {
                // NOTE 1: The value strings in this file containing "{0}" are
        //         processed by the java.text.MessageFormat class.  Any
        //         single quotes appearing in these strings need to be
        //         doubled up.
        //
        // NOTE 2: To make working with this file a bit easier, please
        //         maintain these messages in ASCII sorted order by
        //         message key.
        //
        // LOCALIZE THIS
        {" 1 day"," 1 day"},
        {" 1 hour"," 1 hour"},
        {" 1 min"," 1 min"},
        {" 1 month"," 1 month"},
        {" 1 year"," 1 year"},
        {" 2 hours"," 2 hours"},
        {" 3 hours"," 3 hours"},
        {" 3 months"," 3 months"},
        {" 5 min"," 5 min"},
        {" 6 hours"," 6 hours"},
        {" 6 months"," 6 months"},
        {" 7 days"," 7 days"},
        {"10 min","10 min"},
        {"12 hours","12 hours"},
        {"30 min","30 min"},
        {"<","<"},
        {"<<","<<"},
        {">",">"},
        {"ACTION","ACTION"},
        {"ACTION_INFO","ACTION_INFO"},
        {"All","All"},
        {"Apply","Apply"},
        {"Architecture","Architecture"},
        {"Array, OpenType", "Array, OpenType"},
        {"Array, OpenType, Numeric value viewer","Array, OpenType, Numeric value viewer"},
        {"Attribute","Attribute"},
        {"Attribute value","Attribute value"},
        {"Attribute values","Attribute values"},
        {"Attributes","Attributes"},
        {"Blank", "Blank"},
        {"BlockedCount WaitedCount",
             "Total blocked: {0}  Total waited: {1}\n"},
        {"Boot class path","Boot class path"},
        {"BorderedComponent.moreOrLessButton.toolTip", "Toggle to show more or less information"},
        {"CPU Usage","CPU Usage"},
        {"CPUUsageFormat","CPU Usage: {0}%"},
        {"Cancel","Cancel"},
        {"Cascade", "Cascade"},
        {"Cascade.mnemonic", 'C'},
        {"Chart:", "Chart:"},
        {"Chart:.mnemonic", 'C'},
        {"Class path","Class path"},
        {"Class","Class"},
        {"ClassName","ClassName"},
        {"ClassTab.infoLabelFormat", "<html>Loaded: {0}    Unloaded: {1}    Total: {2}</html>"},
        {"ClassTab.loadedClassesPlotter.accessibleName", "Chart for Loaded Classes."},
        {"Classes","Classes"},
        {"Close","Close"},
        {"Column.Name", "Name"},
        {"Column.PID", "PID"},
        {"Committed memory","Committed memory"},
        {"Committed virtual memory","Committed virtual memory"},
        {"Committed", "Committed"},
        {"Compiler","Compiler"},
        {"CompositeData","CompositeData"},
        {"Config","Config"},
        {"Connect", "Connect"},
        {"Connect.mnemonic", 'C'},
        {"Connect...","Connect..."},
        {"ConnectDialog.connectButton.toolTip", "Connect to Java Virtual Machine"},
        {"ConnectDialog.accessibleDescription", "Dialog for making a new connection to a local or remote Java Virtual Machine"},
        {"ConnectDialog.masthead.accessibleName", "Masthead Graphic"},
        {"ConnectDialog.masthead.title", "New Connection"},
        {"ConnectDialog.statusBar.accessibleName", "Status Bar"},
        {"ConnectDialog.title", "JConsole: New Connection"},
        {"Connected. Click to disconnect.","Connected. Click to disconnect."},
        {"Connection failed","Connection failed"},
        {"Connection", "Connection"},
        {"Connection.mnemonic", 'C'},
        {"Connection name", "Connection name"},
        {"ConnectionName (disconnected)","{0} (disconnected)"},
        {"Constructor","Constructor"},
        {"Current classes loaded", "Current classes loaded"},
        {"Current heap size","Current heap size"},
        {"Current value","Current value: {0}"},
        {"Create", "Create"},
        {"Daemon threads","Daemon threads"},
        {"Disconnected. Click to connect.","Disconnected. Click to connect."},
        {"Double click to expand/collapse","Double click to expand/collapse"},
        {"Double click to visualize", "Double click to visualize"},
        {"Description", "Description"},
        {"Description: ", "Description: "},
        {"Descriptor", "Descriptor"},
        {"Details", "Details"},
        {"Detect Deadlock", "Detect Deadlock"},
        {"Detect Deadlock.mnemonic", 'D'},
        {"Detect Deadlock.toolTip", "Detect deadlocked threads"},
        {"Dimension is not supported:","Dimension is not supported:"},
        {"Discard chart", "Discard chart"},
        {"DurationDaysHoursMinutes","{0,choice,1#{0,number,integer} day |1.0<{0,number,integer} days }" +
                                    "{1,choice,0<{1,number,integer} hours |1#{1,number,integer} hour |1<{1,number,integer} hours }" +
                                    "{2,choice,0<{2,number,integer} minutes|1#{2,number,integer} minute|1.0<{2,number,integer} minutes}"},

        {"DurationHoursMinutes","{0,choice,1#{0,number,integer} hour |1<{0,number,integer} hours }" +
                                "{1,choice,0<{1,number,integer} minutes|1#{1,number,integer} minute|1.0<{1,number,integer} minutes}"},

        {"DurationMinutes","{0,choice,1#{0,number,integer} minute|1.0<{0,number,integer} minutes}"},
        {"DurationSeconds","{0} seconds"},
        {"Empty array", "Empty array"},
        {"Empty opentype viewer", "Empty opentype viewer"},
        {"Error","Error"},
        {"Error: MBeans already exist","Error: MBeans already exist"},
        {"Error: MBeans do not exist","Error: MBeans do not exist"},
        {"Error:","Error:"},
        {"Event","Event"},
        {"Exit", "Exit"},
        {"Exit.mnemonic", 'x'},
        {"Fail to load plugin", "Warning: Fail to load plugin: {0}"},
        {"FileChooser.fileExists.cancelOption", "Cancel"},
        {"FileChooser.fileExists.message", "<html><center>File already exists:<br>{0}<br>Do you want to replace it?"},
        {"FileChooser.fileExists.okOption", "Replace"},
        {"FileChooser.fileExists.title", "File Exists"},
        {"FileChooser.savedFile", "<html>Saved to file:<br>{0}<br>({1} bytes)"},
        {"FileChooser.saveFailed.message", "<html><center>Save to file failed:<br>{0}<br>{1}"},
        {"FileChooser.saveFailed.title", "Save Failed"},
        {"Free physical memory","Free physical memory"},
        {"Free swap space","Free swap space"},
        {"Garbage collector","Garbage collector"},
        {"GTK","GTK"},
        {"GcInfo","Name = ''{0}'', Collections = {1,choice,-1#Unavailable|0#{1,number,integer}}, Total time spent = {2}"},
        {"GC time","GC time"},
        {"GC time details","{0} on {1} ({2} collections)"},
        {"Heap Memory Usage","Heap Memory Usage"},
        {"Heap", "Heap"},
        {"Help.AboutDialog.accessibleDescription", "Dialog containing information about JConsole and JDK versions"},
        {"Help.AboutDialog.jConsoleVersion", "JConsole version:<br>{0}"},
        {"Help.AboutDialog.javaVersion", "Java VM version:<br>{0}"},
        {"Help.AboutDialog.masthead.accessibleName", "Masthead Graphic"},
        {"Help.AboutDialog.masthead.title", "About JConsole"},
        {"Help.AboutDialog.title", "JConsole: About"},
        {"Help.AboutDialog.userGuideLink", "JConsole User Guide:<br>{0}"},
        {"Help.AboutDialog.userGuideLink.mnemonic", 'U'},
        {"Help.AboutDialog.userGuideLink.url", "http://java.sun.com/javase/6/docs/technotes/guides/management/jconsole.html"},
        {"HelpMenu.About.title", "About JConsole"},
        {"HelpMenu.About.title.mnemonic", 'A'},
        {"HelpMenu.UserGuide.title", "Online User Guide"},
        {"HelpMenu.UserGuide.title.mnemonic", 'U'},
        {"HelpMenu.title", "Help"},
        {"HelpMenu.title.mnemonic", 'H'},
        {"Hotspot MBeans...", "Hotspot MBeans..."},
        {"Hotspot MBeans....mnemonic", 'H'},
        {"Hotspot MBeans.dialog.accessibleDescription", "Dialog for managing Hotspot MBeans"},
        {"Impact","Impact"},
        {"Info","Info"},
        {"INFO","INFO"},
        {"Invalid plugin path", "Warning: Invalid plugin path: {0}"},
        {"Invalid URL", "Invalid URL: {0}"},
        {"Is","Is"},
        {"Java Monitoring & Management Console", "Java Monitoring & Management Console"},
        {"JConsole: ","JConsole: {0}"},
        {"JConsole version","JConsole version \"{0}\""},
        {"JConsole.accessibleDescription", "Java Monitoring & Management Console"},
        {"JIT compiler","JIT compiler"},
        {"Java Virtual Machine","Java Virtual Machine"},
        {"Java","Java"},
        {"Library path","Library path"},
        {"Listeners","Listeners"},
        {"Live Threads","Live threads"},
        {"Loaded", "Loaded"},
        {"Local Process:", "Local Process:"},
        {"Local Process:.mnemonic", 'L'},
        {"Look and Feel","Look and Feel"},
        {"Masthead.font", "Dialog-PLAIN-25"},
        {"Management Not Enabled","<b>Note</b>: The management agent is not enabled on this process."},
        {"Management Will Be Enabled","<b>Note</b>: The management agent will be enabled on this process."},
        {"MBeanAttributeInfo","MBeanAttributeInfo"},
        {"MBeanInfo","MBeanInfo"},
        {"MBeanNotificationInfo","MBeanNotificationInfo"},
        {"MBeanOperationInfo","MBeanOperationInfo"},
        {"MBeans","MBeans"},
        {"MBeansTab.clearNotificationsButton", "Clear"},
        {"MBeansTab.clearNotificationsButton.mnemonic", 'C'},
        {"MBeansTab.clearNotificationsButton.toolTip", "Clear notifications"},
        {"MBeansTab.compositeNavigationMultiple", "Composite Navigation {0}/{1}"},
        {"MBeansTab.compositeNavigationSingle", "Composite Navigation"},
        {"MBeansTab.refreshAttributesButton", "Refresh"},
        {"MBeansTab.refreshAttributesButton.mnemonic", 'R'},
        {"MBeansTab.refreshAttributesButton.toolTip", "Refresh attributes"},
        {"MBeansTab.subscribeNotificationsButton", "Subscribe"},
        {"MBeansTab.subscribeNotificationsButton.mnemonic", 'S'},
        {"MBeansTab.subscribeNotificationsButton.toolTip", "Start listening for notifications"},
        {"MBeansTab.tabularNavigationMultiple", "Tabular Navigation {0}/{1}"},
        {"MBeansTab.tabularNavigationSingle", "Tabular Navigation"},
        {"MBeansTab.unsubscribeNotificationsButton", "Unsubscribe"},
        {"MBeansTab.unsubscribeNotificationsButton.mnemonic", 'U'},
        {"MBeansTab.unsubscribeNotificationsButton.toolTip", "Stop listening for notifications"},
        {"Manage Hotspot MBeans in: ", "Manage Hotspot MBeans in: "},
        {"Max","Max"},
        {"Maximum heap size","Maximum heap size"},
        {"Memory","Memory"},
        {"MemoryPoolLabel", "Memory Pool \"{0}\""},
        {"MemoryTab.heapPlotter.accessibleName", "Memory usage chart for heap."},
        {"MemoryTab.infoLabelFormat", "<html>Used: {0}    Committed: {1}    Max: {2}</html>"},
        {"MemoryTab.nonHeapPlotter.accessibleName", "Memory usage chart for non heap."},
        {"MemoryTab.poolChart.aboveThreshold", "which is above the threshold of {0}.\n"},
        {"MemoryTab.poolChart.accessibleName", "Memory Pool Usage Chart."},
        {"MemoryTab.poolChart.belowThreshold", "which is below the threshold of {0}.\n"},
        {"MemoryTab.poolPlotter.accessibleName", "Memory usage chart for {0}."},
        {"Message","Message"},
        {"Method successfully invoked", "Method successfully invoked"},
        {"Minimize All", "Minimize All"},
        {"Minimize All.mnemonic", 'M'},
        {"Minus Version", "This is {0} version {1}"},
        {"Monitor locked",
             "   - locked {0}\n"},
        {"Motif","Motif"},
        {"Name Build and Mode","{0} (build {1}, {2})"},
        {"Name and Build","{0} (build {1})"},
        {"Name","Name"},
        {"Name: ","Name: "},
        {"Name State",
             "Name: {0}\n" +
             "State: {1}\n"},
        {"Name State LockName",
             "Name: {0}\n" +
             "State: {1} on {2}\n"},
        {"Name State LockName LockOwner",
             "Name: {0}\n" +
             "State: {1} on {2} owned by: {3}\n"},
        {"New Connection...", "New Connection..."},
        {"New Connection....mnemonic", 'N'},
        {"New value applied","New value applied"},
        {"No attribute selected","No attribute selected"},
        {"No deadlock detected","No deadlock detected"},
        {"No value selected","No value selected"},
        {"Non-Heap Memory Usage","Non-Heap Memory Usage"},
        {"Non-Heap", "Non-Heap"},
        {"Not Yet Implemented","Not Yet Implemented"},
        {"Not a valid event broadcaster", "Not a valid event broadcaster"},
        {"Notification","Notification"},
        {"Notification buffer","Notification buffer"},
        {"Notifications","Notifications"},
        {"NotifTypes", "NotifTypes"},
        {"Number of Threads","Number of Threads"},
        {"Number of Loaded Classes","Number of Loaded Classes"},
        {"Number of processors","Number of processors"},
        {"ObjectName","ObjectName"},
        {"Operating System","Operating System"},
        {"Operation","Operation"},
        {"Operation invocation","Operation invocation"},
        {"Operation return value", "Operation return value"},
        {"Operations","Operations"},
        {"Overview","Overview"},
        {"OverviewPanel.plotter.accessibleName", "Chart for {0}."},
        {"Parameter", "Parameter"},
        {"Password: ", "Password: "},
        {"Password: .mnemonic", 'P'},
        {"Password.accessibleName", "Password"},
        {"Peak","Peak"},
        {"Perform GC", "Perform GC"},
        {"Perform GC.mnemonic", 'G'},
        {"Perform GC.toolTip", "Request Garbage Collection"},
        {"Plotter.accessibleName", "Chart"},
        {"Plotter.accessibleName.keyAndValue", "{0}={1}\n"},
        {"Plotter.accessibleName.noData", "No data plotted."},
        {"Plotter.saveAsMenuItem", "Save data as..."},
        {"Plotter.saveAsMenuItem.mnemonic", 'a'},
        {"Plotter.timeRangeMenu", "Time Range"},
        {"Plotter.timeRangeMenu.mnemonic", 'T'},
        {"Problem adding listener","Problem adding listener"},
        {"Problem displaying MBean", "Problem displaying MBean"},
        {"Problem invoking", "Problem invoking"},
        {"Problem removing listener","Problem removing listener"},
        {"Problem setting attribute","Problem setting attribute"},
        {"Process CPU time","Process CPU time"},
        {"R/W","R/W"},
        {"Readable","Readable"},
        {"Received","Received"},
        {"Reconnect","Reconnect"},
        {"Remote Process:", "Remote Process:"},
        {"Remote Process:.mnemonic", 'R'},
        {"Remote Process.textField.accessibleName", "Remote Process"},
        {"Remove","Remove"},
        {"Restore All", "Restore All"},
        {"Restore All.mnemonic", 'R'},
        {"Return value", "Return value"},
        {"ReturnType", "ReturnType"},
        {"SeqNum","SeqNum"},
        {"Size Bytes", "{0,number,integer} bytes"},
        {"Size Gb","{0} Gb"},
        {"Size Kb","{0} Kb"},
        {"Size Mb","{0} Mb"},
        {"Source","Source"},
        {"Stack trace",
              "\nStack trace: \n"},
        {"Success:","Success:"},
        // Note: SummaryTab.headerDateTimeFormat can be one the following:
        // 1. A combination of two styles for date and time, using the
        //    constants from class DateFormat: SHORT, MEDIUM, LONG, FULL.
        //    Example: "MEDIUM,MEDIUM" or "FULL,SHORT"
        // 2. An explicit string pattern used for creating an instance
        //    of the class SimpleDateFormat.
        //    Example: "yyyy-MM-dd HH:mm:ss" or "M/d/yyyy h:mm:ss a"
        {"SummaryTab.headerDateTimeFormat", "FULL,FULL"},
        {"SummaryTab.pendingFinalization.label", "Pending finalization"},
        {"SummaryTab.pendingFinalization.value", "{0} objects"},
        {"SummaryTab.tabName", "VM Summary"},
        {"SummaryTab.vmVersion","{0} version {1}"},
        {"TabularData are not supported", "TabularData are not supported"},
        {"Threads","Threads"},
        {"ThreadTab.infoLabelFormat", "<html>Live: {0}    Peak: {1}    Total: {2}</html>"},
        {"ThreadTab.threadInfo.accessibleName", "Thread Information"},
        {"ThreadTab.threadPlotter.accessibleName", "Chart for number of threads."},
        {"Threshold","Threshold"},
        {"Tile", "Tile"},
        {"Tile.mnemonic", 'T'},
        {"Time Range:", "Time Range:"},
        {"Time Range:.mnemonic", 'T'},
        {"Time", "Time"},
        {"TimeStamp","TimeStamp"},
        {"Total Loaded", "Total Loaded"},
        {"Total classes loaded","Total classes loaded"},
        {"Total classes unloaded","Total classes unloaded"},
        {"Total compile time","Total compile time"},
        {"Total physical memory","Total physical memory"},
        {"Total threads started","Total threads started"},
        {"Total swap space","Total swap space"},
        {"Type","Type"},
        {"Unavailable","Unavailable"},
        {"UNKNOWN","UNKNOWN"},
        {"Unknown Host","Unknown Host: {0}"},
        {"Unregister", "Unregister"},
        {"Uptime","Uptime"},
        {"Uptime: ","Uptime: "},
        {"Usage Threshold","Usage Threshold"},
        {"remoteTF.usage","<b>Usage</b>: &lt;hostname&gt;:&lt;port&gt; OR service:jmx:&lt;protocol&gt;:&lt;sap&gt;"},
        {"Used","Used"},
        {"Username: ", "Username: "},
        {"Username: .mnemonic", 'U'},
        {"Username.accessibleName", "User Name"},
        {"UserData","UserData"},
        {"Virtual Machine","Virtual Machine"},
        {"VM arguments","VM arguments"},
        {"VM","VM"},
        {"VMInternalFrame.accessibleDescription", "Internal frame for monitoring a Java Virtual Machine"},
        {"Value","Value"},
        {"Vendor", "Vendor"},
        {"Verbose Output","Verbose Output"},
        {"Verbose Output.toolTip", "Enable verbose output for class loading system"},
        {"View value", "View value"},
        {"View","View"},
        {"Window", "Window"},
        {"Window.mnemonic", 'W'},
        {"Windows","Windows"},
        {"Writable","Writable"},
        {"You cannot drop a class here", "You cannot drop a class here"},
        {"collapse", "collapse"},
        {"connectionFailed1","Connection Failed: Retry?"},
        {"connectionFailed2","The connection to {0} did not succeed.<br>" +
                             "Would you like to try again?"},
        {"connectionLost1","Connection Lost: Reconnect?"},
        {"connectionLost2","The connection to {0} has been lost " +
                           "because the remote process has been terminated.<br>" +
                           "Would you like to reconnect?"},
        {"connectingTo1","Connecting to {0}"},
        {"connectingTo2","You are currently being connected to {0}.<br>" +
                         "This will take a few moments."},
        {"deadlockAllTab","All"},
        {"deadlockTab","Deadlock"},
        {"deadlockTabN","Deadlock {0}"},
        {"expand", "expand"},
        {"kbytes","{0} kbytes"},
        {"operation","operation"},
        {"plot", "plot"},
        {"visualize","visualize"},
        {"zz usage text",
             "Usage: {0} [ -interval=n ] [ -notile ] [ -pluginpath <path> ] [ -version ] [ connection ... ]\n\n" +
             "  -interval   Set the update interval to n seconds (default is 4 seconds)\n" +
             "  -notile     Do not tile windows initially (for two or more connections)\n" +
             "  -pluginpath Specify the path that jconsole uses to look up the plugins\n\n" +
             "  -version    Print program version\n" +
             "  connection = pid || host:port || JMX URL (service:jmx:<protocol>://...)\n" +
             "  pid         The process id of a target process\n" +
             "  host        A remote host name or IP address\n" +
             "  port        The port number for the remote connection\n\n" +
             "  -J          Specify the input arguments to the Java virtual machine\n" +
             "              on which jconsole is running"},
        // END OF MATERIAL TO LOCALIZE
        };

        String ls = System.getProperty("line.separator");
        for(int i=0;i<temp.length;i++) {
            if (temp[i][1] instanceof String){
            temp[i][1] = temp[i][1].toString().replaceAll("\n",ls);
            }
        }

        return temp;

    }

    public synchronized Object[][] getContents() {
        return getContents0();
    }
}
