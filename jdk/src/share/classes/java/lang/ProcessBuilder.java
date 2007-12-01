/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is used to create operating system processes.
 *
 * <p>Each <code>ProcessBuilder</code> instance manages a collection
 * of process attributes.  The {@link #start()} method creates a new
 * {@link Process} instance with those attributes.  The {@link
 * #start()} method can be invoked repeatedly from the same instance
 * to create new subprocesses with identical or related attributes.
 *
 * <p>Each process builder manages these process attributes:
 *
 * <ul>
 *
 * <li>a <i>command</i>, a list of strings which signifies the
 * external program file to be invoked and its arguments, if any.
 * Which string lists represent a valid operating system command is
 * system-dependent.  For example, it is common for each conceptual
 * argument to be an element in this list, but there are operating
 * systems where programs are expected to tokenize command line
 * strings themselves - on such a system a Java implementation might
 * require commands to contain exactly two elements.
 *
 * <li>an <i>environment</i>, which is a system-dependent mapping from
 * <i>variables</i> to <i>values</i>.  The initial value is a copy of
 * the environment of the current process (see {@link System#getenv()}).
 *
 * <li>a <i>working directory</i>.  The default value is the current
 * working directory of the current process, usually the directory
 * named by the system property <code>user.dir</code>.
 *
 * <li>a <i>redirectErrorStream</i> property.  Initially, this property
 * is <code>false</code>, meaning that the standard output and error
 * output of a subprocess are sent to two separate streams, which can
 * be accessed using the {@link Process#getInputStream()} and {@link
 * Process#getErrorStream()} methods.  If the value is set to
 * <code>true</code>, the standard error is merged with the standard
 * output.  This makes it easier to correlate error messages with the
 * corresponding output.  In this case, the merged data can be read
 * from the stream returned by {@link Process#getInputStream()}, while
 * reading from the stream returned by {@link
 * Process#getErrorStream()} will get an immediate end of file.
 *
 * </ul>
 *
 * <p>Modifying a process builder's attributes will affect processes
 * subsequently started by that object's {@link #start()} method, but
 * will never affect previously started processes or the Java process
 * itself.
 *
 * <p>Most error checking is performed by the {@link #start()} method.
 * It is possible to modify the state of an object so that {@link
 * #start()} will fail.  For example, setting the command attribute to
 * an empty list will not throw an exception unless {@link #start()}
 * is invoked.
 *
 * <p><strong>Note that this class is not synchronized.</strong>
 * If multiple threads access a <code>ProcessBuilder</code> instance
 * concurrently, and at least one of the threads modifies one of the
 * attributes structurally, it <i>must</i> be synchronized externally.
 *
 * <p>Starting a new process which uses the default working directory
 * and environment is easy:
 *
 * <blockquote><pre>
 * Process p = new ProcessBuilder("myCommand", "myArg").start();
 * </pre></blockquote>
 *
 * <p>Here is an example that starts a process with a modified working
 * directory and environment:
 *
 * <blockquote><pre>
 * ProcessBuilder pb = new ProcessBuilder("myCommand", "myArg1", "myArg2");
 * Map&lt;String, String&gt; env = pb.environment();
 * env.put("VAR1", "myValue");
 * env.remove("OTHERVAR");
 * env.put("VAR2", env.get("VAR1") + "suffix");
 * pb.directory(new File("myDir"));
 * Process p = pb.start();
 * </pre></blockquote>
 *
 * <p>To start a process with an explicit set of environment
 * variables, first call {@link java.util.Map#clear() Map.clear()}
 * before adding environment variables.
 *
 * @since 1.5
 */

public final class ProcessBuilder
{
    private List<String> command;
    private File directory;
    private Map<String,String> environment;
    private boolean redirectErrorStream;

    /**
     * Constructs a process builder with the specified operating
     * system program and arguments.  This constructor does <i>not</i>
     * make a copy of the <code>command</code> list.  Subsequent
     * updates to the list will be reflected in the state of the
     * process builder.  It is not checked whether
     * <code>command</code> corresponds to a valid operating system
     * command.</p>
     *
     * @param   command  The list containing the program and its arguments
     *
     * @throws  NullPointerException
     *          If the argument is <code>null</code>
     */
    public ProcessBuilder(List<String> command) {
        if (command == null)
            throw new NullPointerException();
        this.command = command;
    }

    /**
     * Constructs a process builder with the specified operating
     * system program and arguments.  This is a convenience
     * constructor that sets the process builder's command to a string
     * list containing the same strings as the <code>command</code>
     * array, in the same order.  It is not checked whether
     * <code>command</code> corresponds to a valid operating system
     * command.</p>
     *
     * @param   command  A string array containing the program and its arguments
     */
    public ProcessBuilder(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command)
            this.command.add(arg);
    }

    /**
     * Sets this process builder's operating system program and
     * arguments.  This method does <i>not</i> make a copy of the
     * <code>command</code> list.  Subsequent updates to the list will
     * be reflected in the state of the process builder.  It is not
     * checked whether <code>command</code> corresponds to a valid
     * operating system command.</p>
     *
     * @param   command  The list containing the program and its arguments
     * @return  This process builder
     *
     * @throws  NullPointerException
     *          If the argument is <code>null</code>
     */
    public ProcessBuilder command(List<String> command) {
        if (command == null)
            throw new NullPointerException();
        this.command = command;
        return this;
    }

    /**
     * Sets this process builder's operating system program and
     * arguments.  This is a convenience method that sets the command
     * to a string list containing the same strings as the
     * <code>command</code> array, in the same order.  It is not
     * checked whether <code>command</code> corresponds to a valid
     * operating system command.</p>
     *
     * @param   command  A string array containing the program and its arguments
     * @return  This process builder
     */
    public ProcessBuilder command(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command)
            this.command.add(arg);
        return this;
    }

    /**
     * Returns this process builder's operating system program and
     * arguments.  The returned list is <i>not</i> a copy.  Subsequent
     * updates to the list will be reflected in the state of this
     * process builder.</p>
     *
     * @return  This process builder's program and its arguments
     */
    public List<String> command() {
        return command;
    }

    /**
     * Returns a string map view of this process builder's environment.
     *
     * Whenever a process builder is created, the environment is
     * initialized to a copy of the current process environment (see
     * {@link System#getenv()}).  Subprocesses subsequently started by
     * this object's {@link #start()} method will use this map as
     * their environment.
     *
     * <p>The returned object may be modified using ordinary {@link
     * java.util.Map Map} operations.  These modifications will be
     * visible to subprocesses started via the {@link #start()}
     * method.  Two <code>ProcessBuilder</code> instances always
     * contain independent process environments, so changes to the
     * returned map will never be reflected in any other
     * <code>ProcessBuilder</code> instance or the values returned by
     * {@link System#getenv System.getenv}.
     *
     * <p>If the system does not support environment variables, an
     * empty map is returned.
     *
     * <p>The returned map does not permit null keys or values.
     * Attempting to insert or query the presence of a null key or
     * value will throw a {@link NullPointerException}.
     * Attempting to query the presence of a key or value which is not
     * of type {@link String} will throw a {@link ClassCastException}.
     *
     * <p>The behavior of the returned map is system-dependent.  A
     * system may not allow modifications to environment variables or
     * may forbid certain variable names or values.  For this reason,
     * attempts to modify the map may fail with
     * {@link UnsupportedOperationException} or
     * {@link IllegalArgumentException}
     * if the modification is not permitted by the operating system.
     *
     * <p>Since the external format of environment variable names and
     * values is system-dependent, there may not be a one-to-one
     * mapping between them and Java's Unicode strings.  Nevertheless,
     * the map is implemented in such a way that environment variables
     * which are not modified by Java code will have an unmodified
     * native representation in the subprocess.
     *
     * <p>The returned map and its collection views may not obey the
     * general contract of the {@link Object#equals} and
     * {@link Object#hashCode} methods.
     *
     * <p>The returned map is typically case-sensitive on all platforms.
     *
     * <p>If a security manager exists, its
     * {@link SecurityManager#checkPermission checkPermission}
     * method is called with a
     * <code>{@link RuntimePermission}("getenv.*")</code>
     * permission.  This may result in a {@link SecurityException} being
     * thrown.
     *
     * <p>When passing information to a Java subprocess,
     * <a href=System.html#EnvironmentVSSystemProperties>system properties</a>
     * are generally preferred over environment variables.</p>
     *
     * @return  This process builder's environment
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkPermission checkPermission}
     *          method doesn't allow access to the process environment
     *
     * @see     Runtime#exec(String[],String[],java.io.File)
     * @see     System#getenv()
     */
    public Map<String,String> environment() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(new RuntimePermission("getenv.*"));

        if (environment == null)
            environment = ProcessEnvironment.environment();

        assert environment != null;

        return environment;
    }

    // Only for use by Runtime.exec(...envp...)
    ProcessBuilder environment(String[] envp) {
        assert environment == null;
        if (envp != null) {
            environment = ProcessEnvironment.emptyEnvironment(envp.length);
            assert environment != null;

            for (String envstring : envp) {
                // Before 1.5, we blindly passed invalid envstrings
                // to the child process.
                // We would like to throw an exception, but do not,
                // for compatibility with old broken code.

                // Silently discard any trailing junk.
                if (envstring.indexOf((int) '\u0000') != -1)
                    envstring = envstring.replaceFirst("\u0000.*", "");

                int eqlsign =
                    envstring.indexOf('=', ProcessEnvironment.MIN_NAME_LENGTH);
                // Silently ignore envstrings lacking the required `='.
                if (eqlsign != -1)
                    environment.put(envstring.substring(0,eqlsign),
                                    envstring.substring(eqlsign+1));
            }
        }
        return this;
    }

    /**
     * Returns this process builder's working directory.
     *
     * Subprocesses subsequently started by this object's {@link
     * #start()} method will use this as their working directory.
     * The returned value may be <code>null</code> -- this means to use
     * the working directory of the current Java process, usually the
     * directory named by the system property <code>user.dir</code>,
     * as the working directory of the child process.</p>
     *
     * @return  This process builder's working directory
     */
    public File directory() {
        return directory;
    }

    /**
     * Sets this process builder's working directory.
     *
     * Subprocesses subsequently started by this object's {@link
     * #start()} method will use this as their working directory.
     * The argument may be <code>null</code> -- this means to use the
     * working directory of the current Java process, usually the
     * directory named by the system property <code>user.dir</code>,
     * as the working directory of the child process.</p>
     *
     * @param   directory  The new working directory
     * @return  This process builder
     */
    public ProcessBuilder directory(File directory) {
        this.directory = directory;
        return this;
    }

    /**
     * Tells whether this process builder merges standard error and
     * standard output.
     *
     * <p>If this property is <code>true</code>, then any error output
     * generated by subprocesses subsequently started by this object's
     * {@link #start()} method will be merged with the standard
     * output, so that both can be read using the
     * {@link Process#getInputStream()} method.  This makes it easier
     * to correlate error messages with the corresponding output.
     * The initial value is <code>false</code>.</p>
     *
     * @return  This process builder's <code>redirectErrorStream</code> property
     */
    public boolean redirectErrorStream() {
        return redirectErrorStream;
    }

    /**
     * Sets this process builder's <code>redirectErrorStream</code> property.
     *
     * <p>If this property is <code>true</code>, then any error output
     * generated by subprocesses subsequently started by this object's
     * {@link #start()} method will be merged with the standard
     * output, so that both can be read using the
     * {@link Process#getInputStream()} method.  This makes it easier
     * to correlate error messages with the corresponding output.
     * The initial value is <code>false</code>.</p>
     *
     * @param   redirectErrorStream  The new property value
     * @return  This process builder
     */
    public ProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    /**
     * Starts a new process using the attributes of this process builder.
     *
     * <p>The new process will
     * invoke the command and arguments given by {@link #command()},
     * in a working directory as given by {@link #directory()},
     * with a process environment as given by {@link #environment()}.
     *
     * <p>This method checks that the command is a valid operating
     * system command.  Which commands are valid is system-dependent,
     * but at the very least the command must be a non-empty list of
     * non-null strings.
     *
     * <p>If there is a security manager, its
     * {@link SecurityManager#checkExec checkExec}
     * method is called with the first component of this object's
     * <code>command</code> array as its argument. This may result in
     * a {@link SecurityException} being thrown.
     *
     * <p>Starting an operating system process is highly system-dependent.
     * Among the many things that can go wrong are:
     * <ul>
     * <li>The operating system program file was not found.
     * <li>Access to the program file was denied.
     * <li>The working directory does not exist.
     * </ul>
     *
     * <p>In such cases an exception will be thrown.  The exact nature
     * of the exception is system-dependent, but it will always be a
     * subclass of {@link IOException}.
     *
     * <p>Subsequent modifications to this process builder will not
     * affect the returned {@link Process}.</p>
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  NullPointerException
     *          If an element of the command list is null
     *
     * @throws  IndexOutOfBoundsException
     *          If the command is an empty list (has size <code>0</code>)
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @see     Runtime#exec(String[], String[], java.io.File)
     * @see     SecurityManager#checkExec(String)
     */
    public Process start() throws IOException {
        // Must convert to array first -- a malicious user-supplied
        // list might try to circumvent the security check.
        String[] cmdarray = command.toArray(new String[command.size()]);
        for (String arg : cmdarray)
            if (arg == null)
                throw new NullPointerException();
        // Throws IndexOutOfBoundsException if command is empty
        String prog = cmdarray[0];

        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkExec(prog);

        String dir = directory == null ? null : directory.toString();

        try {
            return ProcessImpl.start(cmdarray,
                                     environment,
                                     dir,
                                     redirectErrorStream);
        } catch (IOException e) {
            // It's much easier for us to create a high-quality error
            // message than the low-level C code which found the problem.
            throw new IOException(
                "Cannot run program \"" + prog + "\""
                + (dir == null ? "" : " (in directory \"" + dir + "\")")
                + ": " + e.getMessage(),
                e);
        }
    }
}
