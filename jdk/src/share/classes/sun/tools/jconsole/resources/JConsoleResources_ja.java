/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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
public class JConsoleResources_ja extends ListResourceBundle {

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
        {" 1 day"," 1\u65E5"},
        {" 1 hour"," 1\u6642\u9593"},
        {" 1 min"," 1\u5206"},
        {" 1 month"," 1\u304B\u6708"},
        {" 1 year"," 1\u5E74"},
        {" 2 hours"," 2\u6642\u9593"},
        {" 3 hours"," 3\u6642\u9593"},
        {" 3 months"," 3\u304B\u6708"},
        {" 5 min"," 5\u5206"},
        {" 6 hours"," 6\u6642\u9593"},
        {" 6 months"," 6\u304B\u6708"},
        {" 7 days"," 7\u65E5"},
        {"10 min","10\u5206"},
        {"12 hours","12\u6642\u9593"},
        {"30 min","30\u5206"},
        {"<","<"},
        {"<<","<<"},
        {">",">"},
        {"ACTION","ACTION"},
        {"ACTION_INFO","ACTION_INFO"},
        {"All","\u3059\u3079\u3066"},
        {"Apply","\u9069\u7528"},
        {"Architecture","\u30A2\u30FC\u30AD\u30C6\u30AF\u30C1\u30E3"},
        {"Array, OpenType", "\u914D\u5217\u3001OpenType"},
        {"Array, OpenType, Numeric value viewer","\u914D\u5217\u3001OpenType\u3001\u6570\u5024\u30D3\u30E5\u30FC\u30A2"},
        {"Attribute","\u5C5E\u6027"},
        {"Attribute value","\u5C5E\u6027\u5024"},
        {"Attribute values","\u5C5E\u6027\u5024"},
        {"Attributes","\u5C5E\u6027"},
        {"Blank", "\u30D6\u30E9\u30F3\u30AF"},
        {"BlockedCount WaitedCount",
             "\u30D6\u30ED\u30C3\u30AF\u6E08\u5408\u8A08: {0}  \u5F85\u6A5F\u6E08\u5408\u8A08: {1}\n"},
        {"Boot class path","\u30D6\u30FC\u30C8\u30FB\u30AF\u30E9\u30B9\u30D1\u30B9"},
        {"BorderedComponent.moreOrLessButton.toolTip", "\u8868\u793A\u3059\u308B\u60C5\u5831\u91CF\u3092\u5897\u6E1B\u3059\u308B\u30C8\u30B0\u30EB"},
        {"CPU Usage","CPU\u4F7F\u7528\u7387"},
        {"CPUUsageFormat","CPU\u4F7F\u7528\u7387: {0}%"},
        {"Cancel","\u53D6\u6D88"},
        {"Cascade", "\u91CD\u306D\u3066\u8868\u793A"},
        {"Cascade.mnemonic", "C"},
        {"Chart:", "\u30C1\u30E3\u30FC\u30C8:"},
        {"Chart:.mnemonic", "C"},
        {"Class path","\u30AF\u30E9\u30B9\u30D1\u30B9"},
        {"Class","\u30AF\u30E9\u30B9"},
        {"ClassName","ClassName"},
        {"ClassTab.infoLabelFormat", "<html>\u30ED\u30FC\u30C9\u6E08: {0}    \u672A\u30ED\u30FC\u30C9: {1}    \u5408\u8A08: {2}</html>"},
        {"ClassTab.loadedClassesPlotter.accessibleName", "\u30ED\u30FC\u30C9\u6E08\u30AF\u30E9\u30B9\u306E\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"Classes","\u30AF\u30E9\u30B9"},
        {"Close","\u9589\u3058\u308B"},
        {"Column.Name", "\u540D\u524D"},
        {"Column.PID", "PID"},
        {"Committed memory","\u30B3\u30DF\u30C3\u30C8\u6E08\u30E1\u30E2\u30EA\u30FC"},
        {"Committed virtual memory","\u30B3\u30DF\u30C3\u30C8\u6E08\u4EEE\u60F3\u30E1\u30E2\u30EA\u30FC"},
        {"Committed", "\u30B3\u30DF\u30C3\u30C8\u6E08"},
        {"Compiler","\u30B3\u30F3\u30D1\u30A4\u30E9"},
        {"CompositeData","CompositeData"},
        {"Config","\u69CB\u6210"},
        {"Connect", "\u63A5\u7D9A"},
        {"Connect.mnemonic", "C"},
        {"Connect...","\u63A5\u7D9A..."},
        {"ConnectDialog.connectButton.toolTip", "Java\u4EEE\u60F3\u30DE\u30B7\u30F3\u306B\u63A5\u7D9A"},
        {"ConnectDialog.accessibleDescription", "\u30ED\u30FC\u30AB\u30EB\u307E\u305F\u306F\u30EA\u30E2\u30FC\u30C8\u306EJava\u4EEE\u60F3\u30DE\u30B7\u30F3\u3078\u306E\u65B0\u898F\u63A5\u7D9A\u3092\u884C\u3046\u30C0\u30A4\u30A2\u30ED\u30B0"},
        {"ConnectDialog.masthead.accessibleName", "\u30DE\u30B9\u30C8\u30D8\u30C3\u30C9\u56F3\u5F62"},
        {"ConnectDialog.masthead.title", "\u65B0\u898F\u63A5\u7D9A"},
        {"ConnectDialog.statusBar.accessibleName", "\u30B9\u30C6\u30FC\u30BF\u30B9\u30FB\u30D0\u30FC"},
        {"ConnectDialog.title", "JConsole: \u65B0\u898F\u63A5\u7D9A"},
        {"Connected. Click to disconnect.","\u63A5\u7D9A\u3055\u308C\u3066\u3044\u307E\u3059\u3002\u30AF\u30EA\u30C3\u30AF\u3059\u308B\u3068\u5207\u65AD\u3057\u307E\u3059\u3002"},
        {"Connection failed","\u63A5\u7D9A\u306B\u5931\u6557\u3057\u307E\u3057\u305F"},
        {"Connection", "\u63A5\u7D9A"},
        {"Connection.mnemonic", "C"},
        {"Connection name", "\u63A5\u7D9A\u540D"},
        {"ConnectionName (disconnected)","{0} (\u5207\u65AD\u6E08)"},
        {"Constructor","\u30B3\u30F3\u30B9\u30C8\u30E9\u30AF\u30BF"},
        {"Current classes loaded", "\u30ED\u30FC\u30C9\u6E08\u306E\u73FE\u5728\u306E\u30AF\u30E9\u30B9"},
        {"Current heap size","\u73FE\u5728\u306E\u30D2\u30FC\u30D7\u30FB\u30B5\u30A4\u30BA"},
        {"Current value","\u73FE\u5728\u5024: {0}"},
        {"Create", "\u4F5C\u6210"},
        {"Daemon threads","\u30C7\u30FC\u30E2\u30F3\u30FB\u30B9\u30EC\u30C3\u30C9"},
        {"Disconnected. Click to connect.","\u5207\u65AD\u3055\u308C\u3066\u3044\u307E\u3059\u3002\u30AF\u30EA\u30C3\u30AF\u3059\u308B\u3068\u63A5\u7D9A\u3057\u307E\u3059\u3002"},
        {"Double click to expand/collapse","\u5C55\u958B\u307E\u305F\u306F\u7E2E\u5C0F\u3059\u308B\u306B\u306F\u30C0\u30D6\u30EB\u30AF\u30EA\u30C3\u30AF\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Double click to visualize", "\u8996\u899A\u5316\u3059\u308B\u306B\u306F\u30C0\u30D6\u30EB\u30AF\u30EA\u30C3\u30AF\u3057\u3066\u304F\u3060\u3055\u3044"},
        {"Description", "\u8AAC\u660E"},
        {"Description: ", "\u8AAC\u660E: "},
        {"Descriptor", "\u8A18\u8FF0\u5B50"},
        {"Details", "\u8A73\u7D30"},
        {"Detect Deadlock", "\u30C7\u30C3\u30C9\u30ED\u30C3\u30AF\u306E\u691C\u51FA"},
        {"Detect Deadlock.mnemonic", "D"},
        {"Detect Deadlock.toolTip", "\u30C7\u30C3\u30C9\u30ED\u30C3\u30AF\u6E08\u30B9\u30EC\u30C3\u30C9\u306E\u691C\u51FA"},
        {"Dimension is not supported:","\u6B21\u5143\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093:"},
        {"Discard chart", "\u30C1\u30E3\u30FC\u30C8\u306E\u7834\u68C4"},
        {"DurationDaysHoursMinutes","{0,choice,1#{0,number,integer}\u65E5|1.0<{0,number,integer}\u65E5}{1,choice,0<{1,number,integer}\u6642\u9593|1#{1,number,integer}\u6642\u9593|1<{1,number,integer}\u6642\u9593}{2,choice,0<{2,number,integer}\u5206|1#{2,number,integer}\u5206|1.0<{2,number,integer}\u5206}"},

        {"DurationHoursMinutes","{0,choice,1#{0,number,integer}\u6642\u9593|1<{0,number,integer}\u6642\u9593}{1,choice,0<{1,number,integer}\u5206|1#{1,number,integer}\u5206|1.0<{1,number,integer}\u5206}"},

        {"DurationMinutes","{0,choice,1#{0,number,integer}\u5206|1.0<{0,number,integer}\u5206}"},
        {"DurationSeconds","{0}\u79D2"},
        {"Empty array", "\u7A7A\u306E\u914D\u5217"},
        {"Empty opentype viewer", "\u7A7A\u306Eopentype\u30D3\u30E5\u30FC\u30A2"},
        {"Error","\u30A8\u30E9\u30FC"},
        {"Error: MBeans already exist","\u30A8\u30E9\u30FC: MBeans\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u307E\u3059"},
        {"Error: MBeans do not exist","\u30A8\u30E9\u30FC: MBeans\u306F\u5B58\u5728\u3057\u307E\u305B\u3093"},
        {"Error:","\u30A8\u30E9\u30FC:"},
        {"Event","\u30A4\u30D9\u30F3\u30C8"},
        {"Exit", "\u7D42\u4E86"},
        {"Exit.mnemonic", "X"},
        {"Fail to load plugin", "\u8B66\u544A: \u30D7\u30E9\u30B0\u30A4\u30F3\u306E\u30ED\u30FC\u30C9\u306B\u5931\u6557\u3057\u307E\u3057\u305F: {0}"},
        {"FileChooser.fileExists.cancelOption", "\u53D6\u6D88"},
        {"FileChooser.fileExists.message", "<html><center>\u30D5\u30A1\u30A4\u30EB\u306F\u3059\u3067\u306B\u5B58\u5728\u3057\u3066\u3044\u307E\u3059:<br>{0}<br>\u7F6E\u63DB\u3057\u3066\u3082\u3088\u308D\u3057\u3044\u3067\u3059\u304B\u3002"},
        {"FileChooser.fileExists.okOption", "\u7F6E\u63DB"},
        {"FileChooser.fileExists.title", "\u30D5\u30A1\u30A4\u30EB\u304C\u5B58\u5728\u3057\u3066\u3044\u307E\u3059"},
        {"FileChooser.savedFile", "<html>\u30D5\u30A1\u30A4\u30EB\u306B\u4FDD\u5B58\u3057\u307E\u3057\u305F:<br>{0}<br>({1}\u30D0\u30A4\u30C8)"},
        {"FileChooser.saveFailed.message", "<html><center>\u30D5\u30A1\u30A4\u30EB\u3078\u306E\u4FDD\u5B58\u306B\u5931\u6557\u3057\u307E\u3057\u305F:<br>{0}<br>{1}"},
        {"FileChooser.saveFailed.title", "\u4FDD\u5B58\u306B\u5931\u6557\u3057\u307E\u3057\u305F"},
        {"Free physical memory","\u7A7A\u304D\u7269\u7406\u30E1\u30E2\u30EA\u30FC"},
        {"Free swap space","\u7A7A\u304D\u30B9\u30EF\u30C3\u30D7\u30FB\u30B9\u30DA\u30FC\u30B9"},
        {"Garbage collector","\u30AC\u30D9\u30FC\u30B8\u30FB\u30B3\u30EC\u30AF\u30BF"},
        {"GTK","GTK"},
        {"GcInfo","\u540D\u524D= ''{0}''\u3001\u30B3\u30EC\u30AF\u30B7\u30E7\u30F3= {1,choice,-1#\u3042\u308A\u307E\u305B\u3093|0#{1,number,integer}\u500B}\u3001\u5408\u8A08\u6D88\u8CBB\u6642\u9593= {2}"},
        {"GC time","GC\u6642\u9593"},
        {"GC time details","{1}\u3067{0} ({2}\u500B\u306E\u30B3\u30EC\u30AF\u30B7\u30E7\u30F3)"},
        {"Heap Memory Usage","\u30D2\u30FC\u30D7\u30FB\u30E1\u30E2\u30EA\u30FC\u4F7F\u7528\u7387"},
        {"Heap", "\u30D2\u30FC\u30D7"},
        {"Help.AboutDialog.accessibleDescription", "JConsole\u3068JDK\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u306B\u3064\u3044\u3066\u306E\u60C5\u5831\u3092\u542B\u3080\u30C0\u30A4\u30A2\u30ED\u30B0"},
        {"Help.AboutDialog.jConsoleVersion", "JConsole\u30D0\u30FC\u30B8\u30E7\u30F3:<br>{0}"},
        {"Help.AboutDialog.javaVersion", "Java VM\u30D0\u30FC\u30B8\u30E7\u30F3:<br>{0}"},
        {"Help.AboutDialog.masthead.accessibleName", "\u30DE\u30B9\u30C8\u30D8\u30C3\u30C9\u56F3\u5F62"},
        {"Help.AboutDialog.masthead.title", "JConsole\u306B\u3064\u3044\u3066"},
        {"Help.AboutDialog.title", "JConsole: \u8A73\u7D30"},
        {"Help.AboutDialog.userGuideLink", "JConsole\u30E6\u30FC\u30B6\u30FC\u30FB\u30AC\u30A4\u30C9:<br>{0}"},
        {"Help.AboutDialog.userGuideLink.mnemonic", "U"},
        {"Help.AboutDialog.userGuideLink.url", "http://java.sun.com/javase/6/docs/technotes/guides/management/jconsole.html"},
        {"HelpMenu.About.title", "JConsole\u306B\u3064\u3044\u3066"},
        {"HelpMenu.About.title.mnemonic", "A"},
        {"HelpMenu.UserGuide.title", "\u30AA\u30F3\u30E9\u30A4\u30F3\u30FB\u30E6\u30FC\u30B6\u30FC\u30FB\u30AC\u30A4\u30C9"},
        {"HelpMenu.UserGuide.title.mnemonic", "U"},
        {"HelpMenu.title", "\u30D8\u30EB\u30D7"},
        {"HelpMenu.title.mnemonic", "H"},
        {"Hotspot MBeans...", "Hotspot MBeans..."},
        {"Hotspot MBeans....mnemonic", "H"},
        {"Hotspot MBeans.dialog.accessibleDescription", "Hotspot MBeans\u306E\u7BA1\u7406\u7528\u30C0\u30A4\u30A2\u30ED\u30B0"},
        {"Impact","\u5F71\u97FF"},
        {"Info","\u60C5\u5831"},
        {"INFO","\u60C5\u5831"},
        {"Invalid plugin path", "\u8B66\u544A: \u7121\u52B9\u306A\u30D7\u30E9\u30B0\u30A4\u30F3\u30FB\u30D1\u30B9: {0}"},
        {"Invalid URL", "\u7121\u52B9\u306AURL: {0}"},
        {"Is","\u6B21\u306B\u4E00\u81F4\u3059\u308B"},
        {"Java Monitoring & Management Console", "Java Monitoring & Management Console"},
        {"JConsole: ","JConsole: {0}"},
        {"JConsole version","JConsole\u30D0\u30FC\u30B8\u30E7\u30F3\"{0}\""},
        {"JConsole.accessibleDescription", "Java Monitoring & Management Console"},
        {"JIT compiler","JIT\u30B3\u30F3\u30D1\u30A4\u30E9"},
        {"Java Virtual Machine","Java\u4EEE\u60F3\u30DE\u30B7\u30F3"},
        {"Java","Java"},
        {"Library path","\u30E9\u30A4\u30D6\u30E9\u30EA\u30FB\u30D1\u30B9"},
        {"Listeners","\u30EA\u30B9\u30CA\u30FC"},
        {"Live Threads","\u5B9F\u884C\u4E2D\u306E\u30B9\u30EC\u30C3\u30C9"},
        {"Loaded", "\u30ED\u30FC\u30C9\u6E08"},
        {"Local Process:", "\u30ED\u30FC\u30AB\u30EB\u30FB\u30D7\u30ED\u30BB\u30B9:"},
        {"Local Process:.mnemonic", "L"},
        {"Look and Feel","Look&Feel"},
        {"Masthead.font", "Dialog-PLAIN-25"},
        {"Management Not Enabled","<b>\u6CE8\u610F</b>: \u7BA1\u7406\u30A8\u30FC\u30B8\u30A7\u30F3\u30C8\u306F\u3053\u306E\u30D7\u30ED\u30BB\u30B9\u3067\u306F\u6709\u52B9\u5316\u3055\u308C\u307E\u305B\u3093\u3002"},
        {"Management Will Be Enabled","<b>\u6CE8\u610F</b>: \u7BA1\u7406\u30A8\u30FC\u30B8\u30A7\u30F3\u30C8\u306F\u3053\u306E\u30D7\u30ED\u30BB\u30B9\u3067\u6709\u52B9\u5316\u3055\u308C\u307E\u3059\u3002"},
        {"MBeanAttributeInfo","MBeanAttributeInfo"},
        {"MBeanInfo","MBeanInfo"},
        {"MBeanNotificationInfo","MBeanNotificationInfo"},
        {"MBeanOperationInfo","MBeanOperationInfo"},
        {"MBeans","MBeans"},
        {"MBeansTab.clearNotificationsButton", "\u30AF\u30EA\u30A2"},
        {"MBeansTab.clearNotificationsButton.mnemonic", "C"},
        {"MBeansTab.clearNotificationsButton.toolTip", "\u901A\u77E5\u306E\u30AF\u30EA\u30A2"},
        {"MBeansTab.compositeNavigationMultiple", "\u30B3\u30F3\u30DD\u30B8\u30C3\u30C8\u30FB\u30CA\u30D3\u30B2\u30FC\u30B7\u30E7\u30F3{0}/{1}"},
        {"MBeansTab.compositeNavigationSingle", "\u30B3\u30F3\u30DD\u30B8\u30C3\u30C8\u30FB\u30CA\u30D3\u30B2\u30FC\u30B7\u30E7\u30F3"},
        {"MBeansTab.refreshAttributesButton", "\u30EA\u30D5\u30EC\u30C3\u30B7\u30E5"},
        {"MBeansTab.refreshAttributesButton.mnemonic", "R"},
        {"MBeansTab.refreshAttributesButton.toolTip", "\u5C5E\u6027\u306E\u30EA\u30D5\u30EC\u30C3\u30B7\u30E5"},
        {"MBeansTab.subscribeNotificationsButton", "\u30B5\u30D6\u30B9\u30AF\u30E9\u30A4\u30D6"},
        {"MBeansTab.subscribeNotificationsButton.mnemonic", "S"},
        {"MBeansTab.subscribeNotificationsButton.toolTip", "\u901A\u77E5\u30EA\u30B9\u30CB\u30F3\u30B0\u306E\u958B\u59CB"},
        {"MBeansTab.tabularNavigationMultiple", "\u30BF\u30D6\u30FB\u30CA\u30D3\u30B2\u30FC\u30B7\u30E7\u30F3{0}/{1}"},
        {"MBeansTab.tabularNavigationSingle", "\u30BF\u30D6\u30FB\u30CA\u30D3\u30B2\u30FC\u30B7\u30E7\u30F3"},
        {"MBeansTab.unsubscribeNotificationsButton", "\u30B5\u30D6\u30B9\u30AF\u30E9\u30A4\u30D6\u89E3\u9664"},
        {"MBeansTab.unsubscribeNotificationsButton.mnemonic", "U"},
        {"MBeansTab.unsubscribeNotificationsButton.toolTip", "\u901A\u77E5\u30EA\u30B9\u30CB\u30F3\u30B0\u306E\u505C\u6B62"},
        {"Manage Hotspot MBeans in: ", "Hotspot MBeans\u306E\u7BA1\u7406: "},
        {"Max","\u6700\u5927"},
        {"Maximum heap size","\u6700\u5927\u30D2\u30FC\u30D7\u30FB\u30B5\u30A4\u30BA"},
        {"Memory","\u30E1\u30E2\u30EA\u30FC"},
        {"MemoryPoolLabel", "\u30E1\u30E2\u30EA\u30FC\u30FB\u30D7\u30FC\u30EB\"{0}\""},
        {"MemoryTab.heapPlotter.accessibleName", "\u30D2\u30FC\u30D7\u7528\u306E\u30E1\u30E2\u30EA\u30FC\u4F7F\u7528\u7387\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"MemoryTab.infoLabelFormat", "<html>\u4F7F\u7528\u6E08: {0}    \u30B3\u30DF\u30C3\u30C8\u6E08: {1}    \u6700\u5927: {2}</html>"},
        {"MemoryTab.nonHeapPlotter.accessibleName", "\u975E\u30D2\u30FC\u30D7\u7528\u306E\u30E1\u30E2\u30EA\u30FC\u4F7F\u7528\u7387\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"MemoryTab.poolChart.aboveThreshold", "{0}\u306E\u3057\u304D\u3044\u5024\u3088\u308A\u4E0A\u3067\u3059\u3002\n"},
        {"MemoryTab.poolChart.accessibleName", "\u30E1\u30E2\u30EA\u30FC\u30FB\u30D7\u30FC\u30EB\u4F7F\u7528\u7387\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"MemoryTab.poolChart.belowThreshold", "{0}\u306E\u3057\u304D\u3044\u5024\u3088\u308A\u4E0B\u3067\u3059\u3002\n"},
        {"MemoryTab.poolPlotter.accessibleName", "{0}\u306E\u30E1\u30E2\u30EA\u30FC\u4F7F\u7528\u7387\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"Message","\u30E1\u30C3\u30BB\u30FC\u30B8"},
        {"Method successfully invoked", "\u30E1\u30BD\u30C3\u30C9\u304C\u6B63\u5E38\u306B\u8D77\u52D5\u3055\u308C\u307E\u3057\u305F"},
        {"Minimize All", "\u3059\u3079\u3066\u6700\u5C0F\u5316"},
        {"Minimize All.mnemonic", "M"},
        {"Minus Version", "\u3053\u308C\u306F{0}\u306E\u30D0\u30FC\u30B8\u30E7\u30F3{1}\u3067\u3059"},
        {"Monitor locked",
             "   - \u30ED\u30C3\u30AF\u6E08{0}\n"},
        {"Motif","Motif"},
        {"Name Build and Mode","{0} (\u30D3\u30EB\u30C9{1}, {2})"},
        {"Name and Build","{0} (\u30D3\u30EB\u30C9{1})"},
        {"Name","\u540D\u524D"},
        {"Name: ","\u540D\u524D: "},
        {"Name State",
             "\u540D\u524D: {0}\n\u72B6\u614B: {1}\n"},
        {"Name State LockName",
             "\u540D\u524D: {0}\n\u72B6\u614B: {2}\u306E{1}\n"},
        {"Name State LockName LockOwner",
             "\u540D\u524D: {0}\n\u72B6\u614B: {2}\u306E{1}\u3001\u6240\u6709\u8005: {3}\n"},
        {"New Connection...", "\u65B0\u898F\u63A5\u7D9A..."},
        {"New Connection....mnemonic", "N"},
        {"New value applied","\u9069\u7528\u3055\u308C\u305F\u65B0\u898F\u5024"},
        {"No attribute selected","\u5C5E\u6027\u304C\u9078\u629E\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"No deadlock detected","\u30C7\u30C3\u30C9\u30ED\u30C3\u30AF\u304C\u691C\u51FA\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"No value selected","\u5024\u304C\u9078\u629E\u3055\u308C\u307E\u305B\u3093\u3067\u3057\u305F"},
        {"Non-Heap Memory Usage","\u975E\u30D2\u30FC\u30D7\u30FB\u30E1\u30E2\u30EA\u30FC\u4F7F\u7528\u7387"},
        {"Non-Heap", "\u975E\u30D2\u30FC\u30D7"},
        {"Not Yet Implemented","\u307E\u3060\u5B9F\u88C5\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"Not a valid event broadcaster", "\u6709\u52B9\u306A\u30A4\u30D9\u30F3\u30C8\u30FB\u30D6\u30ED\u30FC\u30C9\u30AD\u30E3\u30B9\u30BF\u3067\u306F\u3042\u308A\u307E\u305B\u3093"},
        {"Notification","\u901A\u77E5"},
        {"Notification buffer","\u901A\u77E5\u30D0\u30C3\u30D5\u30A1"},
        {"Notifications","\u901A\u77E5"},
        {"NotifTypes", "NotifTypes"},
        {"Number of Threads","\u30B9\u30EC\u30C3\u30C9\u6570"},
        {"Number of Loaded Classes","\u30ED\u30FC\u30C9\u6E08\u30AF\u30E9\u30B9\u6570"},
        {"Number of processors","\u30D7\u30ED\u30BB\u30C3\u30B5\u6570"},
        {"ObjectName","ObjectName"},
        {"Operating System","\u30AA\u30DA\u30EC\u30FC\u30C6\u30A3\u30F3\u30B0\u30FB\u30B7\u30B9\u30C6\u30E0"},
        {"Operation","\u64CD\u4F5C"},
        {"Operation invocation","\u64CD\u4F5C\u306E\u547C\u51FA\u3057"},
        {"Operation return value", "\u64CD\u4F5C\u306E\u623B\u308A\u5024"},
        {"Operations","\u64CD\u4F5C"},
        {"Overview","\u6982\u8981"},
        {"OverviewPanel.plotter.accessibleName", "{0}\u306E\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"Parameter", "\u30D1\u30E9\u30E1\u30FC\u30BF"},
        {"Password: ", "\u30D1\u30B9\u30EF\u30FC\u30C9: "},
        {"Password: .mnemonic", "P"},
        {"Password.accessibleName", "\u30D1\u30B9\u30EF\u30FC\u30C9"},
        {"Peak","\u30D4\u30FC\u30AF"},
        {"Perform GC", "GC\u306E\u5B9F\u884C"},
        {"Perform GC.mnemonic", "G"},
        {"Perform GC.toolTip", "\u30AC\u30D9\u30FC\u30B8\u30FB\u30B3\u30EC\u30AF\u30B7\u30E7\u30F3\u306E\u30EA\u30AF\u30A8\u30B9\u30C8"},
        {"Plotter.accessibleName", "\u30C1\u30E3\u30FC\u30C8"},
        {"Plotter.accessibleName.keyAndValue", "{0}={1}\n"},
        {"Plotter.accessibleName.noData", "\u30C7\u30FC\u30BF\u304C\u30D7\u30ED\u30C3\u30C8\u3055\u308C\u307E\u305B\u3093\u3002"},
        {"Plotter.saveAsMenuItem", "\u540D\u524D\u3092\u4ED8\u3051\u3066\u30C7\u30FC\u30BF\u3092\u4FDD\u5B58..."},
        {"Plotter.saveAsMenuItem.mnemonic", "A"},
        {"Plotter.timeRangeMenu", "\u6642\u9593\u7BC4\u56F2"},
        {"Plotter.timeRangeMenu.mnemonic", "T"},
        {"Problem adding listener","\u30EA\u30B9\u30CA\u30FC\u8FFD\u52A0\u4E2D\u306E\u554F\u984C"},
        {"Problem displaying MBean", "MBean\u8868\u793A\u4E2D\u306E\u554F\u984C"},
        {"Problem invoking", "\u547C\u51FA\u3057\u4E2D\u306E\u554F\u984C"},
        {"Problem removing listener","\u30EA\u30B9\u30CA\u30FC\u524A\u9664\u4E2D\u306E\u554F\u984C"},
        {"Problem setting attribute","\u5C5E\u6027\u8A2D\u5B9A\u4E2D\u306E\u554F\u984C"},
        {"Process CPU time","\u30D7\u30ED\u30BB\u30B9CPU\u6642\u9593"},
        {"R/W","R/W"},
        {"Readable","\u8AAD\u53D6\u308A\u53EF\u80FD"},
        {"Received","\u53D7\u4FE1\u6E08"},
        {"Reconnect","\u518D\u63A5\u7D9A"},
        {"Remote Process:", "\u30EA\u30E2\u30FC\u30C8\u30FB\u30D7\u30ED\u30BB\u30B9:"},
        {"Remote Process:.mnemonic", "R"},
        {"Remote Process.textField.accessibleName", "\u30EA\u30E2\u30FC\u30C8\u30FB\u30D7\u30ED\u30BB\u30B9"},
        {"Remove","\u524A\u9664"},
        {"Restore All", "\u3059\u3079\u3066\u5FA9\u5143"},
        {"Restore All.mnemonic", "R"},
        {"Return value", "\u623B\u308A\u5024"},
        {"ReturnType", "ReturnType"},
        {"SeqNum","SeqNum"},
        {"Size Bytes", "{0,number,integer}\u30D0\u30A4\u30C8"},
        {"Size Gb","{0} Gb"},
        {"Size Kb","{0} Kb"},
        {"Size Mb","{0} Mb"},
        {"Source","\u30BD\u30FC\u30B9"},
        {"Stack trace",
              "\n\u30B9\u30BF\u30C3\u30AF\u30FB\u30C8\u30EC\u30FC\u30B9: \n"},
        {"Success:","\u6210\u529F:"},
        // Note: SummaryTab.headerDateTimeFormat can be one the following:
        // 1. A combination of two styles for date and time, using the
        //    constants from class DateFormat: SHORT, MEDIUM, LONG, FULL.
        //    Example: "MEDIUM,MEDIUM" or "FULL,SHORT"
        // 2. An explicit string pattern used for creating an instance
        //    of the class SimpleDateFormat.
        //    Example: "yyyy-MM-dd HH:mm:ss" or "M/d/yyyy h:mm:ss a"
        {"SummaryTab.headerDateTimeFormat", "FULL,FULL"},
        {"SummaryTab.pendingFinalization.label", "\u30D5\u30A1\u30A4\u30CA\u30E9\u30A4\u30BA\u306E\u30DA\u30F3\u30C7\u30A3\u30F3\u30B0"},
        {"SummaryTab.pendingFinalization.value", "{0}\u500B\u306E\u30AA\u30D6\u30B8\u30A7\u30AF\u30C8"},
        {"SummaryTab.tabName", "VM\u30B5\u30DE\u30EA\u30FC"},
        {"SummaryTab.vmVersion","{0}\u30D0\u30FC\u30B8\u30E7\u30F3{1}"},
        {"TabularData are not supported", "TabularData \u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093"},
        {"Threads","\u30B9\u30EC\u30C3\u30C9"},
        {"ThreadTab.infoLabelFormat", "<html>\u5B9F\u884C\u4E2D: {0}    \u30D4\u30FC\u30AF: {1}    \u5408\u8A08: {2}</html>"},
        {"ThreadTab.threadInfo.accessibleName", "\u30B9\u30EC\u30C3\u30C9\u60C5\u5831"},
        {"ThreadTab.threadPlotter.accessibleName", "\u30B9\u30EC\u30C3\u30C9\u6570\u306E\u30C1\u30E3\u30FC\u30C8\u3002"},
        {"Threshold","\u3057\u304D\u3044\u5024"},
        {"Tile", "\u4E26\u3079\u3066\u8868\u793A"},
        {"Tile.mnemonic", "T"},
        {"Time Range:", "\u6642\u9593\u7BC4\u56F2:"},
        {"Time Range:.mnemonic", "T"},
        {"Time", "\u6642\u9593"},
        {"TimeStamp","TimeStamp"},
        {"Total Loaded", "\u30ED\u30FC\u30C9\u6E08\u5408\u8A08"},
        {"Total classes loaded","\u30ED\u30FC\u30C9\u6E08\u30AF\u30E9\u30B9\u5408\u8A08"},
        {"Total classes unloaded","\u30A2\u30F3\u30ED\u30FC\u30C9\u6E08\u30AF\u30E9\u30B9\u5408\u8A08"},
        {"Total compile time","\u5408\u8A08\u30B3\u30F3\u30D1\u30A4\u30EB\u6642\u9593"},
        {"Total physical memory","\u5408\u8A08\u7269\u7406\u30E1\u30E2\u30EA\u30FC"},
        {"Total threads started","\u958B\u59CB\u6E08\u5408\u8A08\u30B9\u30EC\u30C3\u30C9"},
        {"Total swap space","\u5408\u8A08\u30B9\u30EF\u30C3\u30D7\u30FB\u30B9\u30DA\u30FC\u30B9"},
        {"Type","\u578B"},
        {"Unavailable","\u5229\u7528\u4E0D\u53EF"},
        {"UNKNOWN","UNKNOWN"},
        {"Unknown Host","\u4E0D\u660E\u306A\u30DB\u30B9\u30C8: {0}"},
        {"Unregister", "\u767B\u9332\u89E3\u9664"},
        {"Uptime","\u7A3C\u50CD\u6642\u9593"},
        {"Uptime: ","\u7A3C\u50CD\u6642\u9593: "},
        {"Usage Threshold","\u4F7F\u7528\u3057\u304D\u3044\u5024"},
        {"remoteTF.usage","<b>\u4F7F\u7528\u65B9\u6CD5</b>: &lt;hostname&gt;:&lt;port&gt;\u307E\u305F\u306Fservice:jmx:&lt;protocol&gt;:&lt;sap&gt;"},
        {"Used","\u4F7F\u7528\u6E08"},
        {"Username: ", "\u30E6\u30FC\u30B6\u30FC\u540D: "},
        {"Username: .mnemonic", "U"},
        {"Username.accessibleName", "\u30E6\u30FC\u30B6\u30FC\u540D"},
        {"UserData","UserData"},
        {"Virtual Machine","\u4EEE\u60F3\u30DE\u30B7\u30F3"},
        {"VM arguments","VM\u5F15\u6570"},
        {"VM","VM"},
        {"VMInternalFrame.accessibleDescription", "Java\u4EEE\u60F3\u30DE\u30B7\u30F3\u306E\u30E2\u30CB\u30BF\u30FC\u7528\u306E\u5185\u90E8\u30D5\u30EC\u30FC\u30E0"},
        {"Value","\u5024"},
        {"Vendor", "\u30D9\u30F3\u30C0\u30FC"},
        {"Verbose Output","\u8A73\u7D30\u51FA\u529B"},
        {"Verbose Output.toolTip", "\u30AF\u30E9\u30B9\u8AAD\u8FBC\u307F\u30B7\u30B9\u30C6\u30E0\u3067\u8A73\u7D30\u51FA\u529B\u3092\u6709\u52B9\u306B\u3059\u308B"},
        {"View value", "\u5024\u306E\u8868\u793A"},
        {"View","\u8868\u793A"},
        {"Window", "\u30A6\u30A3\u30F3\u30C9\u30A6"},
        {"Window.mnemonic", "W"},
        {"Windows","\u30A6\u30A3\u30F3\u30C9\u30A6"},
        {"Writable","\u66F8\u8FBC\u307F\u53EF\u80FD"},
        {"You cannot drop a class here", "\u30AF\u30E9\u30B9\u3092\u3053\u3053\u306B\u30C9\u30ED\u30C3\u30D7\u3067\u304D\u307E\u305B\u3093"},
        {"collapse", "\u7E2E\u5C0F"},
        {"connectionFailed1","\u63A5\u7D9A\u306B\u5931\u6557\u3057\u307E\u3057\u305F: \u518D\u8A66\u884C\u3057\u307E\u3059\u304B\u3002"},
        {"connectionFailed2","{0}\u3078\u306E\u63A5\u7D9A\u304C\u6210\u529F\u3057\u307E\u305B\u3093\u3067\u3057\u305F\u3002<br>\u3082\u3046\u4E00\u5EA6\u8A66\u3057\u307E\u3059\u304B\u3002"},
        {"connectionLost1","\u63A5\u7D9A\u304C\u5931\u308F\u308C\u307E\u3057\u305F: \u518D\u63A5\u7D9A\u3057\u307E\u3059\u304B\u3002"},
        {"connectionLost2","\u30EA\u30E2\u30FC\u30C8\u30FB\u30D7\u30ED\u30BB\u30B9\u304C\u7D42\u4E86\u3057\u305F\u305F\u3081{0}\u3078\u306E\u63A5\u7D9A\u304C\u5931\u308F\u308C\u307E\u3057\u305F\u3002<br>\u518D\u63A5\u7D9A\u3057\u307E\u3059\u304B\u3002"},
        {"connectingTo1","{0}\u306B\u63A5\u7D9A\u4E2D"},
        {"connectingTo2","{0}\u306B\u73FE\u5728\u63A5\u7D9A\u4E2D\u3067\u3059\u3002<br>\u3053\u308C\u306B\u306F\u6570\u5206\u304B\u304B\u308A\u307E\u3059\u3002"},
        {"deadlockAllTab","\u3059\u3079\u3066"},
        {"deadlockTab","\u30C7\u30C3\u30C9\u30ED\u30C3\u30AF"},
        {"deadlockTabN","\u30C7\u30C3\u30C9\u30ED\u30C3\u30AF{0}"},
        {"expand", "\u5C55\u958B"},
        {"kbytes","{0} KB"},
        {"operation","\u64CD\u4F5C"},
        {"plot", "\u30D7\u30ED\u30C3\u30C8"},
        {"visualize","\u8996\u899A\u5316"},
        {"zz usage text",
             "\u4F7F\u7528\u65B9\u6CD5: {0} [ -interval=n ] [ -notile ] [ -pluginpath <path> ] [ -version ] [ connection ... ]\n\n  -interval   \u66F4\u65B0\u9593\u9694\u3092n\u79D2\u306B\u8A2D\u5B9A\u3059\u308B(\u30C7\u30D5\u30A9\u30EB\u30C8\u306F4\u79D2)\n  -notile     \u30A6\u30A3\u30F3\u30C9\u30A6\u3092\u6700\u521D\u306B\u4E26\u3079\u3066\u8868\u793A\u3057\u306A\u3044(2\u3064\u4EE5\u4E0A\u306E\u63A5\u7D9A\u306B\u3064\u3044\u3066)\n  -pluginpath JConsole\u304C\u30D7\u30E9\u30B0\u30A4\u30F3\u3092\u53C2\u7167\u3059\u308B\u305F\u3081\u306B\u4F7F\u7528\u3059\u308B\u30D1\u30B9\u3092\u6307\u5B9A\u3059\u308B\n  -version    \u30D7\u30ED\u30B0\u30E9\u30E0\u30FB\u30D0\u30FC\u30B8\u30E7\u30F3\u3092\u5370\u5237\u3059\u308B\n\n  connection = pid || host:port || JMX URL (service:jmx:<protocol>://...)\n  pid         \u30BF\u30FC\u30B2\u30C3\u30C8\u30FB\u30D7\u30ED\u30BB\u30B9\u306E\u30D7\u30ED\u30BB\u30B9ID\n  host        \u30EA\u30E2\u30FC\u30C8\u30FB\u30DB\u30B9\u30C8\u540D\u307E\u305F\u306FIP\u30A2\u30C9\u30EC\u30B9\n  port        \u30EA\u30E2\u30FC\u30C8\u63A5\u7D9A\u7528\u306E\u30DD\u30FC\u30C8\u756A\u53F7\n\n  -J          JConsole\u304C\u5B9F\u884C\u4E2D\u306EJava\u4EEE\u60F3\u30DE\u30B7\u30F3\u3078\u306E\n              \u5165\u529B\u5F15\u6570\u3092\u6307\u5B9A\u3059\u308B"},
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
