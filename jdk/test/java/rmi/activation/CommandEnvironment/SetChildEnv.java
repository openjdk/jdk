/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 *
 * @bug 4105043
 * @summary cannot set java.rmi.server.hostname on children of rmid in time
 *
 * @bug 4097357
 * @summary activation group should not overwrite system properties
 *
 * @bug 4107184
 * @summary activation groups should be able to control their JVM properties
 *
 * @author Adrian Colley
 *
 * @library ../../testlibrary
 * @build TestLibrary RMID JavaVM StreamPipe
 * @build Eliza Retireable Doctor Doctor_Stub SetChildEnv
 * @run main/othervm/timeout=240/policy=security.policy -Djava.compiler=NONE  SetChildEnv
 */
import java.rmi.*;
import java.util.Properties;
import java.io.*;
import java.util.StringTokenizer;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.rmi.activation.*;

public class SetChildEnv
{
    public static void main(String argv[])
        throws Exception
    {
        System.out.println("java.compiler=" + System.getProperty("java.compiler"));
        // don't embed spaces in any of the test args/props, because
        // they won't be parsed properly
        runwith (new String[0], new String[0]);

        runwith (
            new String[] { "-verbosegc" },
            new String[] { "foo.bar=SetChildEnvTest",
                           "sun.rmi.server.doSomething=true" }
            );

        runwith (
            new String[] { },
            new String[] { "parameter.count=zero" }
            );

        runwith (
            new String[] { "-Xmx32m" },
            new String[] { }
            );
    }

    private static void runwith(
        String[] params,        // extra args
        String[] props          // extra system properties
    )
        throws Exception
    {
        TestLibrary.suggestSecurityManager(TestParams.defaultSecurityManager);

        // make a "watcher" which listens on a pipe and searches for
        // the debugExec line while teeing to System.err
        DebugExecWatcher watcher = DebugExecWatcher.makeWithPipe();

        RMID.removeLog();
        RMID rmid = RMID.createRMID(watcher.otherEnd(), watcher.otherEnd(),
                                    true); // debugExec turned on

        rmid.start();

        // compile props
        Properties p = new Properties();
        p.put("java.security.policy", TestParams.defaultGroupPolicy);
        p.put("java.security.manager", TestParams.defaultSecurityManager);
        //p.put("java.rmi.server.logCalls", "true");
        int i;
        for (i = 0; i < props.length; i++) {
            p.put(props[i].substring(0, props[i].indexOf('=')),
                  props[i].substring(props[i].indexOf('=')+1));
        }

        // create CommandEnvironment and ActivationGroupDesc
        ActivationGroupDesc.CommandEnvironment cmdenv =
                new ActivationGroupDesc.CommandEnvironment(
                    null,
                    params);

        ActivationGroupDesc gdesc = new ActivationGroupDesc(
                p, cmdenv);

        // register group
        ActivationSystem actsys = ActivationGroup.getSystem();
        ActivationGroupID gid = actsys.registerGroup(gdesc);

        // create ActivationDesc
        ActivationDesc odesc = new ActivationDesc(gid, // group
                                                  "Doctor", // class
                                                  null, // codesource
                                                  null); // closure data

        // register activatable object
        Eliza doctor = (Eliza)Activatable.register(odesc);

        // invoke a call with oh-so-humorous sample text
        System.out.println ("Invoking complain()...");
        String complaint =
                "HELP ME, DOCTOR.  I FEEL VIOLENT TOWARDS PEOPLE " +
                "WHO INQUIRE ABOUT MY PARENTS.";

        System.out.println(complaint);
        //Runtime.getRuntime().traceMethodCalls(true);
        String res = doctor.complain(complaint);
        //Runtime.getRuntime().traceMethodCalls(false);
        System.out.println (" => " + res);

        // Get debugExec line, allowing 15 seconds for it to flush
        // through the buffers and pipes.
        String found = watcher.found;
        if (found == null) {
            int fudge = 15;
            while (found == null && --fudge > 0) {
                Thread.sleep(1000);
                found = watcher.found;
            }
            if (found == null) {
                TestLibrary.bomb("rmid subprocess produced no " +
                                 "recognizable debugExec line");
            }
        }

        System.err.println("debugExec found: <<" + found + ">>");
        // q: first double-quote after debugExec
        int q = found.indexOf('"', found.indexOf("rmid: debugExec"));
        // qe: last double-quote on debugExec line
        int qe = found.lastIndexOf('"');
        if (q <= 1 || qe <= q) {
            TestLibrary.bomb("rmid subprocess produced " +
                             "mangled debugExec line");
        }

        // split args by whitespace
        StringTokenizer tk = new StringTokenizer(found.substring(q+1, qe));
        tk.nextToken();         // skip command path/name

        // Now check off the requested args.  Order isn't important, and
        // any extra args are ignored, even if they're inconsistent or
        // bargage, or duplicates.

        Set argset = new HashSet(tk.countTokens());
        while (tk.hasMoreTokens()) {
            argset.add(tk.nextToken());
        }

        int m;
        for (m = 0; m < params.length; m++) {
            if(!argset.contains(params[m]))
                TestLibrary.bomb("Parameter \"" + params[m] + "\" not set");
        }

        for (m = 0; m < props.length; m++) {
            if (!argset.contains("-D" + props[m])) {
                TestLibrary.bomb("Property binding \"" + props[m] +
                                 "\" not set");
            }
        }

        // End doctor
        if (doctor instanceof Retireable)
            ((Retireable)doctor).retire();
        actsys.unregisterGroup(gid);

        Thread.sleep(5000);
        rmid.destroy();
    }

    public static class DebugExecWatcher
        extends Thread
    {
        public String found;
        private BufferedReader str;
        private OutputStream otherEnd;

        private DebugExecWatcher(InputStream readStream, OutputStream wrStream)
        {
            super("DebugExecWatcher");
            found = null;
            str = new BufferedReader(new InputStreamReader(readStream));
            otherEnd = wrStream;
        }

        static public DebugExecWatcher makeWithPipe()
            throws IOException
        {
            PipedOutputStream wr = new PipedOutputStream();
            PipedInputStream rd = new PipedInputStream(wr);
            DebugExecWatcher embryo = new DebugExecWatcher(rd, wr);
            embryo.start();
            return embryo;
        }

        public OutputStream otherEnd()
        {
            return otherEnd;
        }

        public synchronized void notifyLine(String s)
        {
            if (s != null && s.indexOf("rmid: debugExec") != -1)
                found = s;
        }

        public void run()
        {
            try {
                String line;
                while ((line = str.readLine()) != null) {
                    this.notifyLine(line);
                    System.err.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

/*
   code graveyard

        // activation should have proceeded by writing a wrapper.out
        // when test.src/actgrpwrapper was run.

        // Read and check wrapper.out
        BufferedReader r = new BufferedReader(new FileReader(wrapout));
        String[] realArgs = null;
        String line;

        while ( (line = r.readLine()) != null) {
            StringTokenizer tkz = new StringTokenizer(line);
            if (!tkz.nextToken().equals("actgrpwrapper")) {
                // could throw an exception, but let's benignly
                // assume that something unrelated is spewing.
                continue;
            }
            String x;   // writer's block
            x = tkz.nextToken();
            if (x.equals("argc")) {
                if (realArgs != null) {
                    throw new RuntimeException(
                            "SetChildEnv: two argc lines in wrapper.out");
                }
                realArgs = new String[Integer.parseInt(tkz.nextToken())];
            } else if (x.equals("argv")) {
                if (realArgs == null)
                    throw new RuntimeException("SetChildEnv: missing argc");
                int n = Integer.parseInt(tkz.nextToken());
                if (n < 1 || n > realArgs.length) {
                    throw new RuntimeException("SetChildEnv: argc=" +
                            realArgs.length + "; argv[" + n + "]");
                }
                // Hack: manually skip the "actgrpwrapper argv 5 "
                String remainder = line.substring(
                        1 + line.indexOf(' ',
                                1 + line.indexOf(' ',
                                        1 + line.indexOf(' '))));
                realArgs[n-1] = remainder;
            } else {
                throw new RuntimeException("SetChildEnv: bad token \"" + x + "\"");
            }
        }
        r.close();

    private static void ensureLocalExecutable(String fname)
        throws Exception
    {
        File target = new File(fname);
        File source = new File(Dot, fname);
        if (!target.exists()) {
            // copy from source
            System.err.println("Copying " + source.getPath() +
                               " to " + target.getPath());
            java.io.InputStream in = new java.io.FileInputStream(source);
            java.io.OutputStream out = new java.io.FileOutputStream(target);
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf, 0, 512)) > 0) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
        }
        // chmod
        System.err.println("Doing: /bin/chmod 755 " + fname);
        Runtime.getRuntime().exec("/bin/chmod 755 " + fname).waitFor();
    }

*/
