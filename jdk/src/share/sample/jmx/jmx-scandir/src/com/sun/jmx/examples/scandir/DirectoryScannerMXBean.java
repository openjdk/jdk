/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.jmx.examples.scandir;

import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import java.io.IOException;
import javax.management.InstanceNotFoundException;

/**
 * A <code>DirectoryScannerMXBean</code> is an MBean that
 * scans a file system starting at a given root directory,
 * and then looks for files that match a given criteria.
 * <p>
 * When such a file is found, the <code>DirectoryScannerMXBean</code> takes
 * the actions for which it was configured: see {@link #scan scan()}.
 * <p>
 * <code>DirectoryScannerMXBeans</code> are created, initialized, and
 * registered by the {@link ScanManagerMXBean}.
 * The {@link ScanManagerMXBean} will also schedule and run them in
 * background by calling their {@link #scan} method.
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public interface DirectoryScannerMXBean {
    /**
     * Get The {@link DirectoryScanner} state.
     * @return the current state of the <code>DirectoryScanner</code>.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public ScanState getState()
        throws IOException, InstanceNotFoundException;

    /**
     * Stops the current scan if {@link ScanState#RUNNING running}.
     * After this method completes the state of the application will
     * be {@link ScanState#STOPPED STOPPED}.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void stop()
        throws IOException, InstanceNotFoundException;

    /**
     * Scans the file system starting at the specified {@link #getRootDirectory
     * root directory}.
     * <p>If a file that matches this <code>DirectoryScannerMXBean</code>
     * {@link #getConfiguration} criteria is found,
     * the <code>DirectoryScannerMXBean</code> takes the {@link
     * DirectoryScannerConfig#getActions() actions} for which
     * it was {@link #getConfiguration configured}: emit a notification,
     * <i>and or</i> log a {@link
     * com.sun.jmx.examples.scandir.config.ResultRecord} for this file,
     * <i>and or</i> delete that file.
     * </p>
     * <p>
     * The code that would actually delete the file is commented out - so that
     * nothing valuable is lost if this example is run by mistake on the wrong
     * set of directories.
     * </p>
     * <p>This method returns only when the directory scan is completed, or
     *    if it was {@link #stop stopped} by another thread.
     * </p>
     * @throws IllegalStateException if already {@link ScanState#RUNNING}
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void scan()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the root directory at which this <code>DirectoryScannerMXBean</code>
     * will start scanning the file system.
     * <p>
     * This is a shortcut to {@link #getConfiguration
     * getConfiguration()}.{@link
     * DirectoryScannerConfig#getRootDirectory
     * getRootDirectory()}.
     * </p>
     * @return This <code>DirectoryScannerMXBean</code> root directory.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public String getRootDirectory()
        throws IOException, InstanceNotFoundException;

    /**
     * The configuration data from which this {@link DirectoryScanner} was
     * created.
     * <p>
     * You cannot change this configuration here. You can however
     * {@link ScanDirConfigMXBean#setConfiguration modify} the
     * {@link ScanDirConfigMXBean} configuration, and ask the
     * {@link ScanManagerMXBean} to {@link ScanManagerMXBean#applyConfiguration
     * apply} it. This will get all <code>DirectoryScannerMXBean</code>
     * replaced by new MBeans created from the modified configuration.
     * </p>
     *
     * @return This <code>DirectoryScannerMXBean</code> configuration data.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public DirectoryScannerConfig getConfiguration()
        throws IOException, InstanceNotFoundException;

    /**
     * A short string describing what's happening in current/latest scan.
     * @return a short info string.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public String getCurrentScanInfo()
        throws IOException, InstanceNotFoundException;
}
