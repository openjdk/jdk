/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 */


/*
 *
 * Support classes for running jhat tests
 *
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.PrintStream;

/*
 * Helper class to direct process output to a StringBuffer
 */
class MyInputStream implements Runnable {
    private String              name;
    private BufferedInputStream in;
    private StringBuffer        buffer;

    /* Create MyInputStream that saves all output to a StringBuffer */
    MyInputStream(String name, InputStream in) {
        this.name = name;
        this.in = new BufferedInputStream(in);
        buffer = new StringBuffer(4096);
        Thread thr = new Thread(this);
        thr.setDaemon(true);
        thr.start();
    }

    /* Dump the buffer */
    void dump(PrintStream x) {
        String str = buffer.toString();
        x.println("<beginning of " + name + " buffer>");
        x.println(str);
        x.println("<end of buffer>");
    }

    /* Check to see if a pattern is inside the output. */
    boolean contains(String pattern) {
        String str = buffer.toString();
        return str.contains(pattern);
    }

    /* Runs as a separate thread capturing all output in a StringBuffer */
    public void run() {
        try {
            byte b[] = new byte[100];
            for (;;) {
                int n = in.read(b);
                String str;
                if (n < 0) {
                    break;
                }
                str = new String(b, 0, n);
                buffer.append(str);
                System.out.print(str);
            }
        } catch (IOException ioe) { /* skip */ }
    }
}

/*
 * Main jhat run
 */
public class HatRun {

    private String        all_hprof_options;
    private String        all_hat_options;
    private String        dumpfile;
    private MyInputStream output;
    private MyInputStream error;

    /* Create a Hat run process */
    public HatRun(String hprof_options, String hat_options)
    {
        all_hprof_options = hprof_options;
        all_hat_options   = hat_options;
    }

    /*
     * Execute a process with an -agentpath or -agentlib command option
     */
    public void runit(String class_name)
    {
        runit(class_name, null);
    }

    /*
     * Execute a command.
     */
    private void execute(String cmd[])
    {
        /* Begin process */
        Process p;
        String cmdLine = "";
        int i;

        for ( i = 0 ; i < cmd.length; i++ ) {
          cmdLine += cmd[i];
          cmdLine += " ";
        }
        System.out.println("Starting: " + cmdLine);

        try {
            p = Runtime.getRuntime().exec(cmd);
        } catch ( IOException e ) {
            throw new RuntimeException("Test failed - exec got IO exception");
        }

        /* Save process output in StringBuffers */
        output = new MyInputStream("Input Stream", p.getInputStream());
        error  = new MyInputStream("Error Stream", p.getErrorStream());

        /* Wait for process to complete, and if exit code is non-zero we fail */
        try {
            int exitStatus;
            exitStatus = p.waitFor();
            if ( exitStatus != 0) {
                System.out.println("Exit code is " + exitStatus);
                error.dump(System.out);
                output.dump(System.out);
                throw new RuntimeException("Test failed - " +
                                    "exit return code non-zero " +
                                    "(exitStatus==" + exitStatus + ")");
            }
        } catch ( InterruptedException e ) {
            throw new RuntimeException("Test failed - process interrupted");
        }
        System.out.println("Completed: " + cmdLine);
    }

    /*
     * Execute a process with an -agentpath or -agentlib command option
     *    plus any set of other java options.
     */
    public void runit(String class_name, String vm_options[])
    {
        String jre_home  = System.getProperty("java.home");
        String sdk_home  = (jre_home.endsWith("jre") ?
                            (jre_home + File.separator + "..") :
                            jre_home );
        String cdir      = System.getProperty("test.classes", ".");
        String os_arch   = System.getProperty("os.arch");
        String os_name   = System.getProperty("os.name");
        boolean d64      = os_name.equals("SunOS") && (
                             os_arch.equals("sparcv9") ||
                             os_arch.equals("amd64"));
        String isa_dir   = d64?(File.separator+os_arch):"";
        String java      = jre_home
                             + File.separator + "bin" + isa_dir
                             + File.separator + "java";
        String jhat      = sdk_home + File.separator + "bin"
                           + File.separator + "jhat";
        /* Array of strings to be passed in for exec:
         *   1. java
         *   2. -Dtest.classes=.
         *   3. -d64                 (optional)
         *   4. -Xcheck:jni          (Just because it finds bugs)
         *   5. -Xverify:all         (Make sure verification is on full blast)
         *   6. -agent
         *       vm_options
         *   7+i. classname
         */
        int nvm_options = 0;
        if ( vm_options != null ) nvm_options = vm_options.length;
        String cmd[]     = new String[1 + (d64?1:0) + 7 + nvm_options];
        int i,j;

        i = 0;
        cmd[i++] = java;
        cmd[i++] = "-cp";
        cmd[i++] = cdir;
        cmd[i++] = "-Dtest.classes=" + cdir;
        if ( d64 ) {
            cmd[i++] = "-d64";
        }
        cmd[i++] = "-Xcheck:jni";
        cmd[i++] = "-Xverify:all";
        dumpfile= cdir + File.separator + class_name + ".hdump";
        cmd[i++] = "-agentlib:hprof=" + all_hprof_options
                    + ",format=b,file=" + dumpfile;
        /* Add any special VM options */
        for ( j = 0; j < nvm_options; j++ ) {
            cmd[i++] = vm_options[j];
        }
        /* Add classname */
        cmd[i++] = class_name;

        /* Execute process */
        execute(cmd);

        /* Run jhat */
        String jhat_cmd[] = new String[4];
        jhat_cmd[0] = jhat;
        jhat_cmd[1] = "-debug";
        jhat_cmd[2] = "2";
        jhat_cmd[3] = dumpfile;

        /* Execute process */
        execute(jhat_cmd);

    }

    /* Does the pattern appear in the output of this process */
    public boolean output_contains(String pattern)
    {
        return output.contains(pattern) || error.contains(pattern);
    }
}
