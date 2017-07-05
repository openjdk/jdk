/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.management;

import java.lang.management.LockInfo;
import java.lang.management.ThreadInfo;
import javax.management.Attribute;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;

/**
 * This MXBean is used for data conversion from LockInfo
 * to CompositeData (its mapped type) or vice versa.
 */
class LockDataConverter extends StandardMBean
         implements LockDataConverterMXBean {
    private LockInfo      lockInfo;
    private LockInfo[]    lockedSyncs;

    LockDataConverter() {
        super(LockDataConverterMXBean.class, true);
        this.lockInfo = null;
        this.lockedSyncs = null;
    }

    LockDataConverter(ThreadInfo ti) {
        super(LockDataConverterMXBean.class, true);
        this.lockInfo = ti.getLockInfo();
        this.lockedSyncs = ti.getLockedSynchronizers();
    }

    public void setLockInfo(LockInfo l) {
        this.lockInfo = l;
    }

    public LockInfo getLockInfo() {
        return this.lockInfo;
    }

    public void setLockedSynchronizers(LockInfo[] l) {
        this.lockedSyncs = l;
    }

    public LockInfo[] getLockedSynchronizers() {
        return this.lockedSyncs;
    }

    // helper methods
    CompositeData toLockInfoCompositeData() {
        try {
            return (CompositeData) getAttribute("LockInfo");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    CompositeData[] toLockedSynchronizersCompositeData() {
        try {
            return (CompositeData[]) getAttribute("LockedSynchronizers");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    LockInfo toLockInfo(CompositeData cd) {
        try {
            setAttribute(new Attribute("LockInfo", cd));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return getLockInfo();
    }

    LockInfo[] toLockedSynchronizers(CompositeData[] cd) {
        try {
            setAttribute(new Attribute("LockedSynchronizers", cd));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return getLockedSynchronizers();
    }

    static CompositeData toLockInfoCompositeData(LockInfo l) {
        LockDataConverter ldc = new LockDataConverter();
        ldc.setLockInfo(l);
        return ldc.toLockInfoCompositeData();
    }
}
