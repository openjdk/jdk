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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

public class JSJavaVM extends DefaultScriptObject {
    private static final int FIELD_ADDRESS_SIZE = 0;
    private static final int FIELD_BUILD_INFO   = 1;
    private static final int FIELD_CPU          = 2;
    private static final int FIELD_FLAGS        = 3;
    private static final int FIELD_HEAP         = 4;
    private static final int FIELD_OS           = 5;
    private static final int FIELD_SYS_PROPS    = 6;
    private static final int FIELD_THREADS      = 7;
    private static final int FIELD_TYPE         = 8;
    private static final int FIELD_VERSION      = 9;
    private static final int FIELD_CLASS_PATH   = 10;
    private static final int FIELD_BOOT_CLASS_PATH  = 11;
    private static final int FIELD_USER_DIR     = 12;
    private static final int FIELD_UNDEFINED    = -1;

    public JSJavaVM(JSJavaFactory factory) {
        this.factory = factory;
        this.vm = VM.getVM();
    }

    public Object get(String name) {
        int fieldID = getFieldID(name);
        switch (fieldID) {
        case FIELD_ADDRESS_SIZE:
            return new Long(getVMBit());
        case FIELD_BUILD_INFO:
            return vm.getVMInternalInfo();
        case FIELD_CPU:
            return vm.getCPU();
        case FIELD_FLAGS:
            return getFlags();
        case FIELD_HEAP:
            return getHeap();
        case FIELD_OS:
            return vm.getOS();
        case FIELD_SYS_PROPS:
            return getSysProps();
        case FIELD_THREADS:
            return getThreads();
        case FIELD_TYPE:
            return getType();
        case FIELD_VERSION:
            return vm.getVMRelease();
        case FIELD_CLASS_PATH:
            return getClassPath();
        case FIELD_BOOT_CLASS_PATH:
            return getBootClassPath();
        case FIELD_USER_DIR:
            return getUserDir();
        case FIELD_UNDEFINED:
        default:
            return super.get(name);
        }
    }

    public Object[] getIds() {
        Object[] superIds = super.getIds();
        Object[] tmp = fields.keySet().toArray();
        Object[] res = new Object[superIds.length + tmp.length];
        System.arraycopy(tmp, 0, res, 0, tmp.length);
        System.arraycopy(superIds, 0, res, tmp.length, superIds.length);
        return res;
    }

    public boolean has(String name) {
        if (getFieldID(name) != FIELD_UNDEFINED) {
            return true;
        } else {
            return super.has(name);
        }
    }

    public void put(String name, Object value) {
        if (getFieldID(name) == FIELD_UNDEFINED) {
            super.put(name, value);
        }
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Java Hotspot ");
        buf.append(getType());
        buf.append(' ');
        buf.append(getVMBit());
        buf.append(" bit VM (build ");
        buf.append(vm.getVMRelease());
        buf.append(")");
        return buf.toString();
    }

    //-- Internals only below this point
    private static Map fields = new HashMap();
    private static void addField(String name, int fieldId) {
        fields.put(name, new Integer(fieldId));
    }

    private static int getFieldID(String name) {
        Integer res = (Integer) fields.get(name);
        return (res != null)? res.intValue() : FIELD_UNDEFINED;
    }

    static {
        addField("addressSize", FIELD_ADDRESS_SIZE);
        addField("buildInfo", FIELD_BUILD_INFO);
        addField("cpu", FIELD_CPU);
        addField("flags", FIELD_FLAGS);
        addField("heap", FIELD_HEAP);
        addField("os", FIELD_OS);
        addField("sysProps", FIELD_SYS_PROPS);
        addField("threads", FIELD_THREADS);
        addField("type", FIELD_TYPE);
        addField("version", FIELD_VERSION);
        addField("classPath", FIELD_CLASS_PATH);
        addField("bootClassPath", FIELD_BOOT_CLASS_PATH);
        addField("userDir", FIELD_USER_DIR);
    }

    private long getVMBit() {
        // address size in bits
        return vm.getAddressSize() * 8;
    }

    private synchronized JSMap getFlags() {
        if (flagsCache == null) {
            VM.Flag[] flags = vm.getCommandLineFlags();
            Map map = new HashMap();
            if (flags != null) {
                for (int f = 0; f < flags.length; f++) {
                    VM.Flag flag = flags[f];
                    map.put(flag.getName(), flag.getValue());
                }
            }
            flagsCache = factory.newJSMap(map);
        }
        return flagsCache;
    }

    private synchronized JSJavaHeap getHeap() {
        if (heapCache == null) {
            heapCache = factory.newJSJavaHeap();
        }
        return heapCache;
    }

    private synchronized JSMap getSysProps() {
        if (sysPropsCache == null) {
            Properties props = vm.getSystemProperties();
            Map map = new HashMap();
            if (props != null) {
                Enumeration e = props.propertyNames();
                while (e.hasMoreElements()) {
                    String key = (String) e.nextElement();
                    map.put(key, props.getProperty(key));
                }
            }
            sysPropsCache = factory.newJSMap(map);
        }
        return sysPropsCache;
    }

    private synchronized JSList getThreads() {
        if (threadsCache == null) {
            List threads = new ArrayList(0);
            threadsCache = factory.newJSList(threads);
            JavaThread jthread = vm.getThreads().first();
            while (jthread != null) {
                threads.add(jthread);
                jthread = jthread.next();
            }
        }
        return threadsCache;
    }

    private String getType() {
        if (vm.isClientCompiler()) {
            return "Client";
        } else if (vm.isServerCompiler()) {
            return "Server";
        } else {
            return "Core";
        }
    }

    private String getClassPath() {
        return vm.getSystemProperty("java.class.path");
    }

    private String getBootClassPath() {
        return vm.getSystemProperty("sun.boot.class.path");
    }

    private String getUserDir() {
        return vm.getSystemProperty("user.dir");
    }

    private JSMap      flagsCache;
    private JSJavaHeap heapCache;
    private JSMap      sysPropsCache;
    private JSList     threadsCache;
    private final JSJavaFactory factory;
    private final VM vm;
}
