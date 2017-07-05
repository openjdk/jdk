/*
 * Copyright 1998 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.extcheck;

import java.io.*;

/**
 * Main program of extcheck
 */

public final class Main {


    /**
     * Terminates with one of the following codes
     *  1 A newer (or same version) jar file is already installed
     *  0 No newer jar file was found
     *  -1 An internal error occurred
     */
    public static void main(String args[]){

        if (args.length < 1){
            System.err.println("Usage: extcheck [-verbose] <jar file>");
            System.exit(-1);
        }
        int argIndex = 0;
        boolean verboseFlag = false;
        if (args[argIndex].equals("-verbose")){
            verboseFlag = true;
            argIndex++;
        }
        String jarName = args[argIndex];
        argIndex++;
        File jarFile = new File(jarName);
        if (!jarFile.exists()){
            ExtCheck.error("Jarfile " + jarName + " does not exist");
        }
        if (argIndex < args.length) {
            ExtCheck.error("Extra command line argument :"+args[argIndex]);
        }
        ExtCheck jt = ExtCheck.create(jarFile,verboseFlag);
        boolean result = jt.checkInstalledAgainstTarget();
        if (result) {
            System.exit(0);
        } else {
            System.exit(1);
        }

    }

}
