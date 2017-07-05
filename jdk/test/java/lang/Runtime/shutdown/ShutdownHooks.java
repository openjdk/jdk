/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @bug      6829503
 * @summary  1) Test Console and DeleteOnExitHook can be initialized
 *              while shutdown is in progress
 *           2) Test if files that are added by the application shutdown
 *              hook are deleted on exit during shutdown
 */
import java.io.*;
public class ShutdownHooks {
    private static File file;
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ShutdownHooks <dir> <filename>");
        }

        // Add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Cleaner());

        File dir = new File(args[0]);
        file = new File(dir, args[1]);
        // write to file
        System.out.println("writing to "+ file);
        PrintWriter pw = new PrintWriter(file);
        pw.println("Shutdown begins");
        pw.close();
    }

    public static class Cleaner extends Thread {
        public void run() {
            // register the Console's shutdown hook while the application
            // shutdown hook is running
            Console cons = System.console();
            // register the DeleteOnExitHook while the application
            // shutdown hook is running
            file.deleteOnExit();
            try {
                PrintWriter pw = new PrintWriter(file);
                pw.println("file is being deleted");
                pw.close();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
