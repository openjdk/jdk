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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.jmx.examples.scandir;

import java.io.IOException;
import java.util.Map;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;

/**
 * The <code>ScanManagerMXBean</code> is responsible for applying a
 * configuration, starting and scheduling directory scans, and reporting
 * application state.
 * <p>
 * The <code>ScanManagerMXBean</code> is a singleton MBean: there can be
 * at most one instance of such an MBean registered in a given MBeanServer.
 * The name of that MBean is a constant defined in
 * {@link ScanManager#SCAN_MANAGER_NAME ScanManager.SCAN_MANAGER_NAME}.
 * </p>
 * <p>
 * The <code>ScanManagerMXBean</code> is the entry point of the <i>scandir</i>
 * application management interface. It is from this MBean that all other
 * MBeans will be created and registered.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 **/
public interface ScanManagerMXBean {
    /**
     * This state tells whether directory scans are running, scheduled,
     * successfully completed, or stopped.
     * <p>
     * The {@link #CLOSED} state means
     * that the {@link ScanManagerMXBean} was closed and is no longer usable.
     * This state is used when the {@link ScanManagerMXBean} needs to be
     * unregistered.
     * </p>
     **/
    public enum ScanState {
        /**
         * Scanning of directories is in process.
         **/
        RUNNING,

        /**
         * Scanning of directories is not in process, but is scheduled
         * for a later date.
         **/
        SCHEDULED,

        /**
         * Scanning is successfully completed.
         **/
        COMPLETED,

        /**
         * Scanning is stopped. No scanning is scheduled.
         **/
        STOPPED,

        /**
         * close() was called.
         **/
        CLOSED

    }

    /**
     * Returns the current state of the application.
     * @return the current state of the application.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public ScanState getState()
        throws IOException, InstanceNotFoundException;

    /**
     * Schedule a scan session for a later date.
     * <p>
     * A scan session is a background task that will sequentially call {@link
     * DirectoryScannerMXBean#scan scan()} on every {@link
     * DirectoryScannerMXBean} configured for this MBean.
     * </p>
     * @see #getDirectoryScanners
     * @param delay The first scan session will be started after
     *        the given delay. 0 means start now.
     * @param interval Scan session will be rescheduled periodically
     *        at the specified interval. The interval starts at the
     *        the end of the scan session: if a scan session takes
     *        on average x milliseconds to complete, then a scan session will
     *        be started on average every x+interval milliseconds.
     *        if (interval == 0) then scan session will not be
     *        rescheduled, and will run only once.
     * @throws IllegalStateException if a scan session is already
     *         running or scheduled, or the MBean is closed.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void schedule(long delay, long interval)
        throws IOException, InstanceNotFoundException;


    /**
     * Stops current running or scheduled scan sessions if any.
     * <p>
     * A scan session is a background task that will sequentially call {@link
     * DirectoryScannerMXBean#scan scan()} on every {@link
     * DirectoryScannerMXBean} configured for this MBean.
     * </p>
     * <p>
     * Scan sessions are started/scheduled by calls to {@link #start start} or
     * {@link #schedule schedule}.
     * </p>
     * After this method completes the state of the application will
     * be {@link ScanState#STOPPED}.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void stop()
        throws IOException, InstanceNotFoundException;

    /**
     * Switches the state to CLOSED.
     * When closed, this MBean cannot be used any more.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void close()
        throws IOException, InstanceNotFoundException;

    /**
     * Starts a scan session immediately.
     * This is equivalent to {@link #schedule(long,long) schedule(0,0)}.
     * @throws IllegalStateException if a scan session is already
     *         running or scheduled, or the MBean is closed.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void start()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the list of directory scanners configured for this MBean.
     * @return A {@code Map<String,DirectoryScannerMXBean>} where the
     *         key in the map is the value of the <code>name=</code> key
     *         of the {@link DirectoryScannerMXBean} ObjectName.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws JMException The MBeanServer failed to call the underlying MBean.
     **/
    public Map<String,DirectoryScannerMXBean> getDirectoryScanners()
        throws IOException, JMException;

    /**
     * Apply the configuration handled by the {@link
     * #getConfigurationMBean configuration MBean}.
     * <p>
     * When the configuration is applied, all the {@link DirectoryScannerMXBean}
     * created by this MBean will be unregistered, and new {@link
     * DirectoryScannerMXBean} will be created and registered from the
     * new {@link ScanDirConfigMXBean#getConfiguration configuration data}.
     * </p>
     * <p>
     * The initial result log configuration held by the {@link
     * #getConfigurationMBean configuration MBean} will also be pushed to the
     * {@link ResultLogManagerMXBean}. If you don't want to lose your current
     * {@link ResultLogManagerMXBean} configuration, you should therefore call
     * {@link #applyCurrentResultLogConfig
     * applyCurrentResultLogConfig} before calling
     * {@link #applyConfiguration applyConfiguration}
     * </p>
     * @param fromMemory if {@code true}, the configuration will be applied
     *        from memory. if {@code false}, the {@code ScanManagerMXBean} will
     *        ask the {@link
     * #getConfigurationMBean configuration MBean} to {@link
     * ScanDirConfigMXBean#load reload its configuration} before applying
     * it.
     * @throws IllegalStateException if a scan session is
     *         running or scheduled, or the MBean is closed.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws JMException The MBeanServer failed to call the underlying MBean.
     **/
    public void applyConfiguration(boolean fromMemory)
        throws IOException, JMException;
    /**
     * Replaces the {@link
     * #getConfigurationMBean configuration MBean}'s {@link
     * com.sun.jmx.examples.scandir.config.ScanManagerConfig#getInitialResultLogConfig
     * initial result log configuration} with the current {@link
     * ResultLogManagerMXBean}
     * configuration. This prevents the <code>ResultLogManagerMXBean</code>
     * current configuration from being reset when {@link #applyConfiguration
     * applyConfiguration} is called.
     * @param toMemory if {@code true} only replaces the initial result log
     *                 configuration held in memory.
     *                 if {@code false}, the {@link
     * #getConfigurationMBean configuration MBean} will be asked to commit
     * the whole configuration to the configuration file.
     *
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws JMException The MBeanServer failed to call the underlying MBean.
     **/
    public void applyCurrentResultLogConfig(boolean toMemory)
        throws IOException, JMException;

    /**
     * Instruct the {@code ScanManagerMXBean} to use another {@link
     * ScanDirConfigMXBean configuration MBean}.
     * <p>This method doesn't {@link #applyConfiguration apply} the new
     * configuration. If you want to apply the new configuration, you should
     * additionally call {@link #applyConfiguration
     * applyConfiguration(true|false)}. Note that you cannot apply a
     * configuration as long as a scan session is scheduled or running.
     * In that case you will need to wait for that session to complete
     * or call {@link #stop} to stop it.
     * </p>
     * @param config A proxy to the {@link ScanDirConfigMXBean} that holds
     * the new configuration for the application.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     */
    public void setConfigurationMBean(ScanDirConfigMXBean config)
        throws IOException, InstanceNotFoundException;
    /**
     * Gets the current configuration MBean.
     * @return A proxy to the current configuration MBean.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public ScanDirConfigMXBean getConfigurationMBean()
        throws IOException, InstanceNotFoundException;
    /**
     * This method creates a new alternate {@link ScanDirConfigMXBean}.
     *
     * <p>You will need to call {@link #setConfigurationMBean
     * setConfigurationMBean} if you
     * want this new {@link ScanDirConfigMXBean} to become the
     * current configuration MBean.
     * </p>
     * <p>
     * This new {@link ScanDirConfigMXBean} will be unregistered automatically
     * by the {@code ScanManagerMXBean} when the {@code ScanManagerMXBean}
     * is unregistered.
     * </p>
     * @param name The short name for the new {@link ScanDirConfigMXBean}.
     *        This name will be used in the ObjectName <code>name=</code> key
     *        of the new {@link ScanDirConfigMXBean}.
     * @param filename The path of the file from which the new {@link
     *        ScanDirConfigMXBean} can {@link ScanDirConfigMXBean#load load} or
     *        {@link ScanDirConfigMXBean#save save} its configuration data.
     *        Note that even if the file exists and contain a valid
     *        configuration, you will still need to call {@link
     *        ScanDirConfigMXBean#load load} to make the {@link
     *        ScanDirConfigMXBean} load its configuration data.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws JMException The MBeanServer failed to call the underlying MBean.
     * @return A proxy to the created {@link ScanDirConfigMXBean}.
     */
    public ScanDirConfigMXBean createOtherConfigurationMBean(String name,
            String filename)
        throws JMException, IOException;
}
