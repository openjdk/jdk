/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.tools.*;

/**
 * Java Compiler Server.  Can be used to speed up a set of (small)
 * compilation tasks by caching jar files between compilations.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
class Server implements Runnable {
    private final BufferedReader in;
    private final OutputStream out;
    private final boolean isSocket;
    private static final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
    private static final Logger logger = Logger.getLogger("com.sun.tools.javac");
    static class CwdFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        String cwd;
        CwdFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }
        String getAbsoluteName(String name) {
            if (new File(name).isAbsolute()) {
                return name;
            } else {
                return new File(cwd,name).getPath();
            }
        }
//      public JavaFileObject getFileForInput(String name)
//          throws IOException
//      {
//          return super.getFileForInput(getAbsoluteName(name));
//      }
    }
    // static CwdFileManager fm = new CwdFileManager(tool.getStandardFileManager());
    static final StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null);
    static {
        // Use the same file manager for all compilations.  This will
        // cache jar files in the standard file manager.  Use
        // tool.getStandardFileManager().close() to release.
        // FIXME tool.setFileManager(fm);
        logger.setLevel(java.util.logging.Level.SEVERE);
    }
    private Server(BufferedReader in, OutputStream out, boolean isSocket) {
        this.in = in;
        this.out = out;
        this.isSocket = isSocket;
    }
    private Server(BufferedReader in, OutputStream out) {
        this(in, out, false);
    }
    private Server(Socket socket) throws IOException, UnsupportedEncodingException {
        this(new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8")),
             socket.getOutputStream(),
             true);
    }
    public void run() {
        List<String> args = new ArrayList<String>();
        int res = -1;
        try {
            String line = null;
            try {
                line = in.readLine();
            } catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                System.exit(0);
                line = null;
            }
            // fm.cwd=null;
            String cwd = null;
            while (line != null) {
                if (line.startsWith("PWD:")) {
                    cwd = line.substring(4);
                } else if (line.equals("END")) {
                    break;
                } else if (!"-XDstdout".equals(line)) {
                    args.add(line);
                }
                try {
                    line = in.readLine();
                } catch (IOException e) {
                    System.err.println(e.getLocalizedMessage());
                    System.exit(0);
                    line = null;
                }
            }
            Iterable<File> path = cwd == null ? null : Arrays.<File>asList(new File(cwd));
            // try { in.close(); } catch (IOException e) {}
            long msec = System.currentTimeMillis();
            try {
                synchronized (tool) {
                    for (StandardLocation location : StandardLocation.values())
                        fm.setLocation(location, path);
                    res = compile(out, fm, args);
                    // FIXME res = tool.run((InputStream)null, null, out, args.toArray(new String[args.size()]));
                }
            } catch (Throwable ex) {
                logger.log(java.util.logging.Level.SEVERE, args.toString(), ex);
                PrintWriter p = new PrintWriter(out, true);
                ex.printStackTrace(p);
                p.flush();
            }
            if (res >= 3) {
                logger.severe(String.format("problem: %s", args));
            } else {
                logger.info(String.format("success: %s", args));
            }
            // res = compile(args.toArray(new String[args.size()]), out);
            msec -= System.currentTimeMillis();
            logger.info(String.format("Real time: %sms", -msec));
        } finally {
            if (!isSocket) {
                try { in.close(); } catch (IOException e) {}
            }
            try {
                out.write(String.format("EXIT: %s%n", res).getBytes());
            } catch (IOException ex) {
                logger.log(java.util.logging.Level.SEVERE, args.toString(), ex);
            }
            try {
                out.flush();
                out.close();
            } catch (IOException ex) {
                logger.log(java.util.logging.Level.SEVERE, args.toString(), ex);
            }
            logger.info(String.format("EXIT: %s", res));
        }
    }
    public static void main(String... args) throws FileNotFoundException {
        if (args.length == 2) {
            for (;;) {
                throw new UnsupportedOperationException("TODO");
//              BufferedReader in = new BufferedReader(new FileReader(args[0]));
//              PrintWriter out = new PrintWriter(args[1]);
//              new Server(in, out).run();
//              System.out.flush();
//              System.err.flush();
            }
        } else {
            ExecutorService pool = Executors.newCachedThreadPool();
            try
                {
                ServerSocket socket = new ServerSocket(0xcafe, -1, null);
                for (;;) {
                    pool.execute(new Server(socket.accept()));
                }
            }
            catch (IOException e) {
                System.err.format("Error: %s%n", e.getLocalizedMessage());
                pool.shutdown();
            }
        }
    }

    private int compile(OutputStream out, StandardJavaFileManager fm, List<String> args) {
        // FIXME parse args and use getTask
        // System.err.println("Running " + args);
        return tool.run(null, null, out, args.toArray(new String[args.size()]));
    }
}
