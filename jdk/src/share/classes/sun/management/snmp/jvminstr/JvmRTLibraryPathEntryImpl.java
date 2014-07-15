/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.jvminstr;

// java imports
//
import java.io.Serializable;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmRTLibraryPathEntryMBean;

/**
 * The class is used for implementing the "JvmRTLibraryPathEntry" group.
 */
public class JvmRTLibraryPathEntryImpl implements JvmRTLibraryPathEntryMBean,
                                                Serializable {

    static final long serialVersionUID = -3322438153507369765L;
    private final String item;
    private final int index;

    /**
     * Constructor for the "JvmRTLibraryPathEntry" group.
     */
    public JvmRTLibraryPathEntryImpl(String item, int index) {
        this.item  = validPathElementTC(item);
        this.index = index;
    }

    private String validPathElementTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validPathElementTC(str);
    }

    /**
     * Getter for the "JvmRTLibraryPathItem" variable.
     */
    public String getJvmRTLibraryPathItem() throws SnmpStatusException {
        return item;
    }

    /**
     * Getter for the "JvmRTLibraryPathIndex" variable.
     */
    public Integer getJvmRTLibraryPathIndex() throws SnmpStatusException {
        return index;
    }

}
