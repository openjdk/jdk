/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
public class JConsoleResources_ja extends JConsoleResources {

    private static final String cr = System.getProperty("line.separator");

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    protected Object[][] getContents0() {
        return new Object[][] {
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
        {" 1 day"," 1 \u65e5"},
        {" 1 hour"," 1 \u6642\u9593"},
        {" 1 min"," 1 \u5206"},
        {" 1 month"," 1 \u304b\u6708"},
        {" 1 year"," 1 \u5e74"},
        {" 2 hours"," 2 \u6642\u9593"},
        {" 3 hours"," 3 \u6642\u9593"},
        {" 3 months"," 3 \u304b\u6708"},
        {" 5 min"," 5 \u5206"},
        {" 6 hours"," 6 \u6642\u9593"},
        {" 6 months"," 6 \u304b\u6708"},
        {" 7 days"," 7 \u65e5"},
        {"10 min","10 \u5206"},
        {"12 hours","12 \u6642\u9593"},
        {"30 min","30 \u5206"},
        {"<","<"},
        {"<<","<<"},
        {">",">"},
        {"ACTION","ACTION"},
        {"ACTION_INFO","ACTION_INFO"},
        {"All","\u3059\u3079\u3066"},
        {"Apply","\u9069\u7528"},
        {"Architecture","\u30a2\u30fc\u30ad\u30c6\u30af\u30c1\u30e3"},
        {"Array, OpenType", "\u914d\u5217\u3001OpenType"},
        {"Array, OpenType, Numeric value viewer","\u914d\u5217\u3001OpenType\u3001\u6570\u5024\u306e\u30d3\u30e5\u30fc\u30a2"},
        {"Attribute","\u5c5e\u6027"},
        {"Attribute value","\u5c5e\u6027\u5024"},
        {"Attribute values","\u5c5e\u6027\u5024"},
        {"Attributes","\u5c5e\u6027"},
        {"Blank", "\u30d6\u30e9\u30f3\u30af"},
        {"BlockedCount WaitedCount",
             "\u7dcf\u30d6\u30ed\u30c3\u30af\u6570 : {0}  \u7dcf\u5f85\u6a5f\u6570 : {1}" + cr},
        {"Boot class path","\u30d6\u30fc\u30c8\u30af\u30e9\u30b9\u30d1\u30b9"},
        {"BorderedComponent.moreOrLessButton.toolTip", "\u8a73\u7d30\u60c5\u5831\u3092\u8868\u793a\u3059\u308b\u304b\u3069\u3046\u304b\u5207\u308a\u66ff\u3048\u308b"},
        {"CPU Usage","CPU \u4f7f\u7528\u72b6\u6cc1"},
        {"CPUUsageFormat","CPU \u4f7f\u7528\u72b6\u6cc1: {0}%"},
        {"Cancel","\u53d6\u6d88\u3057"},
        {"Cascade", "\u91cd\u306d\u3066\u8868\u793a(C)"},
        {"Cascade.mnemonic", 'C'},
        {"Chart:", "\u56f3(C):"},
        {"Chart:.mnemonic", 'C'},
        {"Class path","\u30af\u30e9\u30b9\u30d1\u30b9"},
        {"Class","\u30af\u30e9\u30b9"},
        {"ClassName","ClassName"},
        {"ClassTab.infoLabelFormat", "<html>\u30ed\u30fc\u30c9: {0}    \u30a2\u30f3\u30ed\u30fc\u30c9: {1}    \u7dcf\u6570: {2}</html>"},
        {"ClassTab.loadedClassesPlotter.accessibleName", "\u30ed\u30fc\u30c9\u3055\u308c\u305f\u30af\u30e9\u30b9\u306e\u56f3\u3002"},
        {"Classes","\u30af\u30e9\u30b9"},
        {"Close","\u9589\u3058\u308b"},
        {"Column.Name", "\u540d\u524d"},
        {"Column.PID", "PID"},
        {"Committed memory","\u78ba\u5b9a\u30e1\u30e2\u30ea"},
        {"Committed virtual memory","\u78ba\u5b9a\u4eee\u60f3\u30e1\u30e2\u30ea"},
        {"Committed", "\u78ba\u5b9a"},
        {"Compiler","\u30b3\u30f3\u30d1\u30a4\u30e9"},
        {"CompositeData","CompositeData"},
        {"Config","\u69cb\u6210"},
        {"Connect", "\u63a5\u7d9a(C)"},
        {"Connect.mnemonic", 'C'},
        {"Connect...","\u63a5\u7d9a..."},
        {"ConnectDialog.connectButton.toolTip", "Java \u4eee\u60f3\u30de\u30b7\u30f3\u306b\u63a5\u7d9a\u3059\u308b"},
        {"ConnectDialog.accessibleDescription", "\u30ed\u30fc\u30ab\u30eb\u307e\u305f\u306f\u30ea\u30e2\u30fc\u30c8\u306e Java \u4eee\u60f3\u30de\u30b7\u30f3\u306b\u65b0\u898f\u63a5\u7d9a\u3059\u308b\u305f\u3081\u306e\u30c0\u30a4\u30a2\u30ed\u30b0"},
        {"ConnectDialog.masthead.accessibleName", "\u30de\u30b9\u30c8\u30d8\u30c3\u30c9\u306e\u30b0\u30e9\u30d5\u30a3\u30c3\u30af"},
        {"ConnectDialog.masthead.title", "\u65b0\u898f\u63a5\u7d9a"},
        {"ConnectDialog.statusBar.accessibleName", "\u30b9\u30c6\u30fc\u30bf\u30b9\u30d0\u30fc"},
        {"ConnectDialog.title", "JConsole: \u65b0\u898f\u63a5\u7d9a"},
        {"Connected. Click to disconnect.","\u63a5\u7d9a\u3055\u308c\u307e\u3057\u305f\u3002\u5207\u65ad\u3059\u308b\u306b\u306f\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Connection failed","\u63a5\u7d9a\u306b\u5931\u6557\u3057\u307e\u3057\u305f"},
        {"Connection", "\u63a5\u7d9a(C)"},
        {"Connection.mnemonic", 'C'},
        {"Connection name", "\u63a5\u7d9a\u540d"},
        {"ConnectionName (disconnected)","{0} (\u63a5\u7d9a\u89e3\u9664)"},
        {"Constructor","\u30b3\u30f3\u30b9\u30c8\u30e9\u30af\u30bf"},
        {"Current classes loaded", "\u73fe\u5728\u30ed\u30fc\u30c9\u3055\u308c\u3066\u3044\u308b\u30af\u30e9\u30b9"},
        {"Current heap size","\u73fe\u5728\u306e\u30d2\u30fc\u30d7\u30b5\u30a4\u30ba"},
        {"Current value","\u73fe\u5728\u306e\u5024: {0}"},
        {"Create", "\u4f5c\u6210"},
        {"Daemon threads","\u30c7\u30fc\u30e2\u30f3\u30b9\u30ec\u30c3\u30c9"},
        {"Disconnected. Click to connect.","\u5207\u65ad\u3055\u308c\u307e\u3057\u305f\u3002\u63a5\u7d9a\u3059\u308b\u306b\u306f\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Double click to expand/collapse","\u30c0\u30d6\u30eb\u30af\u30ea\u30c3\u30af\u3057\u3066\u5c55\u958b/\u6298\u308a\u305f\u305f\u307f"},
        {"Double click to visualize", "\u30c0\u30d6\u30eb\u30af\u30ea\u30c3\u30af\u3057\u3066\u8868\u793a"},
        {"Description", "\u8aac\u660e"},
        {"Description: ", "\u8aac\u660e: "},
        {"Descriptor", "\u8a18\u8ff0\u5b50"},
        {"Details", "\u8a73\u7d30"},
        {"Detect Deadlock", "\u30c7\u30c3\u30c9\u30ed\u30c3\u30af\u3092\u691c\u51fa\u3059\u308b(D)"},
        {"Detect Deadlock.mnemonic", 'D'},
        {"Detect Deadlock.toolTip", "\u30c7\u30c3\u30c9\u30ed\u30c3\u30af\u3057\u305f\u30b9\u30ec\u30c3\u30c9\u3092\u691c\u51fa\u3059\u308b"},
        {"Dimension is not supported:","\u5927\u304d\u3055\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093:"},
        {"Discard chart", "\u56f3\u3092\u7834\u68c4\u3059\u308b"},
        {"DurationDaysHoursMinutes","{0,choice,1#{0,number,integer} \u65e5 |1.0<{0,number,integer} \u65e5 }{1,choice,0<{1,number,integer} \u6642\u9593 |1#{1,number,integer} \u6642\u9593 |1<{1,number,integer} \u6642\u9593 }{2,choice,0<{2,number,integer} \u5206 |1#{2,number,integer} \u5206 |1.0<{2,number,integer} \u5206}"},

        {"DurationHoursMinutes","{0,choice,1#{0,number,integer} \u6642\u9593 |1<{0,number,integer} \u6642\u9593 }{1,choice,0<{1,number,integer} \u5206 |1#{1,number,integer} \u5206 |1.0<{1,number,integer} \u5206}"},

        {"DurationMinutes","{0,choice,1#{0,number,integer} \u5206 |1.0<{0,number,integer} \u5206}"},
        {"DurationSeconds","{0} \u79d2"},
        {"Empty array", "\u914d\u5217\u3092\u7a7a\u306b\u3059\u308b"},
        {"Empty opentype viewer", "OpenType \u30d3\u30e5\u30fc\u30a2\u3092\u7a7a\u306b\u3059\u308b"},
        {"Error","\u30a8\u30e9\u30fc"},
        {"Error: MBeans already exist","\u30a8\u30e9\u30fc : MBean \u306f\u3059\u3067\u306b\u5b58\u5728\u3057\u307e\u3059"},
        {"Error: MBeans do not exist","\u30a8\u30e9\u30fc : MBean \u306f\u5b58\u5728\u3057\u307e\u305b\u3093"},
        {"Error:","\u30a8\u30e9\u30fc:"},
        {"Event","\u30a4\u30d9\u30f3\u30c8"},
        {"Exit", "\u7d42\u4e86(X)"},
        {"Exit.mnemonic", 'x'},
        {"Fail to load plugin", "\u8b66\u544a: \u30d7\u30e9\u30b0\u30a4\u30f3\u306e\u30ed\u30fc\u30c9\u306b\u5931\u6557\u3057\u307e\u3057\u305f: {0}"},
        {"FileChooser.fileExists.cancelOption", "\u30ad\u30e3\u30f3\u30bb\u30eb"},
        {"FileChooser.fileExists.message", "<html><center>\u30d5\u30a1\u30a4\u30eb\u306f\u3059\u3067\u306b\u5b58\u5728\u3057\u3066\u3044\u307e\u3059:<br>{0}<br>\u7f6e\u63db\u3057\u3066\u3082\u3088\u308d\u3057\u3044\u3067\u3059\u304b?"},
        {"FileChooser.fileExists.okOption", "\u7f6e\u63db"},
        {"FileChooser.fileExists.title", "\u30d5\u30a1\u30a4\u30eb\u304c\u5b58\u5728\u3057\u3066\u3044\u307e\u3059"},
        {"FileChooser.savedFile", "<html>\u30d5\u30a1\u30a4\u30eb\u306b\u4fdd\u5b58\u3057\u307e\u3057\u305f:<br>{0}<br>({1} \u30d0\u30a4\u30c8)"},
        {"FileChooser.saveFailed.message", "<html><center>\u30d5\u30a1\u30a4\u30eb\u3078\u306e\u4fdd\u5b58\u306b\u5931\u6557\u3057\u307e\u3057\u305f:<br>{0}<br>{1}"},
        {"FileChooser.saveFailed.title", "\u4fdd\u5b58\u306b\u5931\u6557\u3057\u307e\u3057\u305f"},
        {"Free physical memory","\u7a7a\u304d\u7269\u7406\u30e1\u30e2\u30ea"},
        {"Free swap space","\u7a7a\u304d\u30b9\u30ef\u30c3\u30d7\u30b9\u30da\u30fc\u30b9"},
        {"Garbage collector","\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30bf"},
        {"GTK","GTK"},
        {"GcInfo","\u540d\u524d = ''{0}'', \u30b3\u30ec\u30af\u30b7\u30e7\u30f3 = {1,choice,-1#\u5229\u7528\u4e0d\u53ef|0#{1,number,integer}}, \u7dcf\u7d4c\u904e\u6642\u9593 = {2}"},
        {"GC time","GC \u6642\u9593"},
        {"GC time details","{1} \u306e {0} ({2} \u30b3\u30ec\u30af\u30b7\u30e7\u30f3)"},
        {"Heap Memory Usage","\u30d2\u30fc\u30d7\u30e1\u30e2\u30ea\u306e\u4f7f\u7528\u72b6\u6cc1"},
        {"Heap", "\u30d2\u30fc\u30d7"},
        {"Help.AboutDialog.accessibleDescription", "JConsole \u304a\u3088\u3073 JDK \u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u60c5\u5831\u3092\u542b\u3080\u30c0\u30a4\u30a2\u30ed\u30b0"},
        {"Help.AboutDialog.jConsoleVersion", "JConsole \u30d0\u30fc\u30b8\u30e7\u30f3:<br>{0}"},
        {"Help.AboutDialog.javaVersion", "Java VM \u30d0\u30fc\u30b8\u30e7\u30f3:<br>{0}"},
        {"Help.AboutDialog.masthead.accessibleName", "\u30de\u30b9\u30c8\u30d8\u30c3\u30c9\u306e\u30b0\u30e9\u30d5\u30a3\u30c3\u30af"},
        {"Help.AboutDialog.masthead.title", "JConsole \u306b\u3064\u3044\u3066"},
        {"Help.AboutDialog.title", "JConsole: \u88fd\u54c1\u60c5\u5831"},
        {"Help.AboutDialog.userGuideLink", "JConsole \u30e6\u30fc\u30b6\u30fc\u30ac\u30a4\u30c9:<br>{0}"},
        {"Help.AboutDialog.userGuideLink.mnemonic", 'U'},
        {"Help.AboutDialog.userGuideLink.url", "http://java.sun.com/javase/6/docs/technotes/guides/management/MonitoringGuide/toc.html"},
        {"HelpMenu.About.title", "JConsole \u306b\u3064\u3044\u3066(A)"},
        {"HelpMenu.About.title.mnemonic", 'A'},
        {"HelpMenu.UserGuide.title", "\u30aa\u30f3\u30e9\u30a4\u30f3\u30e6\u30fc\u30b6\u30fc\u30ac\u30a4\u30c9(U)"},
        {"HelpMenu.UserGuide.title.mnemonic", 'U'},
        {"HelpMenu.title", "\u30d8\u30eb\u30d7(H)"},
        {"HelpMenu.title.mnemonic", 'H'},
        {"Hotspot MBeans...", "Hotspot MBean..."},
        {"Hotspot MBeans....mnemonic", 'H'},
        {"Hotspot MBeans.dialog.accessibleDescription", "Hotspot MBean \u3092\u7ba1\u7406\u3059\u308b\u305f\u3081\u306e\u30c0\u30a4\u30a2\u30ed\u30b0"},
        {"Impact","\u5f71\u97ff"},
        {"Info","\u60c5\u5831"},
        {"INFO","\u60c5\u5831"},
        {"Invalid plugin path", "\u8b66\u544a: \u30d7\u30e9\u30b0\u30a4\u30f3\u30d1\u30b9\u304c\u7121\u52b9\u3067\u3059: {0}"},
        {"Invalid URL", "\u7121\u52b9\u306a URL: {0}"},
        {"Is","Is"},
        {"Java Monitoring & Management Console", "Java Monitoring & Management Console"},
        {"JConsole: ","JConsole: {0}"},
        {"JConsole version","JConsole \u30d0\u30fc\u30b8\u30e7\u30f3 \"{0}\""},
        {"JConsole.accessibleDescription", "Java Monitoring & Management Console"},
        {"JIT compiler","JIT \u30b3\u30f3\u30d1\u30a4\u30e9"},
        {"Java Virtual Machine","Java \u4eee\u60f3\u30de\u30b7\u30f3"},
        {"Java","Java"},
        {"Library path","\u30e9\u30a4\u30d6\u30e9\u30ea\u30d1\u30b9"},
        {"Listeners","\u30ea\u30b9\u30ca\u30fc"},
        {"Live Threads","\u30e9\u30a4\u30d6\u30b9\u30ec\u30c3\u30c9"},
        {"Loaded", "\u30ed\u30fc\u30c9\u6e08\u307f"},
        {"Local Process:", "\u30ed\u30fc\u30ab\u30eb\u30d7\u30ed\u30bb\u30b9(L):"},
        {"Local Process:.mnemonic", 'L'},
        {"Look and Feel","Look & Feel"},
        {"Masthead.font", "Dialog-PLAIN-25"},
        {"Management Not Enabled","<b>\u6ce8</b>: \u7ba1\u7406\u30a8\u30fc\u30b8\u30a7\u30f3\u30c8\u304c\u3053\u306e\u30d7\u30ed\u30bb\u30b9\u3067\u6709\u52b9\u306b\u306a\u3063\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Management Will Be Enabled","<b>\u6ce8</b>: \u7ba1\u7406\u30a8\u30fc\u30b8\u30a7\u30f3\u30c8\u304c\u3053\u306e\u30d7\u30ed\u30bb\u30b9\u3067\u6709\u52b9\u306b\u306a\u308a\u307e\u3059\u3002"},
        {"MBeanAttributeInfo","MBeanAttributeInfo"},
        {"MBeanInfo","MBeanInfo"},
        {"MBeanNotificationInfo","MBeanNotificationInfo"},
        {"MBeanOperationInfo","MBeanOperationInfo"},
        {"MBeans","MBean"},
        {"MBeansTab.clearNotificationsButton", "\u6d88\u53bb(C)"},
        {"MBeansTab.clearNotificationsButton.mnemonic", 'C'},
        {"MBeansTab.clearNotificationsButton.toolTip", "\u901a\u77e5\u3092\u6d88\u53bb\u3059\u308b"},
        {"MBeansTab.compositeNavigationMultiple", "\u8907\u5408\u30ca\u30d3\u30b2\u30fc\u30b7\u30e7\u30f3 {0}/{1}"},
        {"MBeansTab.compositeNavigationSingle", "\u8907\u5408\u30ca\u30d3\u30b2\u30fc\u30b7\u30e7\u30f3"},
        {"MBeansTab.refreshAttributesButton", "\u66f4\u65b0(R)"},
        {"MBeansTab.refreshAttributesButton.mnemonic", 'R'},
        {"MBeansTab.refreshAttributesButton.toolTip", "\u5c5e\u6027\u3092\u66f4\u65b0\u3059\u308b"},
        {"MBeansTab.subscribeNotificationsButton", "\u767b\u9332(S)"},
        {"MBeansTab.subscribeNotificationsButton.mnemonic", 'S'},
        {"MBeansTab.subscribeNotificationsButton.toolTip", "\u901a\u77e5\u306e\u5f85\u6a5f\u3092\u958b\u59cb"},
        {"MBeansTab.tabularNavigationMultiple", "\u8868\u5f62\u5f0f\u30ca\u30d3\u30b2\u30fc\u30b7\u30e7\u30f3 {0}/{1}"},
        {"MBeansTab.tabularNavigationSingle", "\u8868\u5f62\u5f0f\u30ca\u30d3\u30b2\u30fc\u30b7\u30e7\u30f3"},
        {"MBeansTab.unsubscribeNotificationsButton", "\u767b\u9332\u89e3\u9664(U)"},
        {"MBeansTab.unsubscribeNotificationsButton.mnemonic", 'U'},
        {"MBeansTab.unsubscribeNotificationsButton.toolTip", "\u901a\u77e5\u306e\u5f85\u6a5f\u3092\u505c\u6b62"},
        {"Manage Hotspot MBeans in: ", "Hotspot MBean \u3092\u7ba1\u7406: "},
        {"Max","\u6700\u5927"},
        {"Maximum heap size","\u6700\u5927\u30d2\u30fc\u30d7\u30b5\u30a4\u30ba"},
        {"Memory","\u30e1\u30e2\u30ea"},
        {"MemoryPoolLabel", "\u30e1\u30e2\u30ea\u30d7\u30fc\u30eb \"{0}\""},
        {"MemoryTab.heapPlotter.accessibleName", "\u30d2\u30fc\u30d7\u30e1\u30e2\u30ea\u30fc\u306e\u4f7f\u7528\u72b6\u6cc1\u306e\u56f3\u3002"},
        {"MemoryTab.infoLabelFormat", "<html>\u4f7f\u7528\u6e08\u307f: {0}    \u78ba\u5b9a: {1}    \u6700\u5927: {2}</html>"},
        {"MemoryTab.nonHeapPlotter.accessibleName", "\u975e\u30d2\u30fc\u30d7\u30e1\u30e2\u30ea\u30fc\u306e\u4f7f\u7528\u72b6\u6cc1\u306e\u56f3\u3002"},
        {"MemoryTab.poolChart.aboveThreshold", "{0} \u306e\u3057\u304d\u3044\u5024\u3092\u8d85\u3048\u3066\u3044\u307e\u3059\u3002\n"},
        {"MemoryTab.poolChart.accessibleName", "\u30e1\u30e2\u30ea\u30fc\u30d7\u30fc\u30eb\u306e\u4f7f\u7528\u72b6\u6cc1\u306e\u56f3\u3002"},
        {"MemoryTab.poolChart.belowThreshold", "{0} \u306e\u3057\u304d\u3044\u5024\u3092\u4e0b\u56de\u3063\u3066\u3044\u307e\u3059\u3002\n"},
        {"MemoryTab.poolPlotter.accessibleName", "{0} \u30e1\u30e2\u30ea\u30fc\u306e\u4f7f\u7528\u72b6\u6cc1\u306e\u56f3\u3002"},
        {"Message","\u30e1\u30c3\u30bb\u30fc\u30b8"},
        {"Method successfully invoked", "\u30e1\u30bd\u30c3\u30c9\u306f\u6b63\u5e38\u306b\u8d77\u52d5\u3055\u308c\u307e\u3057\u305f"},
        {"Minimize All", "\u3059\u3079\u3066\u3092\u30a2\u30a4\u30b3\u30f3\u5316(M)"},
        {"Minimize All.mnemonic", 'M'},
        {"Minus Version", "\u3053\u308c\u306f {0} \u30d0\u30fc\u30b8\u30e7\u30f3 {1} \u3067\u3059"},
        {"Monitor locked",
             "   - \u30ed\u30c3\u30af\u3055\u308c\u305f {0}" + cr},
        {"Motif","Motif"},
        {"Name Build and Mode","{0} (\u30d3\u30eb\u30c9 {1}, {2})"},
        {"Name and Build","{0} (\u30d3\u30eb\u30c9 {1})"},
        {"Name","\u540d\u524d"},
        {"Name: ","\u540d\u524d: "},
        {"Name State",
             "\u540d\u524d: {0}" + cr +
             "\u72b6\u614b: {1}" + cr},
        {"Name State LockName",
             "\u540d\u524d: {0}" + cr +
             "\u72b6\u614b: {1} ({2} \u4e0a)" + cr},
        {"Name State LockName LockOwner",
             "\u540d\u524d: {0}" + cr +
             "\u72b6\u614b: {1} ({2} \u4e0a) \u6240\u6709\u8005: {3}" + cr},
        {"New Connection...", "\u65b0\u898f\u63a5\u7d9a(N)..."},
        {"New Connection....mnemonic", 'N'},
        {"New value applied","\u65b0\u3057\u3044\u5024\u304c\u9069\u7528\u3055\u308c\u307e\u3057\u305f"},
        {"No attribute selected","\u5c5e\u6027\u304c\u9078\u629e\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"No deadlock detected","\u30c7\u30c3\u30c9\u30ed\u30c3\u30af\u306f\u691c\u51fa\u3055\u308c\u307e\u305b\u3093\u3067\u3057\u305f"},
        {"No value selected","\u5024\u304c\u9078\u629e\u3055\u308c\u3066\u307e\u305b\u3093"},
        {"Non-Heap Memory Usage","\u975e\u30d2\u30fc\u30d7\u30e1\u30e2\u30ea\u306e\u4f7f\u7528\u72b6\u6cc1"},
        {"Non-Heap", "\u975e\u30d2\u30fc\u30d7"},
        {"Not Yet Implemented","\u5b9f\u88c5\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Not a valid event broadcaster", "\u6709\u52b9\u306a\u30a4\u30d9\u30f3\u30c8\u30d6\u30ed\u30fc\u30c9\u30ad\u30e3\u30b9\u30c8\u5143\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Notification","\u901a\u77e5"},
        {"Notification buffer","\u901a\u77e5\u30d0\u30c3\u30d5\u30a1\u30fc"},
        {"Notifications","\u901a\u77e5"},
        {"NotifTypes", "NotifTypes"},
        {"Number of Threads","\u30b9\u30ec\u30c3\u30c9\u6570"},
        {"Number of Loaded Classes","\u30ed\u30fc\u30c9\u3055\u308c\u305f\u30af\u30e9\u30b9\u306e\u6570"},
        {"Number of processors","\u30d7\u30ed\u30bb\u30c3\u30b5\u306e\u6570"},
        {"ObjectName","ObjectName"},
        {"Operating System","\u30aa\u30da\u30ec\u30fc\u30c6\u30a3\u30f3\u30b0\u30b7\u30b9\u30c6\u30e0"},
        {"Operation","\u30aa\u30da\u30ec\u30fc\u30b7\u30e7\u30f3"},
        {"Operation invocation","\u30aa\u30da\u30ec\u30fc\u30b7\u30e7\u30f3\u547c\u3073\u51fa\u3057"},
        {"Operation return value", "\u64cd\u4f5c\u306e\u623b\u308a\u5024"},
        {"Operations","\u64cd\u4f5c"},
        {"Overview","\u6982\u8981"},
        {"OverviewPanel.plotter.accessibleName", "{0} \u306e\u56f3\u3002"},
        {"Parameter", "\u30d1\u30e9\u30e1\u30fc\u30bf"},
        {"Password: ", "\u30d1\u30b9\u30ef\u30fc\u30c9(P): "},
        {"Password: .mnemonic", 'P'},
        {"Password.accessibleName", "\u30d1\u30b9\u30ef\u30fc\u30c9"},
        {"Peak","\u30d4\u30fc\u30af"},
        {"Perform GC", "GC \u306e\u5b9f\u884c"},
        {"Perform GC.mnemonic", 'G'},
        {"Perform GC.toolTip", "\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30b7\u30e7\u30f3\u3092\u8981\u6c42\u3059\u308b"},
        {"Plotter.accessibleName", "\u56f3"},
        {"Plotter.accessibleName.keyAndValue", "{0}={1}\n"},
        {"Plotter.accessibleName.noData", "\u30d7\u30ed\u30c3\u30c8\u3055\u308c\u305f\u30c7\u30fc\u30bf\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"Plotter.saveAsMenuItem", "\u30c7\u30fc\u30bf\u3092\u5225\u540d\u3067\u4fdd\u5b58(a)..."},
        {"Plotter.saveAsMenuItem.mnemonic", 'a'},
        {"Plotter.timeRangeMenu", "\u6642\u9593\u7bc4\u56f2(T)"},
        {"Plotter.timeRangeMenu.mnemonic", 'T'},
        {"Problem adding listener","\u30ea\u30b9\u30ca\u30fc\u8ffd\u52a0\u6642\u306e\u554f\u984c"},
        {"Problem displaying MBean", "MBean \u8868\u793a\u6642\u306e\u554f\u984c"},
        {"Problem invoking", "\u547c\u3073\u51fa\u3057\u6642\u306e\u554f\u984c"},
        {"Problem removing listener","\u30ea\u30b9\u30ca\u30fc\u524a\u9664\u6642\u306e\u554f\u984c"},
        {"Problem setting attribute","\u5c5e\u6027\u8a2d\u5b9a\u6642\u306e\u554f\u984c"},
        {"Process CPU time","\u30d7\u30ed\u30bb\u30b9 CPU \u6642\u9593"},
        {"R/W","R/W"},
        {"Readable","\u8aad\u307f\u8fbc\u307f\u53ef\u80fd"},
        {"Received","\u53d7\u4fe1\u6e08\u307f"},
        {"Reconnect","\u518d\u63a5\u7d9a"},
        {"Remote Process:", "\u30ea\u30e2\u30fc\u30c8\u30d7\u30ed\u30bb\u30b9(R):"},
        {"Remote Process:.mnemonic", 'R'},
        {"Remote Process.textField.accessibleName", "\u30ea\u30e2\u30fc\u30c8\u30d7\u30ed\u30bb\u30b9"},
        {"Remove","\u524a\u9664"},
        {"Restore All", "\u3059\u3079\u3066\u3092\u5fa9\u5143(R)"},
        {"Restore All.mnemonic", 'R'},
        {"Return value", "\u623b\u308a\u5024"},
        {"ReturnType", "ReturnType"},
        {"SeqNum","\u30b7\u30fc\u30b1\u30f3\u30b9\u756a\u53f7"},
        {"Size Bytes", "{0,number,integer} \u30d0\u30a4\u30c8"},
        {"Size Gb","{0} G \u30d0\u30a4\u30c8"},
        {"Size Kb","{0} K \u30d0\u30a4\u30c8"},
        {"Size Mb","{0} M \u30d0\u30a4\u30c8"},
        {"Source","\u30bd\u30fc\u30b9"},
        {"Stack trace",
             cr + "\u30b9\u30bf\u30c3\u30af\u30c8\u30ec\u30fc\u30b9: " + cr},
        {"Success:","\u6210\u529f:"},
        // Note: SummaryTab.headerDateTimeFormat can be one the following:
        // 1. A combination of two styles for date and time, using the
        //    constants from class DateFormat: SHORT, MEDIUM, LONG, FULL.
        //    Example: "MEDIUM,MEDIUM" or "FULL,SHORT"
        // 2. An explicit string pattern used for creating an instance
        //    of the class SimpleDateFormat.
        //    Example: "yyyy-MM-dd HH:mm:ss" or "M/d/yyyy h:mm:ss a"
        {"SummaryTab.headerDateTimeFormat", "FULL,FULL"},
        {"SummaryTab.pendingFinalization.label", "\u4fdd\u7559\u72b6\u614b\u306e\u30d5\u30a1\u30a4\u30ca\u30e9\u30a4\u30ba"},
        {"SummaryTab.pendingFinalization.value", "{0} \u30aa\u30d6\u30b8\u30a7\u30af\u30c8"},
        {"SummaryTab.tabName", "VM \u306e\u6982\u8981"},
        {"SummaryTab.vmVersion","{0} \u30d0\u30fc\u30b8\u30e7\u30f3 {1}"},
        {"TabularData are not supported", "TabularData \u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Threads","\u30b9\u30ec\u30c3\u30c9"},
        {"ThreadTab.infoLabelFormat", "<html>\u30e9\u30a4\u30d6: {0}    \u30d4\u30fc\u30af: {1}    \u7dcf\u6570: {2}</html>"},
        {"ThreadTab.threadInfo.accessibleName", "\u30b9\u30ec\u30c3\u30c9\u60c5\u5831"},
        {"ThreadTab.threadPlotter.accessibleName", "\u30b9\u30ec\u30c3\u30c9\u6570\u306e\u56f3\u3002"},
        {"Threshold","\u3057\u304d\u3044\u5024"},
        {"Tile", "\u4e26\u3079\u3066\u8868\u793a(T)"},
        {"Tile.mnemonic", 'T'},
        {"Time Range:", "\u6642\u9593\u7bc4\u56f2(T):"},
        {"Time Range:.mnemonic", 'T'},
        {"Time", "\u6642\u9593"},
        {"TimeStamp","\u30bf\u30a4\u30e0\u30b9\u30bf\u30f3\u30d7"},
        {"Total Loaded", "\u7dcf\u30ed\u30fc\u30c9\u6570"},
        {"Total classes loaded","\u30ed\u30fc\u30c9\u3055\u308c\u305f\u30af\u30e9\u30b9\u306e\u7dcf\u6570"},
        {"Total classes unloaded","\u30a2\u30f3\u30ed\u30fc\u30c9\u3055\u308c\u305f\u30af\u30e9\u30b9\u306e\u7dcf\u6570"},
        {"Total compile time","\u30b3\u30f3\u30d1\u30a4\u30eb\u306e\u7dcf\u6642\u9593"},
        {"Total physical memory","\u7dcf\u7269\u7406\u30e1\u30e2\u30ea"},
        {"Total threads started","\u958b\u59cb\u3057\u305f\u30b9\u30ec\u30c3\u30c9\u306e\u7dcf\u6570"},
        {"Total swap space","\u7dcf\u30b9\u30ef\u30c3\u30d7\u30b9\u30da\u30fc\u30b9"},
        {"Type","\u578b"},
        {"Unavailable","\u4f7f\u7528\u4e0d\u53ef\u80fd"},
        {"UNKNOWN","UNKNOWN"},
        {"Unknown Host","\u672a\u77e5\u306e\u30db\u30b9\u30c8: {0}"},
        {"Unregister", "\u767b\u9332\u89e3\u9664"},
        {"Uptime","\u30a2\u30c3\u30d7\u30bf\u30a4\u30e0"},
        {"Uptime: ","\u30a2\u30c3\u30d7\u30bf\u30a4\u30e0: "},
        {"Usage Threshold","\u4f7f\u7528\u91cf\u306e\u3057\u304d\u3044\u5024"},
        {"remoteTF.usage","<b>\u4f7f\u3044\u65b9</b>: &lt;\u30db\u30b9\u30c8\u540d&gt;:&lt;\u30dd\u30fc\u30c8&gt; \u307e\u305f\u306f service:jmx:&lt;\u30d7\u30ed\u30c8\u30b3\u30eb&gt;:&lt;sap&gt;"},
        {"Used","\u4f7f\u7528\u6e08\u307f"},
        {"Username: ", "\u30e6\u30fc\u30b6\u30fc\u540d(U): "},
        {"Username: .mnemonic", 'U'},
        {"Username.accessibleName", "\u30e6\u30fc\u30b6\u30fc\u540d"},
        {"UserData","UserData"},
        {"Virtual Machine","\u4eee\u60f3\u30de\u30b7\u30f3"},
        {"VM arguments","VM \u306e\u5f15\u6570"},
        {"VM","VM"},
        {"VMInternalFrame.accessibleDescription", "Java \u4eee\u60f3\u30de\u30b7\u30f3\u3092\u76e3\u8996\u3059\u308b\u305f\u3081\u306e\u5185\u90e8\u30d5\u30ec\u30fc\u30e0"},
        {"Value","\u5024"},
        {"Vendor", "\u30d9\u30f3\u30c0"},
        {"Verbose Output","\u8a73\u7d30\u51fa\u529b"},
        {"Verbose Output.toolTip", "\u30af\u30e9\u30b9\u30ed\u30fc\u30c7\u30a3\u30f3\u30b0\u30b7\u30b9\u30c6\u30e0\u306e\u8a73\u7d30\u51fa\u529b\u3092\u6709\u52b9\u306b\u3059\u308b"},
        {"View value", "\u5024\u3092\u8868\u793a\u3059\u308b"},
        {"View","\u8868\u793a"},
        {"Window", "\u30a6\u30a3\u30f3\u30c9\u30a6(W)"},
        {"Window.mnemonic", 'W'},
        {"Windows","\u30a6\u30a3\u30f3\u30c9\u30a6"},
        {"Writable","\u66f8\u304d\u8fbc\u307f\u53ef\u80fd"},
        {"You cannot drop a class here", "\u30af\u30e9\u30b9\u3092\u3053\u3053\u306b\u30c9\u30ed\u30c3\u30d7\u3067\u304d\u307e\u305b\u3093"},
        {"collapse", "\u6298\u308a\u305f\u305f\u307f"},
        {"connectionFailed1","\u63a5\u7d9a\u306b\u5931\u6557\u3057\u307e\u3057\u305f: \u518d\u8a66\u884c\u3057\u307e\u3059\u304b?"},
        {"connectionFailed2","{0} \u3078\u306e\u63a5\u7d9a\u304c\u6210\u529f\u3057\u307e\u305b\u3093\u3067\u3057\u305f\u3002<br>\u3082\u3046\u4e00\u5ea6\u8a66\u3057\u307e\u3059\u304b?"},
        {"connectionLost1","\u63a5\u7d9a\u304c\u5931\u308f\u308c\u307e\u3057\u305f: \u518d\u63a5\u7d9a\u3057\u307e\u3059\u304b?"},
        {"connectionLost2","\u30ea\u30e2\u30fc\u30c8\u30d7\u30ed\u30bb\u30b9\u304c\u7d42\u4e86\u3057\u305f\u305f\u3081\u3001{0} \u3078\u306e\u63a5\u7d9a\u304c\u5931\u308f\u308c\u307e\u3057\u305f\u3002<br>\u518d\u63a5\u7d9a\u3057\u307e\u3059\u304b?"},
        {"connectingTo1","{0} \u306b\u63a5\u7d9a\u3057\u3066\u3044\u307e\u3059"},
        {"connectingTo2","\u73fe\u5728 {0} \u306b\u63a5\u7d9a\u3057\u3066\u3044\u307e\u3059\u3002<br>\u3053\u308c\u306b\u306f\u5c11\u3057\u6642\u9593\u304c\u304b\u304b\u308a\u307e\u3059\u3002"},
        {"deadlockAllTab","\u3059\u3079\u3066"},
        {"deadlockTab","\u30c7\u30c3\u30c9\u30ed\u30c3\u30af"},
        {"deadlockTabN","\u30c7\u30c3\u30c9\u30ed\u30c3\u30af {0}"},
        {"expand", "\u5c55\u958b"},
        {"kbytes","{0} k \u30d0\u30a4\u30c8"},
        {"operation","\u30aa\u30da\u30ec\u30fc\u30b7\u30e7\u30f3"},
        {"plot", "\u30d7\u30ed\u30c3\u30c8"},
        {"visualize","\u8868\u793a"},
        {"zz usage text",
             "\u4f7f\u3044\u65b9: {0} [ -interval=n ] [ -notile ] [ -pluginpath <path> ] [ -version ] [ connection ... ]" + cr +
             cr +
             "  -interval   \u66f4\u65b0\u9593\u9694\u3092 n \u79d2\u306b\u8a2d\u5b9a\u3059\u308b (\u30c7\u30d5\u30a9\u30eb\u30c8\u306f 4 \u79d2)" + cr +
             "  -notile     \u521d\u671f\u72b6\u614b\u306e\u30a6\u30a3\u30f3\u30c9\u30a6\u3092\u30bf\u30a4\u30eb\u72b6\u306b\u4e26\u3079\u306a\u3044 (\u63a5\u7d9a\u304c\u8907\u6570\u3042\u308b\u5834\u5408)" + cr +
             "  -pluginpath JConsole \u3067\u30d7\u30e9\u30b0\u30a4\u30f3\u3092\u63a2\u3059\u305f\u3081\u306b\u4f7f\u7528\u3059\u308b\u30d1\u30b9\u3092\u6307\u5b9a\u3059\u308b" + cr +
             "  -version    \u30d7\u30ed\u30b0\u30e9\u30e0\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u51fa\u529b\u3059\u308b" + cr +
             cr +
             "  connection = pid || host:port || JMX URL (service:jmx:<protocol>://...)" + cr +
             "  pid         \u30bf\u30fc\u30b2\u30c3\u30c8\u30d7\u30ed\u30bb\u30b9\u306e\u30d7\u30ed\u30bb\u30b9 ID" + cr +
             "  host        \u30ea\u30e2\u30fc\u30c8\u30db\u30b9\u30c8\u306e\u540d\u524d\u307e\u305f\u306f IP \u30a2\u30c9\u30ec\u30b9" + cr +
             "  port        \u30ea\u30e2\u30fc\u30c8\u63a5\u7d9a\u7528\u306e\u30dd\u30fc\u30c8\u756a\u53f7" + cr +
             cr +
             "  -J          JConsole \u3092\u5b9f\u884c\u3059\u308b Java \u4eee\u60f3\u30de\u30b7\u30f3\u3078\u306e" + cr +
             "              \u5165\u529b\u5f15\u6570\u3092\u6307\u5b9a\u3059\u308b"},
        // END OF MATERIAL TO LOCALIZE
        };
    }

    public synchronized Object[][] getContents() {
        return getContents0();
    }
}
