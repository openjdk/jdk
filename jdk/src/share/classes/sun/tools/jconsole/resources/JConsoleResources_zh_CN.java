/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
        {" 1 hour"," 1 \u5C0F\u65F6"},
        {" 1 min"," 1 \u5206\u949F"},
        {" 1 month"," 1 \u4E2A\u6708"},
        {" 1 year"," 1 \u5E74"},
        {" 2 hours"," 2 \u5C0F\u65F6"},
        {" 3 hours"," 3 \u5C0F\u65F6"},
        {" 3 months"," 3 \u4E2A\u6708"},
        {" 5 min"," 5 \u5206\u949F"},
        {" 6 hours"," 6 \u5C0F\u65F6"},
        {" 6 months"," 6 \u4E2A\u6708"},
        {" 7 days"," 7 \u5929"},
        {"10 min","10 \u5206\u949F"},
        {"12 hours","12 \u5C0F\u65F6"},
        {"30 min","30 \u5206\u949F"},
        {"<","<"},
        {"<<","<<"},
        {">",">"},
        {"ACTION","ACTION"},
        {"ACTION_INFO","ACTION_INFO"},
        {"All","\u5168\u90E8"},
        {"Apply","\u5E94\u7528"},
        {"Architecture","\u4F53\u7CFB\u7ED3\u6784"},
        {"Array, OpenType", "\u6570\u7EC4, OpenType"},
        {"Array, OpenType, Numeric value viewer","\u6570\u7EC4, OpenType, \u6570\u503C\u67E5\u770B\u5668"},
        {"Attribute","\u5C5E\u6027"},
        {"Attribute value","\u5C5E\u6027\u503C"},
        {"Attribute values","\u5C5E\u6027\u503C"},
        {"Attributes","\u5C5E\u6027"},
        {"Blank", "\u7A7A\u767D"},
        {"BlockedCount WaitedCount",
             "\u603B\u963B\u6B62\u6570: {0}, \u603B\u7B49\u5F85\u6570: {1}\n"},
        {"Boot class path","\u5F15\u5BFC\u7C7B\u8DEF\u5F84"},
        {"BorderedComponent.moreOrLessButton.toolTip", "\u5207\u6362\u4EE5\u663E\u793A\u66F4\u591A\u6216\u66F4\u5C11\u4FE1\u606F"},
        {"CPU Usage","CPU \u5360\u7528\u7387"},
        {"CPUUsageFormat","CPU \u5360\u7528\u7387: {0}%"},
        {"Cancel","\u53D6\u6D88"},
        {"Cascade", "\u5C42\u53E0(C)"},
        {"Cascade.mnemonic", 'C'},
        {"Chart:", "\u56FE\u8868(C):"},
        {"Chart:.mnemonic", 'C'},
        {"Class path","\u7C7B\u8DEF\u5F84"},
        {"Class","\u7C7B"},
        {"ClassName","ClassName"},
        {"ClassTab.infoLabelFormat", "<html>\u5DF2\u52A0\u8F7D: {0}    \u5DF2\u5378\u8F7D: {1}    \u603B\u8BA1: {2}</html>"},
        {"ClassTab.loadedClassesPlotter.accessibleName", "\u5DF2\u52A0\u8F7D\u7C7B\u7684\u56FE\u8868\u3002"},
        {"Classes","\u7C7B"},
        {"Close","\u5173\u95ED"},
        {"Column.Name", "\u540D\u79F0"},
        {"Column.PID", "PID"},
        {"Committed memory","\u63D0\u4EA4\u7684\u5185\u5B58"},
        {"Committed virtual memory","\u63D0\u4EA4\u7684\u865A\u62DF\u5185\u5B58"},
        {"Committed", "\u5DF2\u63D0\u4EA4"},
        {"Compiler","\u7F16\u8BD1\u5668"},
        {"CompositeData","CompositeData"},
        {"Config","\u914D\u7F6E"},
        {"Connect", "\u8FDE\u63A5(C)"},
        {"Connect.mnemonic", 'C'},
        {"Connect...","\u8FDE\u63A5..."},
        {"ConnectDialog.connectButton.toolTip", "\u8FDE\u63A5\u5230 Java \u865A\u62DF\u673A"},
        {"ConnectDialog.accessibleDescription", "\u7528\u4E8E\u4E0E\u672C\u5730\u6216\u8FDC\u7A0B Java \u865A\u62DF\u673A\u5EFA\u7ACB\u65B0\u8FDE\u63A5\u7684\u5BF9\u8BDD\u6846"},
        {"ConnectDialog.masthead.accessibleName", "\u62A5\u5934\u56FE"},
        {"ConnectDialog.masthead.title", "\u65B0\u5EFA\u8FDE\u63A5"},
        {"ConnectDialog.statusBar.accessibleName", "\u72B6\u6001\u680F"},
        {"ConnectDialog.title", "JConsole: \u65B0\u5EFA\u8FDE\u63A5"},
        {"Connected. Click to disconnect.","\u5DF2\u8FDE\u63A5\u3002\u5355\u51FB\u53EF\u65AD\u5F00\u8FDE\u63A5\u3002"},
        {"Connection failed","\u8FDE\u63A5\u5931\u8D25"},
        {"Connection", "\u8FDE\u63A5(C)"},
        {"Connection.mnemonic", 'C'},
        {"Connection name", "\u8FDE\u63A5\u540D\u79F0"},
        {"ConnectionName (disconnected)","{0} (\u5DF2\u65AD\u5F00\u8FDE\u63A5)"},
        {"Constructor","\u6784\u9020\u5668"},
        {"Current classes loaded", "\u5DF2\u52A0\u88C5\u5F53\u524D\u7C7B"},
        {"Current heap size","\u5F53\u524D\u5806\u5927\u5C0F"},
        {"Current value","\u5F53\u524D\u503C: {0}"},
        {"Create", "\u521B\u5EFA"},
        {"Daemon threads","\u5B88\u62A4\u7A0B\u5E8F\u7EBF\u7A0B"},
        {"Disconnected. Click to connect.","\u5DF2\u65AD\u5F00\u8FDE\u63A5\u3002\u5355\u51FB\u53EF\u8FDE\u63A5\u3002"},
        {"Double click to expand/collapse","\u53CC\u51FB\u4EE5\u5C55\u5F00/\u9690\u85CF"},
        {"Double click to visualize", "\u53CC\u51FB\u4EE5\u4F7F\u5176\u53EF\u89C1"},
        {"Description", "\u8BF4\u660E"},
        {"Description: ", "\u8BF4\u660E: "},
        {"Descriptor", "\u63CF\u8FF0\u7B26"},
        {"Details", "\u8BE6\u7EC6\u8D44\u6599"},
        {"Detect Deadlock", "\u68C0\u6D4B\u6B7B\u9501(D)"},
        {"Detect Deadlock.mnemonic", 'D'},
        {"Detect Deadlock.toolTip", "\u68C0\u6D4B\u5904\u4E8E\u6B7B\u9501\u72B6\u6001\u7684\u7EBF\u7A0B"},
        {"Dimension is not supported:","\u4E0D\u652F\u6301\u7EF4:"},
        {"Discard chart", "\u653E\u5F03\u56FE\u8868"},
        {"DurationDaysHoursMinutes","{0,choice,1#{0,number,integer} \u5929 |1.0<{0,number,integer} \u5929 }{1,choice,0<{1,number,integer} \u5C0F\u65F6 |1#{1,number,integer} \u5C0F\u65F6 |1<{1,number,integer} \u5C0F\u65F6 }{2,choice,0<{2,number,integer} \u5206\u949F|1#{2,number,integer} \u5206\u949F|1.0<{2,number,integer} \u5206\u949F}"},

        {"DurationHoursMinutes","{0,choice,1#{0,number,integer} \u5C0F\u65F6 |1<{0,number,integer} \u5C0F\u65F6 }{1,choice,0<{1,number,integer} \u5206\u949F|1#{1,number,integer} \u5206\u949F|1.0<{1,number,integer} \u5206\u949F}"},

        {"DurationMinutes","{0,choice,1#{0,number,integer} \u5206\u949F|1.0<{0,number,integer} \u5206\u949F}"},
        {"DurationSeconds","{0} \u79D2"},
        {"Empty array", "\u7A7A\u6570\u7EC4"},
        {"Empty opentype viewer", "\u7A7A opentype \u67E5\u770B\u5668"},
        {"Error","\u9519\u8BEF"},
        {"Error: MBeans already exist","\u9519\u8BEF: MBean \u5DF2\u5B58\u5728"},
        {"Error: MBeans do not exist","\u9519\u8BEF: MBean \u4E0D\u5B58\u5728"},
        {"Error:","\u9519\u8BEF:"},
        {"Event","\u4E8B\u4EF6"},
        {"Exit", "\u9000\u51FA(X)"},
        {"Exit.mnemonic", 'X'},
        {"Fail to load plugin", "\u8B66\u544A: \u65E0\u6CD5\u52A0\u8F7D\u63D2\u4EF6: {0}"},
        {"FileChooser.fileExists.cancelOption", "\u53D6\u6D88"},
        {"FileChooser.fileExists.message", "<html><center>\u6587\u4EF6\u5DF2\u5B58\u5728:<br>{0}<br>\u662F\u5426\u8981\u66FF\u6362?"},
        {"FileChooser.fileExists.okOption", "\u66FF\u6362"},
        {"FileChooser.fileExists.title", "\u6587\u4EF6\u5DF2\u5B58\u5728"},
        {"FileChooser.savedFile", "<html>\u5DF2\u4FDD\u5B58\u5230\u6587\u4EF6:<br>{0}<br>({1} \u5B57\u8282)"},
        {"FileChooser.saveFailed.message", "<html><center>\u672A\u80FD\u4FDD\u5B58\u5230\u6587\u4EF6:<br>{0}<br>{1}"},
        {"FileChooser.saveFailed.title", "\u4FDD\u5B58\u5931\u8D25"},
        {"Free physical memory","\u7A7A\u95F2\u7269\u7406\u5185\u5B58"},
        {"Free swap space","\u7A7A\u95F2\u4EA4\u6362\u7A7A\u95F4"},
        {"Garbage collector","\u5783\u573E\u6536\u96C6\u5668"},
        {"GTK","GTK"},
        {"GcInfo","\u540D\u79F0 = ''{0}'', \u6536\u96C6 = {1,choice,-1#Unavailable|0#{1,number,integer}}, \u603B\u82B1\u8D39\u65F6\u95F4 = {2}"},
        {"GC time","GC \u65F6\u95F4"},
        {"GC time details","{1}\u4E0A\u7684{0} ({2}\u6536\u96C6)"},
        {"Heap Memory Usage","\u5806\u5185\u5B58\u4F7F\u7528\u91CF"},
        {"Heap", "\u5806"},
        {"Help.AboutDialog.accessibleDescription", "\u5305\u542B\u6709\u5173 JConsole \u548C JDK \u7248\u672C\u4FE1\u606F\u7684\u5BF9\u8BDD\u6846"},
        {"Help.AboutDialog.jConsoleVersion", "JConsole \u7248\u672C:<br>{0}"},
        {"Help.AboutDialog.javaVersion", "Java VM \u7248\u672C:<br>{0}"},
        {"Help.AboutDialog.masthead.accessibleName", "\u62A5\u5934\u56FE"},
        {"Help.AboutDialog.masthead.title", "\u5173\u4E8E JConsole"},
        {"Help.AboutDialog.title", "JConsole: \u5173\u4E8E"},
        {"Help.AboutDialog.userGuideLink", "JConsole \u7528\u6237\u6307\u5357(U):<br>{0}"},
        {"Help.AboutDialog.userGuideLink.mnemonic", 'U'},
        {"Help.AboutDialog.userGuideLink.url", "http://java.sun.com/javase/6/docs/technotes/guides/management/jconsole.html"},
        {"HelpMenu.About.title", "\u5173\u4E8E JConsole(A)"},
        {"HelpMenu.About.title.mnemonic", 'A'},
        {"HelpMenu.UserGuide.title", "\u8054\u673A\u7528\u6237\u6307\u5357(U)"},
        {"HelpMenu.UserGuide.title.mnemonic", 'U'},
        {"HelpMenu.title", "\u5E2E\u52A9(H)"},
        {"HelpMenu.title.mnemonic", 'H'},
        {"Hotspot MBeans...", "HotSpot MBean(H)..."},
        {"Hotspot MBeans....mnemonic", 'H'},
        {"Hotspot MBeans.dialog.accessibleDescription", "\u7528\u4E8E\u7BA1\u7406 HotSpot MBean \u7684\u5BF9\u8BDD\u6846"},
        {"Impact","\u5F71\u54CD"},
        {"Info","\u4FE1\u606F"},
        {"INFO","INFO"},
        {"Invalid plugin path", "\u8B66\u544A: \u63D2\u4EF6\u8DEF\u5F84\u65E0\u6548: {0}"},
        {"Invalid URL", "URL \u65E0\u6548: {0}"},
        {"Is","\u662F"},
        {"Java Monitoring & Management Console", "Java \u76D1\u89C6\u548C\u7BA1\u7406\u63A7\u5236\u53F0"},
        {"JConsole: ","JConsole: {0}"},
        {"JConsole version","JConsole \u7248\u672C \"{0}\""},
        {"JConsole.accessibleDescription", "Java \u76D1\u89C6\u548C\u7BA1\u7406\u63A7\u5236\u53F0"},
        {"JIT compiler","JIT \u7F16\u8BD1\u5668"},
        {"Java Virtual Machine","Java \u865A\u62DF\u673A"},
        {"Java","Java"},
        {"Library path","\u5E93\u8DEF\u5F84"},
        {"Listeners","\u76D1\u542C\u7A0B\u5E8F"},
        {"Live Threads","\u6D3B\u52A8\u7EBF\u7A0B"},
        {"Loaded", "\u5DF2\u52A0\u8F7D"},
        {"Local Process:", "\u672C\u5730\u8FDB\u7A0B(L):"},
        {"Local Process:.mnemonic", 'L'},
        {"Look and Feel","\u5916\u89C2"},
        {"Masthead.font", "Dialog-PLAIN-25"},
        {"Management Not Enabled","<b>\u6CE8</b>: \u672A\u5BF9\u6B64\u8FDB\u7A0B\u542F\u7528\u7BA1\u7406\u4EE3\u7406\u3002"},
        {"Management Will Be Enabled","<b>\u6CE8</b>: \u5C06\u5BF9\u6B64\u8FDB\u7A0B\u542F\u7528\u7BA1\u7406\u4EE3\u7406\u3002"},
        {"MBeanAttributeInfo","MBeanAttributeInfo"},
        {"MBeanInfo","MBeanInfo"},
        {"MBeanNotificationInfo","MBeanNotificationInfo"},
        {"MBeanOperationInfo","MBeanOperationInfo"},
        {"MBeans","MBean"},
        {"MBeansTab.clearNotificationsButton", "\u6E05\u9664(C)"},
        {"MBeansTab.clearNotificationsButton.mnemonic", 'C'},
        {"MBeansTab.clearNotificationsButton.toolTip", "\u6E05\u9664\u901A\u77E5"},
        {"MBeansTab.compositeNavigationMultiple", "\u7EC4\u5408\u5BFC\u822A{0}/{1}"},
        {"MBeansTab.compositeNavigationSingle", "\u7EC4\u5408\u5BFC\u822A"},
        {"MBeansTab.refreshAttributesButton", "\u5237\u65B0(R)"},
        {"MBeansTab.refreshAttributesButton.mnemonic", 'R'},
        {"MBeansTab.refreshAttributesButton.toolTip", "\u5237\u65B0\u5C5E\u6027"},
        {"MBeansTab.subscribeNotificationsButton", "\u8BA2\u9605(S)"},
        {"MBeansTab.subscribeNotificationsButton.mnemonic", 'S'},
        {"MBeansTab.subscribeNotificationsButton.toolTip", "\u5F00\u59CB\u76D1\u542C\u901A\u77E5"},
        {"MBeansTab.tabularNavigationMultiple", "\u8868\u683C\u5F0F\u5BFC\u822A{0}/{1}"},
        {"MBeansTab.tabularNavigationSingle", "\u8868\u683C\u5F0F\u5BFC\u822A"},
        {"MBeansTab.unsubscribeNotificationsButton", "\u53D6\u6D88\u8BA2\u9605(U)"},
        {"MBeansTab.unsubscribeNotificationsButton.mnemonic", 'U'},
        {"MBeansTab.unsubscribeNotificationsButton.toolTip", "\u505C\u6B62\u76D1\u542C\u901A\u77E5"},
        {"Manage Hotspot MBeans in: ", "\u7BA1\u7406\u4EE5\u4E0B\u4F4D\u7F6E\u7684 HotSpot MBean: "},
        {"Max","\u6700\u5927\u503C"},
        {"Maximum heap size","\u6700\u5927\u5806\u5927\u5C0F"},
        {"Memory","\u5185\u5B58"},
        {"MemoryPoolLabel", "\u5185\u5B58\u6C60 \"{0}\""},
        {"MemoryTab.heapPlotter.accessibleName", "\u5806\u7684\u5185\u5B58\u4F7F\u7528\u91CF\u56FE\u8868\u3002"},
        {"MemoryTab.infoLabelFormat", "<html>\u5DF2\u7528: {0}    \u5DF2\u63D0\u4EA4: {1}    \u6700\u5927: {2}</html>"},
        {"MemoryTab.nonHeapPlotter.accessibleName", "\u975E\u5806\u7684\u5185\u5B58\u4F7F\u7528\u91CF\u56FE\u8868\u3002"},
        {"MemoryTab.poolChart.aboveThreshold", "\u5927\u4E8E{0}\u7684\u9608\u503C\u3002\n"},
        {"MemoryTab.poolChart.accessibleName", "\u5185\u5B58\u6C60\u4F7F\u7528\u91CF\u56FE\u8868\u3002"},
        {"MemoryTab.poolChart.belowThreshold", "\u4F4E\u4E8E{0}\u7684\u9608\u503C\u3002\n"},
        {"MemoryTab.poolPlotter.accessibleName", "{0}\u7684\u5185\u5B58\u4F7F\u7528\u91CF\u56FE\u8868\u3002"},
        {"Message","\u6D88\u606F"},
        {"Method successfully invoked", "\u5DF2\u6210\u529F\u8C03\u7528\u65B9\u6CD5"},
        {"Minimize All", "\u5168\u90E8\u6700\u5C0F\u5316(M)"},
        {"Minimize All.mnemonic", 'M'},
        {"Minus Version", "\u8FD9\u662F{0}\u7248\u672C {1}"},
        {"Monitor locked",
             "   - \u5DF2\u9501\u5B9A{0}\n"},
        {"Motif","Motif"},
        {"Name Build and Mode","{0} (\u5DE5\u4F5C\u7248\u672C {1}, {2})"},
        {"Name and Build","{0} (\u5DE5\u4F5C\u7248\u672C {1})"},
        {"Name","\u540D\u79F0"},
        {"Name: ","\u540D\u79F0: "},
        {"Name State",
             "\u540D\u79F0: {0}\n\u72B6\u6001: {1}\n"},
        {"Name State LockName",
             "\u540D\u79F0: {0}\n\u72B6\u6001: {2}\u4E0A\u7684{1}\n"},
        {"Name State LockName LockOwner",
             "\u540D\u79F0: {0}\n\u72B6\u6001: {2}\u4E0A\u7684{1}, \u62E5\u6709\u8005: {3}\n"},
        {"New Connection...", "\u65B0\u5EFA\u8FDE\u63A5(N)..."},
        {"New Connection....mnemonic", 'N'},
        {"New value applied","\u5DF2\u5E94\u7528\u65B0\u503C"},
        {"No attribute selected","\u672A\u9009\u62E9\u5C5E\u6027"},
        {"No deadlock detected","\u672A\u68C0\u6D4B\u5230\u6B7B\u9501"},
        {"No value selected","\u672A\u9009\u62E9\u503C"},
        {"Non-Heap Memory Usage","\u975E\u5806\u5185\u5B58\u4F7F\u7528\u91CF"},
        {"Non-Heap", "\u975E\u5806"},
        {"Not Yet Implemented","\u5C1A\u672A\u5B9E\u73B0"},
        {"Not a valid event broadcaster", "\u4E0D\u662F\u6709\u6548\u7684\u4E8B\u4EF6\u5E7F\u64AD\u8005"},
        {"Notification","\u901A\u77E5"},
        {"Notification buffer","\u901A\u77E5\u7F13\u51B2\u533A"},
        {"Notifications","\u901A\u77E5"},
        {"NotifTypes", "NotifTypes"},
        {"Number of Threads","\u7EBF\u7A0B\u6570"},
        {"Number of Loaded Classes","\u5DF2\u52A0\u8F7D\u7C7B\u6570"},
        {"Number of processors","\u5904\u7406\u7A0B\u5E8F\u6570"},
        {"ObjectName","ObjectName"},
        {"Operating System","\u64CD\u4F5C\u7CFB\u7EDF"},
        {"Operation","\u64CD\u4F5C"},
        {"Operation invocation","\u64CD\u4F5C\u8C03\u7528"},
        {"Operation return value", "\u64CD\u4F5C\u8FD4\u56DE\u503C"},
        {"Operations","\u64CD\u4F5C"},
        {"Overview","\u6982\u89C8"},
        {"OverviewPanel.plotter.accessibleName", "{0}\u7684\u56FE\u8868\u3002"},
        {"Parameter", "\u53C2\u6570"},
        {"Password: ", "\u53E3\u4EE4(P): "},
        {"Password: .mnemonic", 'P'},
        {"Password.accessibleName", "\u53E3\u4EE4"},
        {"Peak","\u5CF0\u503C"},
        {"Perform GC", "\u6267\u884C GC(G)"},
        {"Perform GC.mnemonic", 'G'},
        {"Perform GC.toolTip", "\u8BF7\u6C42\u5783\u573E\u6536\u96C6"},
        {"Plotter.accessibleName", "\u56FE\u8868"},
        {"Plotter.accessibleName.keyAndValue", "{0}={1}\n"},
        {"Plotter.accessibleName.noData", "\u672A\u7ED8\u5236\u6570\u636E\u3002"},
        {"Plotter.saveAsMenuItem", "\u5C06\u6570\u636E\u53E6\u5B58\u4E3A(A)..."},
        {"Plotter.saveAsMenuItem.mnemonic", 'A'},
        {"Plotter.timeRangeMenu", "\u65F6\u95F4\u8303\u56F4(T)"},
        {"Plotter.timeRangeMenu.mnemonic", 'T'},
        {"Problem adding listener","\u6DFB\u52A0\u76D1\u542C\u7A0B\u5E8F\u65F6\u51FA\u73B0\u95EE\u9898"},
        {"Problem displaying MBean", "\u663E\u793A MBean \u65F6\u51FA\u73B0\u95EE\u9898"},
        {"Problem invoking", "\u8C03\u7528\u65F6\u51FA\u73B0\u95EE\u9898"},
        {"Problem removing listener","\u5220\u9664\u76D1\u542C\u7A0B\u5E8F\u65F6\u51FA\u73B0\u95EE\u9898"},
        {"Problem setting attribute","\u8BBE\u7F6E\u5C5E\u6027\u65F6\u51FA\u73B0\u95EE\u9898"},
        {"Process CPU time","\u8FDB\u7A0B CPU \u65F6\u95F4"},
        {"R/W","\u8BFB\u5199"},
        {"Readable","\u53EF\u8BFB"},
        {"Received","\u6536\u5230"},
        {"Reconnect","\u91CD\u65B0\u8FDE\u63A5"},
        {"Remote Process:", "\u8FDC\u7A0B\u8FDB\u7A0B(R):"},
        {"Remote Process:.mnemonic", 'R'},
        {"Remote Process.textField.accessibleName", "\u8FDC\u7A0B\u8FDB\u7A0B"},
        {"Remove","\u5220\u9664"},
        {"Restore All", "\u5168\u90E8\u8FD8\u539F(R)"},
        {"Restore All.mnemonic", 'R'},
        {"Return value", "\u8FD4\u56DE\u503C"},
        {"ReturnType", "ReturnType"},
        {"SeqNum","SeqNum"},
        {"Size Bytes", "{0,number,integer} \u5B57\u8282"},
        {"Size Gb","{0} GB"},
        {"Size Kb","{0} KB"},
        {"Size Mb","{0} MB"},
        {"Source","\u6E90"},
        {"Stack trace",
              "\n\u5806\u6808\u8DDF\u8E2A: \n"},
        {"Success:","\u6210\u529F:"},
        // Note: SummaryTab.headerDateTimeFormat can be one the following:
        // 1. A combination of two styles for date and time, using the
        //    constants from class DateFormat: SHORT, MEDIUM, LONG, FULL.
        //    Example: "MEDIUM,MEDIUM" or "FULL,SHORT"
        // 2. An explicit string pattern used for creating an instance
        //    of the class SimpleDateFormat.
        //    Example: "yyyy-MM-dd HH:mm:ss" or "M/d/yyyy h:mm:ss a"
        {"SummaryTab.headerDateTimeFormat", "FULL,FULL"},
        {"SummaryTab.pendingFinalization.label", "\u6682\u6302\u6700\u7EC8\u5904\u7406"},
        {"SummaryTab.pendingFinalization.value", "{0}\u5BF9\u8C61"},
        {"SummaryTab.tabName", "VM \u6982\u8981"},
        {"SummaryTab.vmVersion","{0}\u7248\u672C {1}"},
        {"TabularData are not supported", "\u4E0D\u652F\u6301 TabularData"},
        {"Threads","\u7EBF\u7A0B"},
        {"ThreadTab.infoLabelFormat", "<html>\u6D3B\u52A8: {0}    \u5CF0\u503C: {1}    \u603B\u8BA1: {2}</html>"},
        {"ThreadTab.threadInfo.accessibleName", "\u7EBF\u7A0B\u4FE1\u606F"},
        {"ThreadTab.threadPlotter.accessibleName", "\u8868\u793A\u7EBF\u7A0B\u6570\u7684\u56FE\u8868\u3002"},
        {"Threshold","\u9608\u503C"},
        {"Tile", "\u5E73\u94FA(T)"},
        {"Tile.mnemonic", 'T'},
        {"Time Range:", "\u65F6\u95F4\u8303\u56F4(T):"},
        {"Time Range:.mnemonic", 'T'},
        {"Time", "\u65F6\u95F4"},
        {"TimeStamp","TimeStamp"},
        {"Total Loaded", "\u52A0\u8F7D\u603B\u6570"},
        {"Total classes loaded","\u5DF2\u52A0\u8F7D\u7C7B\u603B\u6570"},
        {"Total classes unloaded","\u5DF2\u5378\u8F7D\u7C7B\u603B\u6570"},
        {"Total compile time","\u603B\u7F16\u8BD1\u65F6\u95F4"},
        {"Total physical memory","\u603B\u7269\u7406\u5185\u5B58"},
        {"Total threads started","\u542F\u52A8\u7684\u7EBF\u7A0B\u603B\u6570"},
        {"Total swap space","\u603B\u4EA4\u6362\u7A7A\u95F4"},
        {"Type","\u7C7B\u578B"},
        {"Unavailable","\u4E0D\u53EF\u7528"},
        {"UNKNOWN","UNKNOWN"},
        {"Unknown Host","\u672A\u77E5\u4E3B\u673A: {0}"},
        {"Unregister", "\u6CE8\u9500"},
        {"Uptime","\u8FD0\u884C\u65F6\u95F4"},
        {"Uptime: ","\u8FD0\u884C\u65F6\u95F4: "},
        {"Usage Threshold","\u7528\u6CD5\u9608\u503C"},
        {"remoteTF.usage","<b>\u7528\u6CD5</b>: &lt;hostname&gt;:&lt;port&gt; \u6216 service:jmx:&lt;protocol&gt;:&lt;sap&gt;"},
        {"Used","\u5DF2\u7528"},
        {"Username: ", "\u7528\u6237\u540D(U): "},
        {"Username: .mnemonic", 'U'},
        {"Username.accessibleName", "\u7528\u6237\u540D"},
        {"UserData","UserData"},
        {"Virtual Machine","\u865A\u62DF\u673A"},
        {"VM arguments","VM \u53C2\u6570"},
        {"VM","VM"},
        {"VMInternalFrame.accessibleDescription", "\u7528\u4E8E\u76D1\u89C6 Java \u865A\u62DF\u673A\u7684\u5185\u90E8\u6846\u67B6"},
        {"Value","\u503C"},
        {"Vendor", "\u5382\u5546"},
        {"Verbose Output","\u8BE6\u7EC6\u8F93\u51FA"},
        {"Verbose Output.toolTip", "\u4E3A\u7C7B\u52A0\u8F7D\u7CFB\u7EDF\u542F\u7528\u8BE6\u7EC6\u8F93\u51FA"},
        {"View value", "\u89C6\u56FE\u503C"},
        {"View","\u89C6\u56FE"},
        {"Window", "\u7A97\u53E3(W)"},
        {"Window.mnemonic", 'W'},
        {"Windows","Windows"},
        {"Writable","\u53EF\u5199"},
        {"You cannot drop a class here", "\u65E0\u6CD5\u5220\u9664\u6B64\u5904\u7684\u7C7B"},
        {"collapse", "\u9690\u85CF"},
        {"connectionFailed1","\u8FDE\u63A5\u5931\u8D25: \u662F\u5426\u91CD\u8BD5?"},
        {"connectionFailed2","\u672A\u6210\u529F\u8FDE\u63A5\u5230{0}\u3002<br>\u662F\u5426\u8981\u91CD\u8BD5?"},
        {"connectionLost1","\u8FDE\u63A5\u4E22\u5931: \u662F\u5426\u91CD\u65B0\u8FDE\u63A5?"},
        {"connectionLost2","\u7531\u4E8E\u8FDC\u7A0B\u8FDB\u7A0B\u5DF2\u7EC8\u6B62, \u4E0E{0}\u7684\u8FDE\u63A5\u4E22\u5931\u3002<br>\u662F\u5426\u8981\u91CD\u65B0\u8FDE\u63A5?"},
        {"connectingTo1","\u6B63\u5728\u8FDE\u63A5\u5230{0}"},
        {"connectingTo2","\u60A8\u5F53\u524D\u6B63\u5728\u8FDE\u63A5\u5230{0}\u3002<br>\u8FD9\u5C06\u9700\u8981\u51E0\u5206\u949F\u7684\u65F6\u95F4\u3002"},
        {"deadlockAllTab","\u5168\u90E8"},
        {"deadlockTab","\u6B7B\u9501"},
        {"deadlockTabN","\u6B7B\u9501{0}"},
        {"expand", "\u5C55\u5F00"},
        {"kbytes","{0} KB"},
        {"operation","\u64CD\u4F5C"},
        {"plot", "\u7ED8\u56FE"},
        {"visualize","\u53EF\u89C6\u5316"},
        {"zz usage text",
             "\u7528\u6CD5: {0} [ -interval=n ] [ -notile ] [ -pluginpath <path> ] [ -version ] [ connection ... ]\n\n  -interval   \u5C06\u66F4\u65B0\u95F4\u9694\u8BBE\u7F6E\u4E3A n \u79D2 (\u9ED8\u8BA4\u503C\u4E3A 4 \u79D2)\n  -notile     \u521D\u59CB\u4E0D\u5E73\u94FA\u7A97\u53E3 (\u5BF9\u4E8E\u4E24\u4E2A\u6216\u591A\u4E2A\u8FDE\u63A5)\n  -pluginpath \u6307\u5B9A jconsole \u7528\u4E8E\u67E5\u627E\u63D2\u4EF6\u7684\u8DEF\u5F84\n  -version    \u8F93\u51FA\u7A0B\u5E8F\u7248\u672C\n\n  connection = pid || host:port || JMX URL (service:jmx:<\u534F\u8BAE>://...)\n  pid         \u76EE\u6807\u8FDB\u7A0B\u7684\u8FDB\u7A0B ID\n  host        \u8FDC\u7A0B\u4E3B\u673A\u540D\u6216 IP \u5730\u5740\n  port        \u8FDC\u7A0B\u8FDE\u63A5\u7684\u7AEF\u53E3\u53F7\n\n  -J          \u6307\u5B9A\u8FD0\u884C jconsole \u7684 Java \u865A\u62DF\u673A\n              \u7684\u8F93\u5165\u53C2\u6570"},
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
