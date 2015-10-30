/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.remote;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

import java.util.ArrayList;
import java.util.List;
import static jdk.internal.jshell.remote.RemoteCodes.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * The remote agent runs in the execution process (separate from the main JShell
 * process.  This agent loads code over a socket from the main JShell process,
 * executes the code, and other misc,
 * @author Robert Field
 */
class RemoteAgent {

    private final RemoteClassLoader loader = new RemoteClassLoader();
    private final Map<String, Class<?>> klasses = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        String loopBack = null;
        Socket socket = new Socket(loopBack, Integer.parseInt(args[0]));
        (new RemoteAgent()).commandLoop(socket);
    }

    void commandLoop(Socket socket) throws IOException {
        // in before out -- so we don't hang the controlling process
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        while (true) {
            int cmd = in.readInt();
            switch (cmd) {
                case CMD_EXIT:
                    // Terminate this process
                    return;
                case CMD_LOAD:
                    // Load a generated class file over the wire
                    try {
                        int count = in.readInt();
                        List<String> names = new ArrayList<>(count);
                        for (int i = 0; i < count; ++i) {
                            String name = in.readUTF();
                            byte[] kb = (byte[]) in.readObject();
                            loader.delare(name, kb);
                            names.add(name);
                        }
                        for (String name : names) {
                            Class<?> klass = loader.loadClass(name);
                            klasses.put(name, klass);
                            // Get class loaded to the point of, at least, preparation
                            klass.getDeclaredMethods();
                        }
                        out.writeInt(RESULT_SUCCESS);
                        out.flush();
                    } catch (IOException | ClassNotFoundException | ClassCastException ex) {
                        debug("*** Load failure: %s\n", ex);
                        out.writeInt(RESULT_FAIL);
                        out.writeUTF(ex.toString());
                        out.flush();
                    }
                    break;
                case CMD_INVOKE: {
                    // Invoke executable entry point in loaded code
                    String name = in.readUTF();
                    Class<?> klass = klasses.get(name);
                    if (klass == null) {
                        debug("*** Invoke failure: no such class loaded %s\n", name);
                        out.writeInt(RESULT_FAIL);
                        out.writeUTF("no such class loaded: " + name);
                        out.flush();
                        break;
                    }
                    Method doitMethod;
                    try {
                        doitMethod = klass.getDeclaredMethod(DOIT_METHOD_NAME, new Class<?>[0]);
                        doitMethod.setAccessible(true);
                        Object res;
                        try {
                            clientCodeEnter();
                            res = doitMethod.invoke(null, new Object[0]);
                        } catch (InvocationTargetException ex) {
                            if (ex.getCause() instanceof StopExecutionException) {
                                expectingStop = false;
                                throw (StopExecutionException) ex.getCause();
                            }
                            throw ex;
                        } catch (StopExecutionException ex) {
                            expectingStop = false;
                            throw ex;
                        } finally {
                            clientCodeLeave();
                        }
                        out.writeInt(RESULT_SUCCESS);
                        out.writeUTF(valueString(res));
                        out.flush();
                    } catch (InvocationTargetException ex) {
                        Throwable cause = ex.getCause();
                        StackTraceElement[] elems = cause.getStackTrace();
                        if (cause instanceof RemoteResolutionException) {
                            out.writeInt(RESULT_CORRALLED);
                            out.writeInt(((RemoteResolutionException) cause).id);
                        } else {
                            out.writeInt(RESULT_EXCEPTION);
                            out.writeUTF(cause.getClass().getName());
                            out.writeUTF(cause.getMessage() == null ? "<none>" : cause.getMessage());
                        }
                        out.writeInt(elems.length);
                        for (StackTraceElement ste : elems) {
                            out.writeUTF(ste.getClassName());
                            out.writeUTF(ste.getMethodName());
                            out.writeUTF(ste.getFileName() == null ? "<none>" : ste.getFileName());
                            out.writeInt(ste.getLineNumber());
                        }
                        out.flush();
                    } catch (NoSuchMethodException | IllegalAccessException ex) {
                        debug("*** Invoke failure: %s -- %s\n", ex, ex.getCause());
                        out.writeInt(RESULT_FAIL);
                        out.writeUTF(ex.toString());
                        out.flush();
                    } catch (StopExecutionException ex) {
                        try {
                            out.writeInt(RESULT_KILLED);
                            out.flush();
                        } catch (IOException err) {
                            debug("*** Error writing killed result: %s -- %s\n", ex, ex.getCause());
                        }
                    }
                    System.out.flush();
                    break;
                }
                case CMD_VARVALUE: {
                    // Retrieve a variable value
                    String classname = in.readUTF();
                    String varname = in.readUTF();
                    Class<?> klass = klasses.get(classname);
                    if (klass == null) {
                        debug("*** Var value failure: no such class loaded %s\n", classname);
                        out.writeInt(RESULT_FAIL);
                        out.writeUTF("no such class loaded: " + classname);
                        out.flush();
                        break;
                    }
                    try {
                        Field var = klass.getDeclaredField(varname);
                        var.setAccessible(true);
                        Object res = var.get(null);
                        out.writeInt(RESULT_SUCCESS);
                        out.writeUTF(valueString(res));
                        out.flush();
                    } catch (Exception ex) {
                        debug("*** Var value failure: no such field %s.%s\n", classname, varname);
                        out.writeInt(RESULT_FAIL);
                        out.writeUTF("no such field loaded: " + varname + " in class: " + classname);
                        out.flush();
                    }
                    break;
                }
                case CMD_CLASSPATH: {
                    // Append to the claspath
                    String cp = in.readUTF();
                    for (String path : cp.split(File.pathSeparator)) {
                        loader.addURL(new File(path).toURI().toURL());
                    }
                    out.writeInt(RESULT_SUCCESS);
                    out.flush();
                    break;
                }
                default:
                    debug("*** Bad command code: %d\n", cmd);
                    break;
            }
        }
    }

    // These three variables are used by the main JShell process in interrupting
    // the running process.  Access is via JDI, so the reference is not visible
    // to code inspection.
    private boolean inClientCode; // Queried by the main process
    private boolean expectingStop; // Set by the main process

    // thrown by the main process via JDI:
    private final StopExecutionException stopException = new StopExecutionException();

    @SuppressWarnings("serial")             // serialVersionUID intentionally omitted
    private class StopExecutionException extends ThreadDeath {
        @Override public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    void clientCodeEnter() {
        expectingStop = false;
        inClientCode = true;
    }

    void clientCodeLeave() {
        inClientCode = false;
        while (expectingStop) {
            try {
                Thread.sleep(0);
            } catch (InterruptedException ex) {
                debug("*** Sleep interrupted while waiting for stop exception: %s\n", ex);
            }
        }
    }

    private void debug(String format, Object... args) {
        System.err.printf("REMOTE: "+format, args);
    }

    static String valueString(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + expunge((String)value) + "\"";
        } else if (value instanceof Character) {
            return "'" + value + "'";
        } else {
            return expunge(value.toString());
        }
    }

    static String expunge(String s) {
        StringBuilder sb = new StringBuilder();
        for (String comp : prefixPattern.split(s)) {
            sb.append(comp);
        }
        return sb.toString();
    }
}
