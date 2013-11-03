/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.tools;

import java.io.*;
import sun.jvm.hotspot.debugger.JVMDebugger;
import sun.jvm.hotspot.utilities.*;

public class JMap extends Tool {
    public JMap(int m) {
        mode = m;
    }

    public JMap() {
        this(MODE_PMAP);
    }

    public JMap(JVMDebugger d) {
        super(d);
    }

    protected boolean needsJavaPrefix() {
        return false;
    }

    public String getName() {
        return "jmap";
    }

    protected String getCommandFlags() {
        return "-heap|-heap:format=b|-histo|-clstats|-finalizerinfo";
    }

    protected void printFlagsUsage() {
        System.out.println("    <no option>\tto print same info as Solaris pmap");
        System.out.println("    -heap\tto print java heap summary");
        System.out.println("    -heap:format=b\tto dump java heap in hprof binary format");
        System.out.println("    -histo\tto print histogram of java object heap");
        System.out.println("    -clstats\tto print class loader statistics");
        System.out.println("    -finalizerinfo\tto print information on objects awaiting finalization");
        super.printFlagsUsage();
    }

    public static final int MODE_HEAP_SUMMARY = 0;
    public static final int MODE_HISTOGRAM = 1;
    public static final int MODE_CLSTATS = 2;
    public static final int MODE_PMAP = 3;
    public static final int MODE_HEAP_GRAPH_HPROF_BIN = 4;
    public static final int MODE_HEAP_GRAPH_GXL = 5;
    public static final int MODE_FINALIZERINFO = 6;

    public void run() {
        Tool tool = null;
        switch (mode) {

        case MODE_HEAP_SUMMARY:
            tool = new HeapSummary();
            break;

        case MODE_HISTOGRAM:
            tool = new ObjectHistogram();
            break;

        case MODE_CLSTATS:
            tool = new ClassLoaderStats();
            break;

        case MODE_PMAP:
            tool = new PMap();
            break;

        case MODE_HEAP_GRAPH_HPROF_BIN:
            writeHeapHprofBin();
            return;

        case MODE_HEAP_GRAPH_GXL:
            writeHeapGXL();
            return;

        case MODE_FINALIZERINFO:
            tool = new FinalizerInfo();
            break;

        default:
            usage();
            break;
       }

       tool.setAgent(getAgent());
       tool.setDebugeeType(getDebugeeType());
       tool.run();
    }

    public static void main(String[] args) {
        int mode = MODE_PMAP;
        if (args.length > 1 ) {
            String modeFlag = args[0];
            boolean copyArgs = true;
            if (modeFlag.equals("-heap")) {
                mode = MODE_HEAP_SUMMARY;
            } else if (modeFlag.equals("-histo")) {
                mode = MODE_HISTOGRAM;
            } else if (modeFlag.equals("-clstats")) {
                mode = MODE_CLSTATS;
            } else if (modeFlag.equals("-finalizerinfo")) {
                mode = MODE_FINALIZERINFO;
            } else {
                int index = modeFlag.indexOf("-heap:format=");
                if (index != -1) {
                    String format = modeFlag.substring(1 + modeFlag.indexOf('='));
                    if (format.equals("b")) {
                        mode = MODE_HEAP_GRAPH_HPROF_BIN;
                    } else if (format.equals("x")) {
                        mode = MODE_HEAP_GRAPH_GXL;
                    } else {
                        System.err.println("unknown heap format:" + format);

                        // Exit with error status
                        System.exit(1);
                    }
                } else {
                    copyArgs = false;
                }
            }

            if (copyArgs) {
                String[] newArgs = new String[args.length - 1];
                for (int i = 0; i < newArgs.length; i++) {
                    newArgs[i] = args[i + 1];
                }
                args = newArgs;
            }
        }

        JMap jmap = new JMap(mode);
        jmap.execute(args);
    }

    public boolean writeHeapHprofBin(String fileName) {
        try {
            HeapGraphWriter hgw = new HeapHprofBinWriter();
            hgw.write(fileName);
            System.out.println("heap written to " + fileName);
            return true;
        } catch (IOException exp) {
            System.err.println(exp.getMessage());
            return false;
        }
    }

    public boolean writeHeapHprofBin() {
        return writeHeapHprofBin("heap.bin");
    }

    private boolean writeHeapGXL(String fileName) {
        try {
            HeapGraphWriter hgw = new HeapGXLWriter();
            hgw.write(fileName);
            System.out.println("heap written to " + fileName);
            return true;
        } catch (IOException exp) {
            System.err.println(exp.getMessage());
            return false;
        }
    }

    public boolean writeHeapGXL() {
        return writeHeapGXL("heap.xml");
    }

    private int mode;
}
