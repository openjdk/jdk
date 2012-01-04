/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.management;

import java.util.List;
import java.lang.management.PlatformManagedObject;

/**
 * Diagnostic management interface for the HotSpot Virtual Machine.
 *
 * <p>{@linkplain #getDiagnosticCommands Diagnostic commands}
 * are actions that can be invoked dynamically and
 * executed in a target Java virtual machine, mainly for troubleshooting
 * and diagnosis.
 *
 * <p>The diagnostic MBean is registered to the platform MBeanServer
 * as are other platform MBeans.
 *
 * <p>The <tt>ObjectName</tt> for uniquely identifying the diagnostic
 * MXBean within an MBeanServer is:
 * <blockquote>
 *    <tt>com.sun.management:type=HotSpotDiagnostic</tt>
 * </blockquote>
.*
 * It can be obtained by calling the
 * {@link PlatformManagedObject#getObjectName} method.
 *
 * All methods throw a {@code NullPointerException} if any input argument is
 * {@code null} unless it's stated otherwise.
 *
 * @see ManagementFactory#getPlatformMXBeans(Class)
 */
public interface HotSpotDiagnosticMXBean extends PlatformManagedObject {
    /**
     * Dumps the heap to the <tt>outputFile</tt> file in the same
     * format as the hprof heap dump.
     * <p>
     * If this method is called remotely from another process,
     * the heap dump output is written to a file named <tt>outputFile</tt>
     * on the machine where the target VM is running.  If outputFile is
     * a relative path, it is relative to the working directory where
     * the target VM was started.
     *
     * @param  outputFile the system-dependent filename
     * @param  live if <tt>true</tt> dump only <i>live</i> objects
     *         i.e. objects that are reachable from others
     * @throws IOException if the <tt>outputFile</tt>
     *                     cannot be created, opened, or written to.
     * @throws UnsupportedOperationException if this operation is not supported.
     * @throws NullPointerException if <tt>outputFile</tt> is <tt>null</tt>.
     */
    public void dumpHeap(String outputFile, boolean live) throws java.io.IOException;

    /**
     * Returns a list of <tt>VMOption</tt> objects for all diagnostic options.
     * A diagnostic option is a {@link VMOption#isWriteable writeable}
     * VM option that can be set dynamically mainly for troubleshooting
     * and diagnosis.
     *
     * @return a list of <tt>VMOption</tt> objects for all diagnostic options.
     */
    public java.util.List<VMOption> getDiagnosticOptions();

    /**
     * Returns a <tt>VMOption</tt> object for a VM option of the given
     * name.
     *
     * @return a <tt>VMOption</tt> object for a VM option of the given name.
     * @throws NullPointerException if name is <tt>null</tt>.
     * @throws IllegalArgumentException if a VM option of the given name
     *                                     does not exist.
     */
    public VMOption getVMOption(String name);

    /**
     * Sets a VM option of the given name to the specified value.
     * The new value will be reflected in a new <tt>VMOption</tt>
     * object returned by the {@link #getVMOption} method or
     * the {@link #getDiagnosticOptions} method.  This method does
     * not change the value of this <tt>VMOption</tt> object.
     *
     * @param name Name of a VM option
     * @param value New value of the VM option to be set
     *
     * @throws IllegalArgumentException if the VM option of the given name
     *                                     does not exist.
     * @throws IllegalArgumentException if the new value is invalid.
     * @throws IllegalArgumentException if the VM option is not writeable.
     * @throws NullPointerException if name or value is <tt>null</tt>.
     *
     * @throws  java.lang.SecurityException
     *     if a security manager exists and the caller does not have
     *     ManagementPermission("control").
     */
    public void setVMOption(String name, String value);

    /**
     * Returns the {@linkplain DiagnosticCommandInfo#getName() names}
     * of all diagnostic commands.
     * A diagnostic command is an action that can be invoked dynamically
     * mainly for troubleshooting and diagnosis.  The list of diagnostic
     * commands may change at runtime.  A diagnostic command may be
     * {@linkplain DiagnosticCommandInfo#isEnabled disabled} but will
     * not be removed from a previously returned list.
     *
     * @return the names of all diagnostic commands.
     *
     * @since 7u4
     */
    public List<String> getDiagnosticCommands();

    /**
     * Returns a {@code DiagnosticCommandInfo} object describing the
     * diagnostic command of the specified name {@code command}
     *
     * @param command a diagnostic command name
     * @return a {@code DiagnosticCommandInfo} object
     * @throws java.lang.IllegalArgumentException if the {@code command}
     *         doesn't match any diagnostic command registered in the
     *         targeted Java virtual machine.
     *
     * @since 7u4
     */
    public DiagnosticCommandInfo getDiagnosticCommandInfo(String command);

    /**
     * Returns a list of {@code DiagnosticCommandInfo} object describing
     * all diagnostic commands available on the targeted Java virtual machine
     *
     * @return a list of {@code DiagnosticCommandInfo} objects
     *
     * @since 7u4
     */
    public List<DiagnosticCommandInfo> getDiagnosticCommandInfo();

    /**
     * Returns a list of {@code DiagnosticCommandInfo} object describing
     * all diagnostic commands specified in the {@code commands} list.
     *
     * @param commands {@code List} of {@code String} containing diagnostic
     *        command names
     * @return a {@code List} of {@code DiagnosticCommandInfo} objects
     *
     * @throws java.lang.IllegalArgumentException if at least one
     *         command specified in the {@code commands } list
     *         doesn't match any diagnostic command registered in the
     *         targeted Java virtual machine.
     *
     * @since 7u4
     */
    public List<DiagnosticCommandInfo> getDiagnosticCommandInfo(List<String> commands);

    /**
     * Executes the command line {@code commandLine}. The command line must
     * start with a diagnostic command name, optionally followed by parameters.
     * Each command has its own syntax but the generic syntax for a diagnostic
     * command line is:
     * <blockquote>
     *    &lt;command name&gt; [&lt;option&gt;=&lt;value&gt;] [&lt;argument_value&gt;]
     * </blockquote>
     *
     * @param commandLine command line to execute
     * @return a {@code String} object containing the diagnostic command
     *         output.
     *
     * @throws java.lang.IllegalArgumentException if the command line doesn't
     *         match any diagnostic command registered in the virtual machine
     *         of if the parameters don't match the diagnostic command syntax.
     * @throws java.lang.SecurityException
     *         if a security manager exists and the caller does not have
     *         ManagementPermission("control").
     *
     * @since 7u4
     */
    public String execute(String commandLine);

    /**
     * Invokes the diagnostic command named {@code cmd} with the parameters
     * specified in {@code args}. Each command has its own syntax but
     * the generic syntax for parameters is:
     * <blockquote>
     *    [&lt;option&gt;=&lt;value&gt;] [&lt;argument_value&gt;]
     * </blockquote>
     *
     * @param cmd a diagnostic command name
     * @param args the command parameters
     * @return a {@code String} object containing the diagnostic command
     *         output.
     *
     * @throws java.lang.IllegalArgumentException if the command line doesn't
     *         match any diagnostic command registered in the virtual machine
     *         of if the parameters don't match the diagnostic command syntax.
     * @throws java.lang.SecurityException
     *         if a security manager exists and the caller does not have
     *         ManagementPermission("control").
     *
     * @since 7u4
     */
    public String execute(String cmd, String... args);
}
