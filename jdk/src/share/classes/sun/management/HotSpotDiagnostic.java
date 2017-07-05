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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.management.ObjectName;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

/**
 * Implementation of the diagnostic MBean for Hotspot VM.
 */
public class HotSpotDiagnostic implements HotSpotDiagnosticMXBean {
    public HotSpotDiagnostic() {
    }

    public native void dumpHeap(String outputFile, boolean live) throws IOException;

    public List<VMOption> getDiagnosticOptions() {
        List<Flag> allFlags = Flag.getAllFlags();
        List<VMOption> result = new ArrayList<VMOption>();
        for (Flag flag : allFlags) {
            if (flag.isWriteable() && flag.isExternal()) {
                result.add(flag.getVMOption());
            }
        }
        return result;
    }

    public VMOption getVMOption(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        Flag f = Flag.getFlag(name);
        if (f == null) {
            throw new IllegalArgumentException("VM option \"" +
                name + "\" does not exist");
        }
        return f.getVMOption();
    }

    public void setVMOption(String name, String value) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }

        Util.checkControlAccess();
        Flag flag = Flag.getFlag(name);
        if (flag == null) {
            throw new IllegalArgumentException("VM option \"" +
                name + "\" does not exist");
        }
        if (!flag.isWriteable()){
            throw new IllegalArgumentException("VM Option \"" +
                name + "\" is not writeable");
        }

        // Check the type of the value
        Object v = flag.getValue();
        if (v instanceof Long) {
            try {
                long l = Long.parseLong(value);
                Flag.setLongValue(name, l);
            } catch (NumberFormatException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException("Invalid value:" +
                        " VM Option \"" + name + "\"" +
                        " expects numeric value");
                iae.initCause(e);
                throw iae;
            }
        } else if (v instanceof Boolean) {
            if (!value.equalsIgnoreCase("true") &&
                !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Invalid value:" +
                    " VM Option \"" + name + "\"" +
                    " expects \"true\" or \"false\".");
            }
            Flag.setBooleanValue(name, Boolean.parseBoolean(value));
        } else if (v instanceof String) {
            Flag.setStringValue(name, value);
        } else {
            throw new IllegalArgumentException("VM Option \"" +
                name + "\" is of an unsupported type: " +
                v.getClass().getName());
        }
    }

    public ObjectName getObjectName() {
        return Util.newObjectName("com.sun.management:type=HotSpotDiagnostic");
    }
}
