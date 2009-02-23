/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.servicetag;

// This class is a copy of the com.sun.scn.servicetags.WindowsSystemEnvironment
// class from the Sun Connection source.
//
// The Service Tags team maintains the latest version of the implementation
// for system environment data collection.  JDK will include a copy of
// the most recent released version for a JDK release. We rename
// the package to com.sun.servicetag so that the Sun Connection
// product always uses the latest version from the com.sun.scn.servicetags
// package. JDK and users of the com.sun.servicetag API
// (e.g. NetBeans and SunStudio) will use the version in JDK.
//
// So we keep this class in src/share/classes instead of src/<os>/classes.

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows implementation of the SystemEnvironment class.
 */
class WindowsSystemEnvironment extends SystemEnvironment {
    WindowsSystemEnvironment() {
        super();

        // run a call to make sure things are initialized
        // ignore the first call result as the system may
        // give inconsistent data on the first invocation ever
        getWmicResult("computersystem", "get", "model");

        setSystemModel(getWmicResult("computersystem", "get", "model"));
        setSystemManufacturer(getWmicResult("computersystem", "get", "manufacturer"));
        setSerialNumber(getWmicResult("bios", "get", "serialnumber"));

        String cpuMfr = getWmicResult("cpu", "get", "manufacturer");
        // this isn't as good an option, but if we couldn't get anything
        // from wmic, try the processor_identifier
        if (cpuMfr.length() == 0) {
            String procId = System.getenv("processor_identifer");
            if (procId != null) {
                String[] s = procId.split(",");
                cpuMfr = s[s.length - 1].trim();
            }
        }
        setCpuManufacturer(cpuMfr);

        // try to remove the temp file that gets created from running wmic cmds
        try {
            // look in the current working directory
            File f = new File("TempWmicBatchFile.bat");
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
            // ignore the exception
        }
    }


    /**
     * This method invokes wmic outside of the normal environment
     * collection routines.
     *
     * An initial call to wmic can be costly in terms of time.
     *
     * <code>
     * Details of why the first call is costly can be found at:
     *
     * http://support.microsoft.com/kb/290216/en-us
     *
     * "When you run the Wmic.exe utility for the first time, the utility
     * compiles its .mof files into the repository. To save time during
     * Windows installation, this operation takes place as necessary."
     * </code>
     */
    private String getWmicResult(String alias, String verb, String property) {
        String res = "";
        BufferedReader in = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/C", "WMIC", alias, verb, property);
            Process p = pb.start();
            // need this for executing windows commands (at least
            // needed for executing wmic command)
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(
                         new OutputStreamWriter(p.getOutputStream()));
                bw.write(13);
                bw.flush();
            } finally {
                if (bw != null) {
                    bw.close();
                }
            }

            p.waitFor();
            if (p.exitValue() == 0) {
                in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = null;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.length() == 0) {
                        continue;
                    }
                    res = line;
                }
                // return the *last* line read
                return res;
            }

        } catch (Exception e) {
            // ignore the exception
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return res.trim();
    }
}
