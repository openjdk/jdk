/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @bug 6474073
 * @summary Make sure zombies don't get created on Unix
 * @author Martin Buchholz
 */

import java.io.*;

public class Zombies {
    public static void main(String[] args) throws Throwable {
        if (! new File("/usr/bin/perl").canExecute() ||
            ! new File("/bin/ps").canExecute())
            return;
        System.out.println("Looks like a Unix system.");
        final Runtime rt = Runtime.getRuntime();

        try {
            rt.exec("no-such-file");
            throw new Error("expected IOException not thrown");
        } catch (IOException _) {/* OK */}

        try {
            rt.exec(".");
            throw new Error("expected IOException not thrown");
        } catch (IOException _) {/* OK */}

        try {
            rt.exec("/bin/true", null, new File("no-such-dir"));
            throw new Error("expected IOException not thrown");
        } catch (IOException _) {/* OK */}

        rt.exec("/bin/true").waitFor();

        // Count all the zombies that are children of this Java process
        final String[] zombieCounter = {
            "/usr/bin/perl", "-e",
            "exit @{[`/bin/ps -eo ppid,s` =~ /^ *@{[getppid]} +Z$/mog]}"
        };

        int zombies = rt.exec(zombieCounter).waitFor();
        if (zombies != 0) throw new Error(zombies + " zombies!");
    }
}
