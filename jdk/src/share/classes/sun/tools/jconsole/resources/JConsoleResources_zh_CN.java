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
public class JConsoleResources_zh_CN extends JConsoleResources {

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
        {" 1 day"," 1 \u5929"},
        {" 1 hour"," 1 \u5c0f\u65f6"},
        {" 1 min"," 1 \u5206\u949f"},
        {" 1 month"," 1 \u4e2a\u6708"},
        {" 1 year"," 1 \u5e74"},
        {" 2 hours"," 2 \u5c0f\u65f6"},
        {" 3 hours"," 3 \u5c0f\u65f6"},
        {" 3 months"," 3 \u4e2a\u6708"},
        {" 5 min"," 5 \u5206\u949f"},
        {" 6 hours"," 6 \u5c0f\u65f6"},
        {" 6 months"," 6 \u4e2a\u6708"},
        {" 7 days"," 7 \u5929"},
        {"10 min","10 \u5206\u949f"},
        {"12 hours","12 \u5c0f\u65f6"},
        {"30 min","30 \u5206\u949f"},
        {"<","<"},
        {"<<","<<"},
        {">",">"},
        {"ACTION","ACTION"},
        {"ACTION_INFO","ACTION_INFO"},
        {"All","\u5168\u90e8"},
        {"Apply","\u5e94\u7528"},
        {"Architecture","\u4f53\u7cfb\u7ed3\u6784"},
        {"Array, OpenType", "\u6570\u7ec4, OpenType"},
        {"Array, OpenType, Numeric value viewer","\u6570\u7ec4, OpenType, \u6570\u503c\u67e5\u770b\u5668"},
        {"Attribute","\u5c5e\u6027"},
        {"Attribute value","\u5c5e\u6027\u503c"},
        {"Attribute values","\u5c5e\u6027\u503c"},
        {"Attributes","\u5c5e\u6027"},
        {"Blank", "\u7a7a\u767d"},
        {"BlockedCount WaitedCount",
             "\u963b\u585e\u603b\u6570\uff1a{0}  \u7b49\u5f85\u603b\u6570\uff1a {1}\n"},
        {"Boot class path","\u5f15\u5bfc\u7c7b\u8def\u5f84"},
        {"BorderedComponent.moreOrLessButton.toolTip", "\u5207\u6362\u4ee5\u663e\u793a\u8f83\u591a\u4fe1\u606f\u6216\u8f83\u5c11\u4fe1\u606f"},
        {"CPU Usage","CPU \u4f7f\u7528\u60c5\u51b5"},
        {"CPUUsageFormat","CPU \u4f7f\u7528\u60c5\u51b5: {0}%"},
        {"Cancel","\u53d6\u6d88"},
        {"Cascade", "\u5c42\u53e0"},
        {"Cascade.mnemonic", 'C'},
        {"Chart:", "\u56fe\u8868\uff1a"},
        {"Chart:.mnemonic", 'C'},
        {"Class path","\u7c7b\u8def\u5f84"},
        {"Class","\u7c7b"},
        {"ClassName","\u7c7b\u540d\u79f0"},
        {"ClassTab.infoLabelFormat", "<html>\u5df2\u52a0\u8f7d: {0}    \u672a\u52a0\u8f7d: {1}    \u603b\u8ba1: {2}</html>"},
        {"ClassTab.loadedClassesPlotter.accessibleName", "\u5df2\u88c5\u5165\u7c7b\u7684\u56fe\u8868\u3002"},
        {"Classes","\u7c7b"},
        {"Close","\u5173\u95ed"},
        {"Column.Name", "\u540d\u79f0"},
        {"Column.PID", "PID"},
        {"Committed memory","\u5206\u914d\u7684\u5185\u5b58"},
        {"Committed virtual memory","\u5206\u914d\u7684\u865a\u62df\u5185\u5b58"},
        {"Committed", "\u5206\u914d"},
        {"Compiler","\u7f16\u8bd1\u5668"},
        {"CompositeData","\u590d\u5408\u6570\u636e"},
        {"Config","\u914d\u7f6e"},
        {"Connect", "\u8fde\u63a5"},
        {"Connect.mnemonic", 'C'},
        {"Connect...","\u8fde\u63a5..."},
        {"ConnectDialog.connectButton.toolTip", "\u8fde\u63a5\u81f3 Java \u865a\u62df\u673a"},
        {"ConnectDialog.accessibleDescription", "\u7528\u4e8e\u4e0e\u672c\u5730\u6216\u8fdc\u7a0b Java \u865a\u62df\u673a\u5efa\u7acb\u65b0\u8fde\u63a5\u7684\u5bf9\u8bdd\u6846"},
        {"ConnectDialog.masthead.accessibleName", "\u6807\u9898\u56fe\u5f62"},
        {"ConnectDialog.masthead.title", "\u65b0\u5efa\u8fde\u63a5"},
        {"ConnectDialog.statusBar.accessibleName", "\u72b6\u6001\u6761"},
        {"ConnectDialog.title", "JConsole\uff1a\u65b0\u5efa\u8fde\u63a5"},
        {"Connected. Click to disconnect.","\u5df2\u8fde\u63a5\u3002\u8bf7\u5355\u51fb\u4ee5\u65ad\u5f00\u8fde\u63a5\u3002"},
        {"Connection failed","\u8fde\u63a5\u5931\u8d25"},
        {"Connection", "\u8fde\u63a5"},
        {"Connection.mnemonic", 'C'},
        {"Connection name", "\u8fde\u63a5\u540d\u79f0"},
        {"ConnectionName (disconnected)","{0}\uff08\u5df2\u65ad\u5f00\u8fde\u63a5\uff09"},
        {"Constructor","\u6784\u9020\u51fd\u6570"},
        {"Current classes loaded", "\u5f53\u524d\u7c7b\u5df2\u88c5\u5165"},
        {"Current heap size","\u5f53\u524d\u5806\u5927\u5c0f"},
        {"Current value","\u5f53\u524d\u503c\uff1a {0}"},
        {"Create", "\u521b\u5efa"},
        {"Daemon threads","\u5b88\u62a4\u7ebf\u7a0b"},
        {"Disconnected. Click to connect.","\u5df2\u65ad\u5f00\u8fde\u63a5\u3002\u8bf7\u5355\u51fb\u4ee5\u8fde\u63a5\u3002"},
        {"Double click to expand/collapse","\u53cc\u51fb\u4ee5\u5c55\u5f00/\u6298\u53e0"},
        {"Double click to visualize", "\u53cc\u51fb\u4ee5\u663e\u793a"},
        {"Description", "\u63cf\u8ff0"},
        {"Description: ", "\u63cf\u8ff0\uff1a "},
        {"Descriptor", "\u63cf\u8ff0\u7b26"},
        {"Details", "\u8be6\u7ec6\u4fe1\u606f"},
        {"Detect Deadlock", "\u68c0\u6d4b\u5230\u6b7b\u9501"},
        {"Detect Deadlock.mnemonic", 'D'},
        {"Detect Deadlock.toolTip", "\u68c0\u6d4b\u5230\u6b7b\u9501\u7684\u7ebf\u7a0b"},
        {"Dimension is not supported:","\u4e0d\u652f\u6301\u7ef4\uff1a"},
        {"Discard chart", "\u653e\u5f03\u56fe\u8868"},
        {"DurationDaysHoursMinutes","{0,choice,1#{0,number,integer} day |1.0<{0,number,integer} days }{1,choice,0<{1,number,integer} hours |1#{1,number,integer} hour |1<{1,number,integer} hours }{2,choice,0<{2,number,integer} minutes|1#{2,number,integer} minute|1.0<{2,number,integer} minutes}"},

        {"DurationHoursMinutes","{0,choice,1#{0,number,integer} hour |1<{0,number,integer} hours }{1,choice,0<{1,number,integer} minutes|1#{1,number,integer} minute|1.0<{1,number,integer} minutes}"},

        {"DurationMinutes","{0,choice,1#{0,number,integer} minute|1.0<{0,number,integer} minutes}"},
        {"DurationSeconds","{0} \u79d2"},
        {"Empty array", "\u7a7a\u6570\u7ec4"},
        {"Empty opentype viewer", "\u7a7a OpenType \u67e5\u770b\u5668"},
        {"Error","\u9519\u8bef"},
        {"Error: MBeans already exist","\u9519\u8bef\uff1aMBean \u5df2\u5b58\u5728"},
        {"Error: MBeans do not exist","\u9519\u8bef\uff1aMBean \u4e0d\u5b58\u5728"},
        {"Error:","\u9519\u8bef\uff1a"},
        {"Event","\u4e8b\u4ef6"},
        {"Exit", "\u9000\u51fa"},
        {"Exit.mnemonic", 'x'},
        {"Fail to load plugin", "\u8b66\u544a: \u65e0\u6cd5\u88c5\u5165\u63d2\u4ef6: {0}"},
        {"FileChooser.fileExists.cancelOption", "\u53d6\u6d88"},
        {"FileChooser.fileExists.message", "<html><center>\u6587\u4ef6\u5df2\u5b58\u5728:<br>{0}<br>\u662f\u5426\u8981\u66ff\u6362\uff1f"},
        {"FileChooser.fileExists.okOption", "\u66ff\u6362"},
        {"FileChooser.fileExists.title", "\u6587\u4ef6\u5df2\u5b58\u5728"},
        {"FileChooser.savedFile", "<html>\u5df2\u4fdd\u5b58\u5230\u6587\u4ef6:<br>{0}<br>\uff08{1} \u5b57\u8282\uff09"},
        {"FileChooser.saveFailed.message", "<html><center>\u4fdd\u5b58\u5230\u6587\u4ef6\u5931\u8d25:<br>{0}<br>{1}"},
        {"FileChooser.saveFailed.title", "\u4fdd\u5b58\u5931\u8d25"},
        {"Free physical memory","\u53ef\u7528\u7269\u7406\u5185\u5b58"},
        {"Free swap space","\u53ef\u7528\u4ea4\u6362\u7a7a\u95f4"},
        {"Garbage collector","\u5783\u573e\u6536\u96c6\u5668"},
        {"GTK","GTK"},
        {"GcInfo","Name = ''{0}'', Collections = {1,choice,-1#Unavailable|0#{1,number,integer}}, Total time spent = {2}"},
        {"GC time","GC \u65f6\u95f4"},
        {"GC time details","{1}\uff08{2} \u9879\u6536\u96c6\uff09\u6240\u7528\u7684\u65f6\u95f4\u4e3a {0}"},
        {"Heap Memory Usage","\u5806\u5185\u5b58\u4f7f\u7528\u60c5\u51b5"},
        {"Heap", "\u5806"},
        {"Help.AboutDialog.accessibleDescription", "\u5305\u542b\u6709\u5173 JConsole \u548c JDK \u7248\u672c\u4fe1\u606f\u7684\u5bf9\u8bdd\u6846"},
        {"Help.AboutDialog.jConsoleVersion", "JConsole \u7248\u672c:<br>{0}"},
        {"Help.AboutDialog.javaVersion", "Java VM \u7248\u672c:<br>{0}"},
        {"Help.AboutDialog.masthead.accessibleName", "\u6807\u9898\u56fe\u5f62"},
        {"Help.AboutDialog.masthead.title", "\u5173\u4e8e JConsole"},
        {"Help.AboutDialog.title", "JConsole\uff1a\u5173\u4e8e"},
        {"Help.AboutDialog.userGuideLink", "JConsole \u7528\u6237\u6307\u5357:<br>{0}"},
        {"Help.AboutDialog.userGuideLink.mnemonic", 'U'},
        {"Help.AboutDialog.userGuideLink.url", "http://java.sun.com/javase/6/docs/technotes/guides/management/MonitoringGuide/toc.html"},
        {"HelpMenu.About.title", "\u5173\u4e8e JConsole"},
        {"HelpMenu.About.title.mnemonic", 'A'},
        {"HelpMenu.UserGuide.title", "\u8054\u673a\u7528\u6237\u6307\u5357"},
        {"HelpMenu.UserGuide.title.mnemonic", 'U'},
        {"HelpMenu.title", "\u5e2e\u52a9"},
        {"HelpMenu.title.mnemonic", 'H'},
        {"Hotspot MBeans...", "Hotspot MBean..."},
        {"Hotspot MBeans....mnemonic", 'H'},
        {"Hotspot MBeans.dialog.accessibleDescription", "\u7528\u4e8e\u7ba1\u7406 Hotspot Mbean \u7684\u5bf9\u8bdd\u6846"},
        {"Impact","\u5f71\u54cd"},
        {"Info","\u4fe1\u606f"},
        {"INFO","INFO"},
        {"Invalid plugin path", "\u8b66\u544a: \u65e0\u6548\u7684\u63d2\u4ef6\u8def\u5f84: {0}"},
        {"Invalid URL", "\u65e0\u6548\u7684 URL: {0}"},
        {"Is","\u4e3a"},
        {"Java Monitoring & Management Console", "Java \u76d1\u89c6\u548c\u7ba1\u7406\u63a7\u5236\u53f0"},
        {"JConsole: ","JConsole: {0}"},
        {"JConsole version","JConsole \u7248\u672c \"{0}\""},
        {"JConsole.accessibleDescription", "Java \u76d1\u89c6\u548c\u7ba1\u7406\u63a7\u5236\u53f0"},
        {"JIT compiler","JIT \u7f16\u8bd1\u5668"},
        {"Java Virtual Machine","Java \u865a\u62df\u673a"},
        {"Java","Java"},
        {"Library path","\u5e93\u8def\u5f84"},
        {"Listeners","\u4fa6\u542c\u5668"},
        {"Live Threads","\u6d3b\u52a8\u7ebf\u7a0b"},
        {"Loaded", "\u5df2\u88c5\u5165"},
        {"Local Process:", "\u672c\u5730\u8fdb\u7a0b:"},
        {"Local Process:.mnemonic", 'L'},
        {"Look and Feel","\u5916\u89c2"},
        {"Masthead.font", "Dialog-PLAIN-25"},
        {"Management Not Enabled","<b>\u6ce8\u610f</b>\uff1a\u5728\u6b64\u8fdb\u7a0b\u4e2d\u672a\u542f\u7528\u7ba1\u7406\u4ee3\u7406\u3002"},
        {"Management Will Be Enabled","<b>\u6ce8\u610f</b>\uff1a\u5728\u6b64\u8fdb\u7a0b\u4e2d\u5c06\u542f\u7528\u7ba1\u7406\u4ee3\u7406\u3002"},
        {"MBeanAttributeInfo","MBeanAttributeInfo"},
        {"MBeanInfo","MBeanInfo"},
        {"MBeanNotificationInfo","MBeanNotificationInfo"},
        {"MBeanOperationInfo","MBeanOperationInfo"},
        {"MBeans","MBean"},
        {"MBeansTab.clearNotificationsButton", "\u6e05\u9664(C)"},
        {"MBeansTab.clearNotificationsButton.mnemonic", 'C'},
        {"MBeansTab.clearNotificationsButton.toolTip", "\u6e05\u9664\u901a\u77e5"},
        {"MBeansTab.compositeNavigationMultiple", "\u590d\u5408\u5bfc\u822a {0}/{1}"},
        {"MBeansTab.compositeNavigationSingle", "\u590d\u5408\u5bfc\u822a"},
        {"MBeansTab.refreshAttributesButton", "\u5237\u65b0(R)"},
        {"MBeansTab.refreshAttributesButton.mnemonic", 'R'},
        {"MBeansTab.refreshAttributesButton.toolTip", "\u5237\u65b0\u5c5e\u6027"},
        {"MBeansTab.subscribeNotificationsButton", "\u8ba2\u9605(S)"},
        {"MBeansTab.subscribeNotificationsButton.mnemonic", 'S'},
        {"MBeansTab.subscribeNotificationsButton.toolTip", "\u5f00\u59cb\u4fa6\u542c\u901a\u77e5"},
        {"MBeansTab.tabularNavigationMultiple", "\u8868\u683c\u5bfc\u822a {0}/{1}"},
        {"MBeansTab.tabularNavigationSingle", "\u8868\u683c\u5bfc\u822a"},
        {"MBeansTab.unsubscribeNotificationsButton", "\u53d6\u6d88\u8ba2\u9605(U)"},
        {"MBeansTab.unsubscribeNotificationsButton.mnemonic", 'U'},
        {"MBeansTab.unsubscribeNotificationsButton.toolTip", "\u505c\u6b62\u4fa6\u542c\u901a\u77e5"},
        {"Manage Hotspot MBeans in: ", "\u7ba1\u7406 Hotspot MBean \u4e8e\uff1a "},
        {"Max","\u6700\u5927\u503c"},
        {"Maximum heap size","\u5806\u5927\u5c0f\u7684\u6700\u5927\u503c"},
        {"Memory","\u5185\u5b58"},
        {"MemoryPoolLabel", "\u5185\u5b58\u6c60 \"{0}\""},
        {"MemoryTab.heapPlotter.accessibleName", "\u5806\u7684\u5185\u5b58\u4f7f\u7528\u60c5\u51b5\u56fe\u8868\u3002"},
        {"MemoryTab.infoLabelFormat", "<html>\u5df2\u4f7f\u7528: {0}    \u5df2\u63d0\u4ea4: {1}    \u6700\u5927\u503c: {2}</html>"},
        {"MemoryTab.nonHeapPlotter.accessibleName", "\u975e\u5806\u7684\u5185\u5b58\u4f7f\u7528\u60c5\u51b5\u56fe\u8868\u3002"},
        {"MemoryTab.poolChart.aboveThreshold", "\u5927\u4e8e\u9608\u503c {0}\u3002\n"},
        {"MemoryTab.poolChart.accessibleName", "\u5185\u5b58\u6c60\u4f7f\u7528\u60c5\u51b5\u56fe\u8868\u3002"},
        {"MemoryTab.poolChart.belowThreshold", "\u5c0f\u4e8e\u9608\u503c {0}\u3002\n"},
        {"MemoryTab.poolPlotter.accessibleName", "{0} \u7684\u5185\u5b58\u4f7f\u7528\u60c5\u51b5\u56fe\u8868\u3002"},
        {"Message","\u6d88\u606f"},
        {"Method successfully invoked", "\u6210\u529f\u8c03\u7528\u65b9\u6cd5"},
        {"Minimize All", "\u5168\u90e8\u6700\u5c0f\u5316"},
        {"Minimize All.mnemonic", 'M'},
        {"Minus Version", "\u8fd9\u662f {0} \u7248\u672c {1}"},
        {"Monitor locked",
             "   - \u5df2\u9501\u5b9a {0}\n"},
        {"Motif","\u4fee\u6539"},
        {"Name Build and Mode","{0}\uff08\u5185\u90e8\u7248\u672c {1}\u3001{2}\uff09"},
        {"Name and Build","{0}\uff08\u5185\u90e8\u7248\u672c {1}\uff09"},
        {"Name","\u540d\u79f0"},
        {"Name: ","\u540d\u79f0\uff1a "},
        {"Name State",
             "\u540d\u79f0\uff1a {0}\n" +
             "\u72b6\u6001\uff1a {1}\n"},
        {"Name State LockName",
             "\u540d\u79f0\uff1a {0}\n" +
             "\u72b6\u6001\uff1a{1} \u5728 {2} \u4e0a\n"},
        {"Name State LockName LockOwner",
             "\u540d\u79f0\uff1a {0}\n" +
             "\u72b6\u6001\uff1a{1} \u5728 {2} \u4e0a\uff0c\u62e5\u6709\u8005\uff1a {3}\n"},
        {"New Connection...", "\u65b0\u5efa\u8fde\u63a5..."},
        {"New Connection....mnemonic", 'N'},
        {"New value applied","\u5df2\u5e94\u7528\u65b0\u503c"},
        {"No attribute selected","\u672a\u9009\u62e9\u5c5e\u6027"},
        {"No deadlock detected","\u672a\u68c0\u6d4b\u5230\u6b7b\u9501"},
        {"No value selected","\u672a\u9009\u62e9\u503c"},
        {"Non-Heap Memory Usage","\u975e\u5806\u5185\u5b58\u4f7f\u7528\u60c5\u51b5"},
        {"Non-Heap", "\u975e\u5806"},
        {"Not Yet Implemented","\u5c1a\u672a\u5b9e\u73b0"},
        {"Not a valid event broadcaster", "\u4e0d\u662f\u6709\u6548\u7684\u4e8b\u4ef6\u5e7f\u64ad\u5668"},
        {"Notification","\u901a\u77e5"},
        {"Notification buffer","\u901a\u77e5\u7f13\u51b2\u533a"},
        {"Notifications","\u901a\u77e5"},
        {"NotifTypes", "NotifTypes"},
        {"Number of Threads","\u7ebf\u7a0b\u7684\u6570\u76ee"},
        {"Number of Loaded Classes","\u5df2\u88c5\u5165\u7c7b\u7684\u6570\u76ee"},
        {"Number of processors","\u5904\u7406\u5668\u7684\u6570\u76ee"},
        {"ObjectName","ObjectName"},
        {"Operating System","\u64cd\u4f5c\u7cfb\u7edf"},
        {"Operation","\u64cd\u4f5c"},
        {"Operation invocation","\u64cd\u4f5c\u8c03\u7528"},
        {"Operation return value", "\u64cd\u4f5c\u8fd4\u56de\u503c"},
        {"Operations","\u64cd\u4f5c"},
        {"Overview","\u6982\u8ff0"},
        {"OverviewPanel.plotter.accessibleName", "{0} \u7684\u56fe\u8868\u3002"},
        {"Parameter", "\u53c2\u6570"},
        {"Password: ", "\u53e3\u4ee4\uff1a "},
        {"Password: .mnemonic", 'P'},
        {"Password.accessibleName", "\u5bc6\u7801"},
        {"Peak","\u5cf0"},
        {"Perform GC", "\u6267\u884c GC"},
        {"Perform GC.mnemonic", 'G'},
        {"Perform GC.toolTip", "\u8bf7\u6c42\u5783\u573e\u6536\u96c6"},
        {"Plotter.accessibleName", "\u56fe\u8868"},
        {"Plotter.accessibleName.keyAndValue", "{0}={1}\n"},
        {"Plotter.accessibleName.noData", "\u672a\u7ed8\u5236\u6570\u636e\u3002"},
        {"Plotter.saveAsMenuItem", "\u5c06\u6570\u636e\u53e6\u5b58\u4e3a..."},
        {"Plotter.saveAsMenuItem.mnemonic", 'a'},
        {"Plotter.timeRangeMenu", "\u65f6\u95f4\u8303\u56f4"},
        {"Plotter.timeRangeMenu.mnemonic", 'T'},
        {"Problem adding listener","\u6dfb\u52a0\u4fa6\u542c\u5668\u65f6\u51fa\u73b0\u95ee\u9898"},
        {"Problem displaying MBean", "\u663e\u793a MBean \u65f6\u51fa\u73b0\u95ee\u9898"},
        {"Problem invoking", "\u8c03\u7528\u65f6\u51fa\u73b0\u95ee\u9898"},
        {"Problem removing listener","\u5220\u9664\u4fa6\u542c\u5668\u65f6\u51fa\u73b0\u95ee\u9898"},
        {"Problem setting attribute","\u8bbe\u7f6e\u5c5e\u6027\u65f6\u51fa\u73b0\u95ee\u9898"},
        {"Process CPU time","\u5904\u7406 CPU \u65f6\u95f4"},
        {"R/W","R/W"},
        {"Readable","\u53ef\u8bfb"},
        {"Received","\u5df2\u6536\u5230"},
        {"Reconnect","\u91cd\u65b0\u8fde\u63a5"},
        {"Remote Process:", "\u8fdc\u7a0b\u8fdb\u7a0b:"},
        {"Remote Process:.mnemonic", 'R'},
        {"Remote Process.textField.accessibleName", "\u8fdc\u7a0b\u8fdb\u7a0b"},
        {"Remove","\u5220\u9664"},
        {"Restore All", "\u5168\u90e8\u6062\u590d"},
        {"Restore All.mnemonic", 'R'},
        {"Return value", "\u8fd4\u56de\u503c"},
        {"ReturnType", "ReturnType"},
        {"SeqNum","\u5e8f\u5217\u53f7"},
        {"Size Bytes", "{0,number,integer} \u5b57\u8282"},
        {"Size Gb","{0} Gb"},
        {"Size Kb","{0} Kb"},
        {"Size Mb","{0} Mb"},
        {"Source","\u6e90"},
        {"Stack trace",
             "\n\u5806\u6808\u8ffd\u8e2a\uff1a \n"},
        {"Success:","\u6210\u529f\uff1a"},
        // Note: SummaryTab.headerDateTimeFormat can be one the following:
        // 1. A combination of two styles for date and time, using the
        //    constants from class DateFormat: SHORT, MEDIUM, LONG, FULL.
        //    Example: "MEDIUM,MEDIUM" or "FULL,SHORT"
        // 2. An explicit string pattern used for creating an instance
        //    of the class SimpleDateFormat.
        //    Example: "yyyy-MM-dd HH:mm:ss" or "M/d/yyyy h:mm:ss a"
        {"SummaryTab.headerDateTimeFormat", "FULL,FULL"},
        {"SummaryTab.pendingFinalization.label", "\u6682\u6302\u7ed3\u675f\u64cd\u4f5c"},
        {"SummaryTab.pendingFinalization.value", "{0} \u4e2a\u5bf9\u8c61"},
        {"SummaryTab.tabName", "VM \u6458\u8981"},
        {"SummaryTab.vmVersion","{0} \u7248\u672c {1}"},
        {"TabularData are not supported", "\u4e0d\u652f\u6301\u8868\u683c\u5f0f\u6570\u636e"},
        {"Threads","\u7ebf\u7a0b"},
        {"ThreadTab.infoLabelFormat", "<html>\u6d3b\u52a8: {0}    \u5cf0\u503c: {1}    \u603b\u8ba1: {2}</html>"},
        {"ThreadTab.threadInfo.accessibleName", "\u7ebf\u7a0b\u4fe1\u606f"},
        {"ThreadTab.threadPlotter.accessibleName", "\u7ebf\u7a0b\u6570\u76ee\u56fe\u8868\u3002"},
        {"Threshold","\u9608\u503c"},
        {"Tile", "\u5e73\u94fa"},
        {"Tile.mnemonic", 'T'},
        {"Time Range:", "\u65f6\u95f4\u8303\u56f4\uff1a"},
        {"Time Range:.mnemonic", 'T'},
        {"Time", "\u65f6\u95f4"},
        {"TimeStamp","\u65f6\u95f4\u6233"},
        {"Total Loaded", "\u5df2\u88c5\u5165\u7684\u603b\u6570"},
        {"Total classes loaded","\u5df2\u88c5\u5165\u7c7b\u7684\u603b\u6570"},
        {"Total classes unloaded","\u5df2\u5378\u8f7d\u7c7b\u7684\u603b\u6570"},
        {"Total compile time","\u7f16\u8bd1\u603b\u65f6\u95f4"},
        {"Total physical memory","\u7269\u7406\u5185\u5b58\u603b\u91cf"},
        {"Total threads started","\u5df2\u542f\u52a8\u7684\u7ebf\u7a0b\u603b\u6570"},
        {"Total swap space","\u4ea4\u6362\u7a7a\u95f4\u603b\u91cf"},
        {"Type","\u7c7b\u578b"},
        {"Unavailable","\u4e0d\u53ef\u7528"},
        {"UNKNOWN","\u672a\u77e5"},
        {"Unknown Host","\u672a\u77e5\u4e3b\u673a: {0}"},
        {"Unregister", "\u672a\u6ce8\u518c"},
        {"Uptime","\u6b63\u5e38\u8fd0\u884c\u65f6\u95f4"},
        {"Uptime: ","\u6b63\u5e38\u8fd0\u884c\u65f6\u95f4\uff1a "},
        {"Usage Threshold","\u4f7f\u7528\u9608\u503c"},
        {"remoteTF.usage","<b>\u7528\u6cd5</b>: &lt;hostname&gt;:&lt;port&gt; \u6216 service:jmx:&lt;protocol&gt;:&lt;sap&gt;"},
        {"Used","\u5df2\u4f7f\u7528"},
        {"Username: ", "\u7528\u6237\u540d: "},
        {"Username: .mnemonic", 'U'},
        {"Username.accessibleName", "\u7528\u6237\u540d"},
        {"UserData","\u7528\u6237\u6570\u636e"},
        {"Virtual Machine","\u865a\u62df\u673a"},
        {"VM arguments","VM \u53c2\u6570"},
        {"VM","VM"},
        {"VMInternalFrame.accessibleDescription", "\u7528\u4e8e\u76d1\u89c6 Java \u865a\u62df\u673a\u7684\u5185\u90e8\u6846\u67b6"},
        {"Value","\u503c"},
        {"Vendor", "\u4f9b\u5e94\u5546"},
        {"Verbose Output","\u8be6\u7ec6\u8f93\u51fa"},
        {"Verbose Output.toolTip", "\u4e3a\u7c7b\u88c5\u5165\u7cfb\u7edf\u542f\u7528\u8be6\u7ec6\u8f93\u51fa"},
        {"View value", "\u67e5\u770b\u503c"},
        {"View","\u89c6\u56fe"},
        {"Window", "\u7a97\u53e3"},
        {"Window.mnemonic", 'W'},
        {"Windows","\u7a97\u53e3"},
        {"Writable","\u53ef\u5199"},
        {"You cannot drop a class here", "\u60a8\u4e0d\u80fd\u5c06\u7c7b\u653e\u5728\u6b64\u5904"},
        {"collapse", "\u6298\u53e0"},
        {"connectionFailed1","\u8fde\u63a5\u5931\u8d25\uff1a\u662f\u5426\u91cd\u8bd5\uff1f"},
        {"connectionFailed2","\u4e0e {0} \u7684\u8fde\u63a5\u672a\u6210\u529f\u3002<br>\u662f\u5426\u8981\u91cd\u8bd5\uff1f"},
        {"connectionLost1","\u8fde\u63a5\u65ad\u5f00\uff1a\u662f\u5426\u91cd\u65b0\u8fde\u63a5\uff1f"},
        {"connectionLost2","\u4e0e {0} \u7684\u8fde\u63a5\u5df2\u65ad\u5f00\u539f\u56e0\u662f\u5df2\u7ec8\u6b62\u8fdc\u7a0b\u8fdb\u7a0b\u3002<br>\u662f\u5426\u8981\u91cd\u65b0\u8fde\u63a5\uff1f"},
        {"connectingTo1","\u6b63\u5728\u8fde\u63a5\u81f3 {0}"},
        {"connectingTo2","\u5f53\u524d\u6b63\u5728\u8fde\u63a5\u81f3 {0}\u3002<br>\u8fd9\u5c06\u4f1a\u82b1\u8d39\u4e00\u4e9b\u65f6\u95f4\u3002"},
        {"deadlockAllTab","\u5168\u90e8"},
        {"deadlockTab","\u6b7b\u9501"},
        {"deadlockTabN","\u6b7b\u9501 {0}"},
        {"expand", "\u5c55\u5f00"},
        {"kbytes","{0} Kb"},
        {"operation","\u64cd\u4f5c"},
        {"plot", "\u7ed8\u56fe"},
        {"visualize","\u663e\u793a"},
        {"zz usage text",
             "\u7528\u6cd5: {0} [ -interval=n ] [ -notile ] [ -pluginpath <path> ] [ -version ] [ connection ...]\n\n" +
             "  -interval   \u5c06\u66f4\u65b0\u95f4\u9694\u65f6\u95f4\u8bbe\u7f6e\u4e3a n \u79d2\uff08\u9ed8\u8ba4\u503c\u4e3a 4 \u79d2\uff09\n" +
             "  -notile     \u6700\u521d\u4e0d\u5e73\u94fa\u663e\u793a\u7a97\u53e3\uff08\u5bf9\u4e8e\u4e24\u4e2a\u6216\u66f4\u591a\u8fde\u63a5\uff09\n" +
             "  -pluginpath \u6307\u5b9a jconsole \u7528\u4e8e\u67e5\u627e\u63d2\u4ef6\u7684\u8def\u5f84\n" +
             "  -version    \u8f93\u51fa\u7a0b\u5e8f\u7248\u672c\n\n" +
             "  connection = pid || host:port || JMX URL (service:jmx:<protocol>://...)\n" +
             "  pid       \u76ee\u6807\u8fdb\u7a0b\u7684\u8fdb\u7a0b ID\n" +
             "  host      \u8fdc\u7a0b\u4e3b\u673a\u540d\u6216 IP \u5730\u5740\n" +
             "  port      \u7528\u4e8e\u8fdc\u7a0b\u8fde\u63a5\u7684\u7aef\u53e3\u53f7\n\n" +
             "  -J          \u5bf9\u6b63\u5728\u8fd0\u884c jconsole \u7684 Java \u865a\u62df\u673a\u6307\u5b9a\n" +
             "            \u8f93\u5165\u53c2\u6570"},
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
