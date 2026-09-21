/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package sun.tools.attach;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.List;
import java.util.Map;

import jdk.internal.util.OperatingSystem;

import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("restricted")
public class VirtualMachineCoreDumpImpl extends VirtualMachineCoreDump {

    VirtualMachineCoreDumpImpl(AttachProvider provider, String vmid, Map<String, ?> env)
            throws AttachNotSupportedException, IllegalArgumentException, IOException {

        super(provider, vmid, env);
    }

    private static final int HEADER_READ_SIZE = 8;

    protected void checkCoreFile(String filename) throws AttachNotSupportedException, IOException {
        // Verify header of a MiniDump:
        try (InputStream is = new FileInputStream(filename)) {
            byte[] bytes = new byte[HEADER_READ_SIZE];
            int e = is.read(bytes);
            if (e < HEADER_READ_SIZE) {
                throw new AttachNotSupportedException("Truncated file '" + filename + "'");
            }
            if (bytes[0] != 'M' || bytes[1] != 'D' || bytes[2] != 'M' || bytes[3] != 'P') {
                throw new AttachNotSupportedException("Not a MiniDump: '" + filename + "'");
            }
        }
    }

    protected String helperName(String jdkLibDir) {
        return jdkLibDir + File.separator + "revivalhelper.exe";
    }

    protected void setEnv(Map<String, String> env, String jdkLibDir) {
        String editbin = System.getProperty("jdk.attach.core.editbin");
        if (editbin != null) {
            env.put("EDITBIN", editbin);
        }
    }
}
