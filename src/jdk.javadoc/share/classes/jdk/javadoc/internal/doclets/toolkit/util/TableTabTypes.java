/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

/**
 * Interface representing table tab types.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public interface TableTabTypes {

    TableTabs tableTabs();

    public static final class TableTabs {

        private final int value;
        private final String resourceKey;
        private final String tabId;
        private final boolean isDefaultTab;

        private TableTabs(int v, String k, String id, boolean dt) {
            this.value = v;
            this.resourceKey = k;
            this.tabId = id;
            this.isDefaultTab = dt;
        }

        public static TableTabs tab(int value, String resourceKey, String tabId, boolean isDefaultTab) {
            return new TableTabs(value, resourceKey, tabId, isDefaultTab);
        }

        public int value() {
            return this.value;
        }

        public String resourceKey() {
            return this.resourceKey;
        }

        public String tabId() {
            return this.tabId;
        }

        public boolean isDefaultTab() {
            return this.isDefaultTab;
        }
    }
}
