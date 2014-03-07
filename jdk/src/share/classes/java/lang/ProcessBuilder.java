/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is used to create operating system processes.
 *
 * <p>Each {@code ProcessBuilder} instance manages a collection
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
 * named by the system property {@code user.dir}.
 *
 * <li><a name="redirect-input">a source of <i>standard input</i></a>.
 * By default, the subprocess reads input from a pipe.  Java code
 * can access this pipe via the output stream returned by
 * {@link Process#getOutputStream()}.  However, standard input may
 * be redirected to another source using
 * {@link #redirectInput(Redirect) redirectInput}.
 * In this case, {@link Process#getOutputStream()} will return a
 * <i>null output stream</i>, for which:
 *
 * <ul>
 * <li>the {@link OutputStream#write(int) write} methods always
 * throw {@code IOException}
 * <li>the {@link OutputStream#close() close} method does nothing
 * </ul>
 *
 * <li><a name="redirect-output">a destination for <i>standard output</i>
 * and <i>standard error</i></a>.  By default, the subprocess writes standard
 * output and standard error to pipes.  Java code can access these pipes
 * via the input streams returned by {@link Process#getInputStream()} and
 * {@link Process#getErrorStream()}.  However, standard output and
 * standard error may be redirected to other destinations using
 * {@link #redirectOutput(Redirect) redirectOutput} and
 * {@link #redirectError(Redirect) redirectError}.
 * In this case, {@link Process#getInputStream()} and/or
 * {@link Process#getErrorStream()} will return a <i>null input
 * stream</i>, for which:
 *
 * <ul>
 * <li>the {@link InputStream#read() read} methods always return
 * {@code -1}
 * <li>the {@link InputStream#available() available} method always returns
 * {@code 0}
 * <li>the {@link InputStream#close() close} method does nothing
 * </ul>
 *
 * <li>a <i>redirectErrorStream</i> property.  Initially, this property
 * is {@code false}, meaning that the standard output and error
 * output of a subprocess are sent to two separate streams, which can
 * be accessed using the {@link Process#getInputStream()} and {@link
 * Process#getErrorStream()} methods.
 *
 * <p>If the value is set to {@code true}, then:
 *
 * <ul>
 * <li>standard error is merged with the standard output and always sent
 * to the same destination (this makes it easier to correlate error
 * messages with the corresponding output)
 * <li>the common destination of standard error and standard output can be
 * redirected using
 * {@link #redirectOutput(Redirect) redirectOutput}
 * <li>any redirection set by the
 * {@link #redirectError(Redirect) redirectError}
 * method is ignored when creating a subprocess
 * <li>the stream returned from {@link Process#getErrorStream()} will
 * always be a <a href="#redirect-output">null input stream</a>
 * </ul>
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
 * If multiple threads access a {@code ProcessBuilder} instance
 * concurrently, and at least one of the threads modifies one of the
 * attributes structurally, it <i>must</i> be synchronized externally.
 *
 * <p>Starting a new process which uses the default working directory
 * and environment is easy:
 *
 * <pre> {@code
 * Process p = new ProcessBuilder("myCommand", "myArg").start();
 * }</pre>
 *
 * <p>Here is an example that starts a process with a modified working
 * directory and environment, and redirects standard output and error
 * to be appended to a log file:
 *
 * <pre> {@code
 * ProcessBuilder pb =
 *   new ProcessBuilder("myCommand", "myArg1", "myArg2");
 * Map<String, String> env = pb.environment();
 * env.put("VAR1", "myValue");
 * env.remove("OTHERVAR");
 * env.put("VAR2", env.get("VAR1") + "suffix");
 * pb.directory(new File("myDir"));
 * File log = new File("log");
 * pb.redirectErrorStream(true);
 * pb.redirectOutput(Redirect.appendTo(log));
 * Process p = pb.start();
 * assert pb.redirectInput() == Redirect.PIPE;
 * assert pb.redirectOutput().file() == log;
 * assert p.getInputStream().read() == -1;
 * }</pre>
 *
 * <p>To start a process with an explicit set of environment
 * variables, first call {@link java.util.Map#clear() Map.clear()}
 * before adding environment variables.
 *
 * @author Martin Buchholz
 * @since 1.5
 */

public final class ProcessBuilder
{
    private List<String> command;
    private File directory;
    private Map<String,String> environment;
    private boolean redirectErrorStream;
    private Redirect[] redirects;

    /**
     * Constructs a process builder with the specified operating
     * system program and arguments.  This constructor does <i>not</i>
     * make a copy of the {@code command} list.  Subsequent
     * updates to the list will be reflected in the state of the
     * process builder.  It is not checked whether
     * {@code command} corresponds to a valid operating system
     * command.
     *
     * @param  command the list containing the program and its arguments
     * @throws NullPointerException if the argument is null
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
     * list containing the same strings as the {@code command}
     * array, in the same order.  It is not checked whether
     * {@code command} corresponds to a valid operating system
     * command.
     *
     * @param command a string array containing the program and its arguments
     */
    public ProcessBuilder(String... command) {
        this.command = new ArrayList<>(command.length);
        for (String arg : command)
            this.command.add(arg);
    }

    /**
     * Sets this process builder's operating system program and
     * arguments.  This method does <i>not</i> make a copy of the
     * {@code command} list.  Subsequent updates to the list will
     * be reflected in the state of the process builder.  It is not
     * checked whether {@code command} corresponds to a valid
     * operating system command.
     *
     * @param  command the list containing the program and its arguments
     * @return this process builder
     *
     * @throws NullPointerException if the argument is null
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
     * {@code command} array, in the same order.  It is not
     * checked whether {@code command} corresponds to a valid
     * operating system command.
     *
     * @param  command a string array containing the program and its arguments
     * @return this process builder
     */
    public ProcessBuilder command(String... command) {
        this.command = new ArrayList<>(command.length);
        for (String arg : command)
            this.command.add(arg);
        return this;
    }

    /**
     * Returns this process builder's operating system program and
     * arguments.  The returned list is <i>not</i> a copy.  Subsequent
     * updates to the list will be reflected in the state of this
     * process builder.
     *
     * @return this process builder's program and its arguments
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
     * method.  Two {@code ProcessBuilder} instances always
     * contain independent process environments, so changes to the
     * returned map will never be reflected in any other
     * {@code ProcessBuilder} instance or the values returned by
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
     * {@link SecurityManager#checkPermission checkPermission} method
     * is called with a
     * {@link RuntimePermission}{@code ("getenv.*")} permission.
     * This may result in a {@link SecurityException} being thrown.
     *
     * <p>When passing information to a Java subprocess,
     * <a href=System.html#EnvironmentVSSystemProperties>system properties</a>
     * are generally preferred over environment variables.
     *
     * @return this process builder's environment
     *
     * @throws SecurityException
     *         if a security manager exists and its
     *         {@link SecurityManager#checkPermission checkPermission}
     *         method doesn't allow access to the process environment
     *
     * @see    Runtime#exec(String[],String[],java.io.File)
     * @see    System#getenv()
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
     * The returned value may be {@code null} -- this means to use
     * the working directory of the current Java process, usually the
     * directory named by the system property {@code user.dir},
     * as the working directory of the child process.
     *
     * @return this process builder's working directory
     */
    public File directory() {
        return directory;
    }

    /**
     * Sets this process builder's working directory.
     *
     * Subprocesses subsequently started by this object's {@link
     * #start()} method will use this as their working directory.
     * The argument may be {@code null} -- this means to use the
     * working directory of the current Java process, usually the
     * directory named by the system property {@code user.dir},
     * as the working directory of the child process.
     *
     * @param  directory the new working directory
     * @return this process builder
     */
    public ProcessBuilder directory(File directory) {
        this.directory = directory;
        return this;
    }

    // ---------------- I/O Redirection ----------------

    /**
     * Implements a <a href="#redirect-output">null input stream</a>.
     */
    static class NullInputStream extends InputStream {
        static final NullInputStream INSTANCE = new NullInputStream();
        private NullInputStream() {}
        public int read()      { return -1; }
        public int available() { return 0; }
    }

    /**
     * Implements a <a href="#redirect-input">null output stream</a>.
     */
    static class NullOutputStream extends OutputStream {
        static final NullOutputStream INSTANCE = new NullOutputStream();
        private NullOutputStream() {}
        public void write(int b) throws IOException {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Represents a source of subprocess input or a destination of
     * subprocess output.
     *
     * Each {@code Redirect} instance is one of the following:
     *
     * <ul>
     * <li>the special value {@link #PIPE Redirect.PIPE}
     * <li>the special value {@link #INHERIT Redirect.INHERIT}
     * <li>a redirection to read from a file, created by an invocation of
     *     {@link Redirect#from Redirect.from(File)}
     * <li>a redirection to write to a file,  created by an invocation of
     *     {@link Redirect#to Redirect.to(File)}
     * <li>a redirection to append to a file, created by an invocation of
     *     {@link Redirect#appendTo Redirect.appendTo(File)}
     * </ul>
     *
     * <p>Each of the above categories has an associated unique
     * {@link Type Type}.
     *
     * @since 1.7
     */
    public static abstract class Redirect {
        /**
         * The type of a {@link Redirect}.
         */
        public enum Type {
            /**
             * The type of {@link Redirect#PIPE Redirect.PIPE}.
             */
            PIPE,

            /**
             * The type of {@link Redirect#INHERIT Redirect.INHERIT}.
             */
            INHERIT,

            /**
             * The type of redirects returned from
             * {@link Redirect#from Redirect.from(File)}.
             */
            READ,

            /**
             * The type of redirects returned from
             * {@link Redirect#to Redirect.to(File)}.
             */
            WRITE,

            /**
             * The type of redirects returned from
             * {@link Redirect#appendTo Redirect.appendTo(File)}.
             */
            APPEND
        };

        /**
         * Returns the type of this {@code Redirect}.
         * @return the type of this {@code Redirect}
         */
        public abstract Type type();

        /**
         * Indicates that subprocess I/O will be connected to the
         * current Java process over a pipe.
         *
         * This is the default handling of subprocess standard I/O.
         *
         * <p>It will always be true that
         *  <pre> {@code
         * Redirect.PIPE.file() == null &&
         * Redirect.PIPE.type() == Redirect.Type.PIPE
         * }</pre>
         */
        public static final Redirect PIPE = new Redirect() {
                public Type type() { return Type.PIPE; }
                public String toString() { return type().toString(); }};

        /**
         * Indicates that subprocess I/O source or destination will be the
         * same as those of the current process.  This is the normal
         * behavior of most operating system command interpreters (shells).
         *
         * <p>It will always be true that
         *  <pre> {@code
         * Redirect.INHERIT.file() == null &&
         * Redirect.INHERIT.type() == Redirect.Type.INHERIT
         * }</pre>
         */
        public static final Redirect INHERIT = new Redirect() {
                public Type type() { return Type.INHERIT; }
                public String toString() { return type().toString(); }};

        /**
         * Returns the {@link File} source or destination associated
         * with this redirect, or {@code null} if there is no such file.
         *
         * @return the file associated with this redirect,
         *         or {@code null} if there is no such file
         */
        public File file() { return null; }

        /**
         * When redirected to a destination file, indicates if the output
         * is to be written to the end of the file.
         */
        boolean append() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a redirect to read from the specified file.
         *
         * <p>It will always be true that
         *  <pre> {@code
         * Redirect.from(file).file() == file &&
         * Redirect.from(file).type() == Redirect.Type.READ
         * }</pre>
         *
         * @param file The {@code File} for the {@code Redirect}.
         * @throws NullPointerException if the specified file is null
         * @return a redirect to read from the specified file
         */
        public static Redirect from(final File file) {
            if (file == null)
                throw new NullPointerException();
            return new Redirect() {
                    public Type type() { return Type.READ; }
                    public File file() { return file; }
                    public String toString() {
                        return "redirect to read from file \"" + file + "\"";
                    }
                };
        }

        /**
         * Returns a redirect to write to the specified file.
         * If the specified file exists when the subprocess is started,
         * its previous contents will be discarded.
         *
         * <p>It will always be true that
         *  <pre> {@code
         * Redirect.to(file).file() == file &&
         * Redirect.to(file).type() == Redirect.Type.WRITE
         * }</pre>
         *
         * @param file The {@code File} for the {@code Redirect}.
         * @throws NullPointerException if the specified file is null
         * @return a redirect to write to the specified file
         */
        public static Redirect to(final File file) {
            if (file == null)
                throw new NullPointerException();
            return new Redirect() {
                    public Type type() { return Type.WRITE; }
                    public File file() { return file; }
                    public String toString() {
                        return "redirect to write to file \"" + file + "\"";
                    }
                    boolean append() { return false; }
                };
        }

        /**
         * Returns a redirect to append to the specified file.
         * Each write operation first advances the position to the
         * end of the file and then writes the requested data.
         * Whether the advancement of the position and the writing
         * of the data are done in a single atomic operation is
         * system-dependent and therefore unspecified.
         *
         * <p>It will always be true that
         *  <pre> {@code
         * Redirect.appendTo(file).file() == file &&
         * Redirect.appendTo(file).type() == Redirect.Type.APPEND
         * }</pre>
         *
         * @param file The {@code File} for the {@code Redirect}.
         * @throws NullPointerException if the specified file is null
         * @return a redirect to append to the specified file
         */
        public static Redirect appendTo(final File file) {
            if (file == null)
                throw new NullPointerException();
            return new Redirect() {
                    public Type type() { return Type.APPEND; }
                    public File file() { return file; }
                    public String toString() {
                        return "redirect to append to file \"" + file + "\"";
                    }
                    boolean append() { return true; }
                };
        }

        /**
         * Compares the specified object with this {@code Redirect} for
         * equality.  Returns {@code true} if and only if the two
         * objects are identical or both objects are {@code Redirect}
         * instances of the same type associated with non-null equal
         * {@code File} instances.
         */
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (! (obj instanceof Redirect))
                return false;
            Redirect r = (Redirect) obj;
            if (r.type() != this.type())
                return false;
            assert this.file() != null;
            return this.file().equals(r.file());
        }

        /**
         * Returns a hash code value for this {@code Redirect}.
         * @return a hash code value for this {@code Redirect}
         */
        public int hashCode() {
            File file = file();
            if (file == null)
                return super.hashCode();
            else
                return file.hashCode();
        }

        /**
         * No public constructors.  Clients must use predefined
         * static {@code Redirect} instances or factory methods.
         */
        private Redirect() {}
    }

    private Redirect[] redirects() {
        if (redirects == null)
            redirects = new Redirect[] {
                Redirect.PIPE, Redirect.PIPE, Redirect.PIPE
            };
        return redirects;
    }

    /**
     * Sets this process builder's standard input source.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method obtain their standard input from this source.
     *
     * <p>If the source is {@link Redirect#PIPE Redirect.PIPE}
     * (the initial value), then the standard input of a
     * subprocess can be written to using the output stream
     * returned by {@link Process#getOutputStream()}.
     * If the source is set to any other value, then
     * {@link Process#getOutputStream()} will return a
     * <a href="#redirect-input">null output stream</a>.
     *
     * @param  source the new standard input source
     * @return this process builder
     * @throws IllegalArgumentException
     *         if the redirect does not correspond to a valid source
     *         of data, that is, has type
     *         {@link Redirect.Type#WRITE WRITE} or
     *         {@link Redirect.Type#APPEND APPEND}
     * @since  1.7
     */
    public ProcessBuilder redirectInput(Redirect source) {
        if (source.type() == Redirect.Type.WRITE ||
            source.type() == Redirect.Type.APPEND)
            throw new IllegalArgumentException(
                "Redirect invalid for reading: " + source);
        redirects()[0] = source;
        return this;
    }

    /**
     * Sets this process builder's standard output destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method send their standard output to this destination.
     *
     * <p>If the destination is {@link Redirect#PIPE Redirect.PIPE}
     * (the initial value), then the standard output of a subprocess
     * can be read using the input stream returned by {@link
     * Process#getInputStream()}.
     * If the destination is set to any other value, then
     * {@link Process#getInputStream()} will return a
     * <a href="#redirect-output">null input stream</a>.
     *
     * @param  destination the new standard output destination
     * @return this process builder
     * @throws IllegalArgumentException
     *         if the redirect does not correspond to a valid
     *         destination of data, that is, has type
     *         {@link Redirect.Type#READ READ}
     * @since  1.7
     */
    public ProcessBuilder redirectOutput(Redirect destination) {
        if (destination.type() == Redirect.Type.READ)
            throw new IllegalArgumentException(
                "Redirect invalid for writing: " + destination);
        redirects()[1] = destination;
        return this;
    }

    /**
     * Sets this process builder's standard error destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method send their standard error to this destination.
     *
     * <p>If the destination is {@link Redirect#PIPE Redirect.PIPE}
     * (the initial value), then the error output of a subprocess
     * can be read using the input stream returned by {@link
     * Process#getErrorStream()}.
     * If the destination is set to any other value, then
     * {@link Process#getErrorStream()} will return a
     * <a href="#redirect-output">null input stream</a>.
     *
     * <p>If the {@link #redirectErrorStream redirectErrorStream}
     * attribute has been set {@code true}, then the redirection set
     * by this method has no effect.
     *
     * @param  destination the new standard error destination
     * @return this process builder
     * @throws IllegalArgumentException
     *         if the redirect does not correspond to a valid
     *         destination of data, that is, has type
     *         {@link Redirect.Type#READ READ}
     * @since  1.7
     */
    public ProcessBuilder redirectError(Redirect destination) {
        if (destination.type() == Redirect.Type.READ)
            throw new IllegalArgumentException(
                "Redirect invalid for writing: " + destination);
        redirects()[2] = destination;
        return this;
    }

    /**
     * Sets this process builder's standard input source to a file.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code redirectInput(file)}
     * behaves in exactly the same way as the invocation
     * {@link #redirectInput(Redirect) redirectInput}
     * {@code (Redirect.from(file))}.
     *
     * @param  file the new standard input source
     * @return this process builder
     * @since  1.7
     */
    public ProcessBuilder redirectInput(File file) {
        return redirectInput(Redirect.from(file));
    }

    /**
     * Sets this process builder's standard output destination to a file.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code redirectOutput(file)}
     * behaves in exactly the same way as the invocation
     * {@link #redirectOutput(Redirect) redirectOutput}
     * {@code (Redirect.to(file))}.
     *
     * @param  file the new standard output destination
     * @return this process builder
     * @since  1.7
     */
    public ProcessBuilder redirectOutput(File file) {
        return redirectOutput(Redirect.to(file));
    }

    /**
     * Sets this process builder's standard error destination to a file.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code redirectError(file)}
     * behaves in exactly the same way as the invocation
     * {@link #redirectError(Redirect) redirectError}
     * {@code (Redirect.to(file))}.
     *
     * @param  file the new standard error destination
     * @return this process builder
     * @since  1.7
     */
    public ProcessBuilder redirectError(File file) {
        return redirectError(Redirect.to(file));
    }

    /**
     * Returns this process builder's standard input source.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method obtain their standard input from this source.
     * The initial value is {@link Redirect#PIPE Redirect.PIPE}.
     *
     * @return this process builder's standard input source
     * @since  1.7
     */
    public Redirect redirectInput() {
        return (redirects == null) ? Redirect.PIPE : redirects[0];
    }

    /**
     * Returns this process builder's standard output destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method redirect their standard output to this destination.
     * The initial value is {@link Redirect#PIPE Redirect.PIPE}.
     *
     * @return this process builder's standard output destination
     * @since  1.7
     */
    public Redirect redirectOutput() {
        return (redirects == null) ? Redirect.PIPE : redirects[1];
    }

    /**
     * Returns this process builder's standard error destination.
     *
     * Subprocesses subsequently started by this object's {@link #start()}
     * method redirect their standard error to this destination.
     * The initial value is {@link Redirect#PIPE Redirect.PIPE}.
     *
     * @return this process builder's standard error destination
     * @since  1.7
     */
    public Redirect redirectError() {
        return (redirects == null) ? Redirect.PIPE : redirects[2];
    }

    /**
     * Sets the source and destination for subprocess standard I/O
     * to be the same as those of the current Java process.
     *
     * <p>This is a convenience method.  An invocation of the form
     *  <pre> {@code
     * pb.inheritIO()
     * }</pre>
     * behaves in exactly the same way as the invocation
     *  <pre> {@code
     * pb.redirectInput(Redirect.INHERIT)
     *   .redirectOutput(Redirect.INHERIT)
     *   .redirectError(Redirect.INHERIT)
     * }</pre>
     *
     * This gives behavior equivalent to most operating system
     * command interpreters, or the standard C library function
     * {@code system()}.
     *
     * @return this process builder
     * @since  1.7
     */
    public ProcessBuilder inheritIO() {
        Arrays.fill(redirects(), Redirect.INHERIT);
        return this;
    }

    /**
     * Tells whether this process builder merges standard error and
     * standard output.
     *
     * <p>If this property is {@code true}, then any error output
     * generated by subprocesses subsequently started by this object's
     * {@link #start()} method will be merged with the standard
     * output, so that both can be read using the
     * {@link Process#getInputStream()} method.  This makes it easier
     * to correlate error messages with the corresponding output.
     * The initial value is {@code false}.
     *
     * @return this process builder's {@code redirectErrorStream} property
     */
    public boolean redirectErrorStream() {
        return redirectErrorStream;
    }

    /**
     * Sets this process builder's {@code redirectErrorStream} property.
     *
     * <p>If this property is {@code true}, then any error output
     * generated by subprocesses subsequently started by this object's
     * {@link #start()} method will be merged with the standard
     * output, so that both can be read using the
     * {@link Process#getInputStream()} method.  This makes it easier
     * to correlate error messages with the corresponding output.
     * The initial value is {@code false}.
     *
     * @param  redirectErrorStream the new property value
     * @return this process builder
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
     * <p>A minimal set of system dependent environment variables may
     * be required to start a process on some operating systems.
     * As a result, the subprocess may inherit additional environment variable
     * settings beyond those in the process builder's {@link #environment()}.
     *
     * <p>If there is a security manager, its
     * {@link SecurityManager#checkExec checkExec}
     * method is called with the first component of this object's
     * {@code command} array as its argument. This may result in
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
     * affect the returned {@link Process}.
     *
     * @return a new {@link Process} object for managing the subprocess
     *
     * @throws NullPointerException
     *         if an element of the command list is null
     *
     * @throws IndexOutOfBoundsException
     *         if the command is an empty list (has size {@code 0})
     *
     * @throws SecurityException
     *         if a security manager exists and
     *         <ul>
     *
     *         <li>its
     *         {@link SecurityManager#checkExec checkExec}
     *         method doesn't allow creation of the subprocess, or
     *
     *         <li>the standard input to the subprocess was
     *         {@linkplain #redirectInput redirected from a file}
     *         and the security manager's
     *         {@link SecurityManager#checkRead checkRead} method
     *         denies read access to the file, or
     *
     *         <li>the standard output or standard error of the
     *         subprocess was
     *         {@linkplain #redirectOutput redirected to a file}
     *         and the security manager's
     *         {@link SecurityManager#checkWrite checkWrite} method
     *         denies write access to the file
     *
     *         </ul>
     *
     * @throws IOException if an I/O error occurs
     *
     * @see Runtime#exec(String[], String[], java.io.File)
     */
    public Process start() throws IOException {
        // Must convert to array first -- a malicious user-supplied
        // list might try to circumvent the security check.
        String[] cmdarray = command.toArray(new String[command.size()]);
        cmdarray = cmdarray.clone();

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
                                     redirects,
                                     redirectErrorStream);
        } catch (IOException | IllegalArgumentException e) {
            String exceptionInfo = ": " + e.getMessage();
            Throwable cause = e;
            if ((e instanceof IOException) && security != null) {
                // Can not disclose the fail reason for read-protected files.
                try {
                    security.checkRead(prog);
                } catch (SecurityException se) {
                    exceptionInfo = "";
                    cause = se;
                }
            }
            // It's much easier for us to create a high-quality error
            // message than the low-level C code which found the problem.
            throw new IOException(
                "Cannot run program \"" + prog + "\""
                + (dir == null ? "" : " (in directory \"" + dir + "\")")
                + exceptionInfo,
                cause);
        }
    }
}
