/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;
import java.util.*;

/*
 * @test
 * @bug 6439940
 * @summary Cleanup javap implementation
 * @run main/othervm OptionTest
 */
public class OptionTest {
    public static void main(String[] args) throws Exception {
        new OptionTest().run();
    }

    OptionTest() {
        String v = System.getProperty("view.cmd");
        if (v != null) {
            viewResults = true;
            viewCmd = Arrays.asList(v.split(" +"));
        }
    }


    void run() throws Exception {
        int count = 0;
        int pass = 0;
        // try combinations of options and compare old javap against new javap
        for (int i = 0; i < (1<<8); i++) {
            List<String> options = new ArrayList<String>();
            if ((i & 0x01) != 0)
                options.add("-c");
            if ((i & 0x02) != 0)
                options.add("-l");
            if ((i & 0x04) != 0)
                options.add("-public");
            if ((i & 0x08) != 0)
                options.add("-protected");
            if ((i & 0x10) != 0)
                options.add("-package");
            if ((i & 0x20) != 0)
                options.add("-private");
            if ((i & 0x40) != 0)
                options.add("-s");
            if ((i & 0x80) != 0)
                options.add("-verbose");
            count++;
            if (test(options))
                pass++;
        }

        if (pass < count)
            throw new Error(pass + "/" + count + " test cases passed");
    }

    boolean test(List<String> options) throws Exception {
        String[] args = new String[options.size() + 1];
        options.toArray(args);
        args[args.length - 1] = testClassName;
        String oldOut = runOldJavap(args);
        String newOut = runNewJavap(args);
        boolean ok = oldOut.equals(newOut);
        System.err.println((ok ? "pass" : "FAIL") + ": " + options);
        if (!ok && viewResults)
            view(oldOut, newOut);
        return ok;
    }

    String runOldJavap(String[] args) {
        //System.err.println("OLD: " + Arrays.asList(args));
        PrintStream oldOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            sun.tools.javap.Main.entry(args);
        } finally {
            System.setOut(oldOut);
        }
        return out.toString();
    }

    String runNewJavap(String[] args) {
        String[] nArgs = new String[args.length + 2];
        nArgs[0] = "-XDcompat";
        nArgs[1] = "-XDignore.symbol.file";
        System.arraycopy(args, 0, nArgs, 2, args.length);
        //System.err.println("NEW: " + Arrays.asList(nArgs));
        StringWriter out = new StringWriter();
        com.sun.tools.javap.Main.run(nArgs, new PrintWriter(out, true));
        return out.toString();
    }

    File write(String text, String suffix) throws IOException {
        File f = File.createTempFile("OptionTest", suffix);
        FileWriter out = new FileWriter(f);
        out.write(text);
        out.close();
        return f;
    }

    void view(String oldOut, String newOut) throws Exception {
        File oldFile = write(oldOut, "old");
        File newFile = write(newOut, "new");
        List<String> cmd = new ArrayList<String>();
        cmd.addAll(viewCmd);
        cmd.add(oldFile.getPath());
        cmd.add(newFile.getPath());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while ((line = in.readLine()) != null)
            System.err.println(line);
        in.close();
        p.waitFor();
    }

    String testClassName = "java.lang.SecurityManager";
    boolean viewResults;
    List<String> viewCmd;
}
