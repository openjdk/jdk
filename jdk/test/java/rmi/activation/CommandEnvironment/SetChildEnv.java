/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 *          java.base/sun.nio.ch
 * @build TestLibrary RMID ActivationLibrary RMIDSelectorProvider
 *     Eliza Retireable Doctor Doctor_Stub
 * @run main/othervm/timeout=240/policy=security.policy SetChildEnv 0 0
 * @run main/othervm/timeout=240/policy=security.policy SetChildEnv 1 -verbosegc
 *                       2 foo.bar=SetChildEnvTest sun.rmi.server.doSomething=true
 * @run main/othervm/timeout=240/policy=security.policy SetChildEnv 0 1 parameter.count=zero
 * @run main/othervm/timeout=240/policy=security.policy SetChildEnv 1 -Xmx32m 0
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
    public static void main(String argv[]) throws Exception {
        RMID rmid = null;
        try {
            System.out.println("java.compiler=" + System.getProperty("java.compiler"));
            int paramCount = Integer.valueOf(argv[0]);
            String[] params = paramCount == 0 ?
                    new String[0] : Arrays.copyOfRange(argv, 1, paramCount+1);
            int propCount = Integer.valueOf(argv[paramCount+1]);
            String[] props = propCount == 0 ?
                    new String[0] :
                    Arrays.copyOfRange(argv, paramCount+2, paramCount+propCount+2);

            TestLibrary.suggestSecurityManager(TestParams.defaultSecurityManager);

            // make a "watcher" which listens on a pipe and searches for
            // the debugExec line while teeing to System.err
            DebugExecWatcher watcher = DebugExecWatcher.makeWithPipe();

            RMID.removeLog();
            rmid = RMID.createRMIDOnEphemeralPort(watcher.otherEnd(),
                                                  watcher.otherEnd(), true);

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
        } finally {
            Thread.sleep(5000);
            if (rmid != null) {
                rmid.cleanup();
            }
        }
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
                /* During termination of distant rmid, StreamPipes will be broken when
                 * distant vm terminates. A "Pipe broken" exception is expected because
                 * DebugExecWatcher points to the same streams as StreamPipes used by RMID.
                 * If we get this exception. We just terminate the thread.
                 */
                if (e.getMessage().equals("Pipe broken")) {
                    try {
                        str.close();
                    } catch (IOException ioe) {}
                }
                else {
                    e.printStackTrace();
                }
            }
        }
    }
}
