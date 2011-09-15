/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 7068051
 * @summary SIGSEGV in PhaseIdealLoop::build_loop_late_post on T5440
 *
 * @run shell/timeout=300 Test7068051.sh
 */

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

public class Test7068051 {

    public static void main (String[] args) throws Throwable {

        ZipFile zf = new ZipFile(args[0]);

        Enumeration<? extends ZipEntry> entries = zf.entries();
        ArrayList<String> names = new ArrayList<String>();
        while (entries.hasMoreElements()) {
            names.add(entries.nextElement().getName());
        }

        byte[] bytes = new byte[16];
        for (String name : names) {
            ZipEntry e = zf.getEntry(name);

            if (e.isDirectory())
                continue;

            final InputStream is = zf.getInputStream(e);

            try  {
                while (is.read(bytes) >= 0) {
                }
                is.close();

            } catch (IOException x) {
                 System.out.println("..................................");
                 System.out.println("          -->  is :" + is);
                 System.out.println("          is.hash :" + is.hashCode());
                 System.out.println();
                 System.out.println("           e.name :" + e.getName());
                 System.out.println("           e.hash :" + e.hashCode());
                 System.out.println("         e.method :" + e.getMethod());
                 System.out.println("           e.size :" + e.getSize());
                 System.out.println("          e.csize :" + e.getCompressedSize());

                 x.printStackTrace();
                 System.out.println("..................................");
                 System.exit(97);
            }
        }
        zf.close();
    }
}
