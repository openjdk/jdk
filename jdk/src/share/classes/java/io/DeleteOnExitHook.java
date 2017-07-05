/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.io;

import java.util.*;
import java.io.File;

/**
 * This class holds a set of filenames to be deleted on VM exit through a shutdown hook.
 * A set is used both to prevent double-insertion of the same file as well as offer
 * quick removal.
 */

class DeleteOnExitHook {
    static {
         sun.misc.SharedSecrets.getJavaLangAccess()
             .registerShutdownHook(2 /* Shutdown hook invocation order */,
                 new Runnable() {
                     public void run() {
                        runHooks();
                     }
                 });
    }

    private static LinkedHashSet<String> files = new LinkedHashSet<String>();

    private DeleteOnExitHook() {}

    static synchronized void add(String file) {
        if(files == null)
            throw new IllegalStateException("Shutdown in progress");

        files.add(file);
    }

    static void runHooks() {
        LinkedHashSet<String> theFiles;

        synchronized (DeleteOnExitHook.class) {
            theFiles = files;
            files = null;
        }

        ArrayList<String> toBeDeleted = new ArrayList<String>(theFiles);

        // reverse the list to maintain previous jdk deletion order.
        // Last in first deleted.
        Collections.reverse(toBeDeleted);
        for (String filename : toBeDeleted) {
            (new File(filename)).delete();
        }
    }
}
