/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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


/* DemoRun:
 *
 * Support classes for java jvmti demo tests
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
 * Main JVMTI Demo Run class.
 */
public class DemoRun {

    private String        demo_name;
    private String        demo_options;
    private MyInputStream output;
    private MyInputStream error;

    /* Create a Demo run process */
    public DemoRun(String name, String options)
    {
        demo_name    = name;
        demo_options = options;
    }

    /*
     * Execute a process with an -agentpath or -agentlib command option
     */
    public void runit(String class_name)
    {
        runit(class_name, null);
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
        String libprefix = os_name.contains("Windows")?"":"lib";
        String libsuffix = os_name.contains("Windows")?".dll":
                                os_name.startsWith("Mac OS")?".dylib":".so";
        boolean d64      =    ( os_name.contains("Solaris") ||
                                os_name.contains("SunOS") )
                           && ( os_arch.equals("sparcv9") ||
                                os_arch.equals("amd64"));
        boolean hprof    = demo_name.equals("hprof");
        String isa_dir   = d64?(File.separator+os_arch):"";
        String java      = jre_home
                             + File.separator + "bin" + isa_dir
                             + File.separator + "java";
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
        String cmdLine;
        int exitStatus;
        int i,j;

        i = 0;
        cmdLine = "";
        cmdLine += (cmd[i++] = java);
        cmdLine += " ";
        cmdLine += (cmd[i++] = "-cp");
        cmdLine += " ";
        cmdLine += (cmd[i++] = cdir);
        cmdLine += " ";
        cmdLine += (cmd[i++] = "-Dtest.classes=" + cdir);
        if ( d64 ) {
            cmdLine += " ";
            cmdLine += (cmd[i++] = "-d64");
        }
        cmdLine += " ";
        cmdLine += (cmd[i++] = "-Xcheck:jni");
        cmdLine += " ";
        cmdLine += (cmd[i++] = "-Xverify:all");
        if ( hprof ) {
            /* Load hprof with -agentlib since it's part of jre */
            cmdLine += " ";
            cmdLine += (cmd[i++] = "-agentlib:" + demo_name
                     + (demo_options.equals("")?"":("="+demo_options)));
        } else {
            String libname  = sdk_home
                         + File.separator + "demo"
                         + File.separator + "jvmti"
                         + File.separator + demo_name
                         + File.separator + "lib" + isa_dir
                         + File.separator + libprefix + demo_name + libsuffix;
            cmdLine += " ";
            cmdLine += (cmd[i++] = "-agentpath:" + libname
                     + (demo_options.equals("")?"":("="+demo_options)));
        }
        /* Add any special VM options */
        for ( j = 0; j < nvm_options; j++ ) {
            cmdLine += " ";
            cmdLine += (cmd[i++] = vm_options[j]);
        }
        /* Add classname */
        cmdLine += " ";
        cmdLine += (cmd[i++] = class_name);

        /* Begin process */
        Process p;

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

    /* Does the pattern appear in the output of this process */
    public boolean output_contains(String pattern)
    {
        return output.contains(pattern) || error.contains(pattern);
    }
}
