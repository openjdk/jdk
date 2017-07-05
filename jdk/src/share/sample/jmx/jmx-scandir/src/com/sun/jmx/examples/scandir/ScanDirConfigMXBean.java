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

import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import com.sun.jmx.examples.scandir.config.ScanManagerConfig;
import java.io.IOException;
import javax.management.InstanceNotFoundException;

/**
 * <p>The <code>ScanDirConfigMXBean</code> is in charge of the
 * <i>scandir</i> application configuration.
 * </p>
 * <p>The <code>ScanDirConfigMXBean</code> is an MBean which is able to
 * load and save the <i>scandir</i> application configuration to and from an
 * XML file.
 * </p>
 * <p>
 * It will let you also interactively modify that configuration, which you
 * can later save to the file, by calling {@link #save}, or discard, by
 * reloading the file without saving - see {@link #load}.
 * </p>
 * <p>
 * There can be as many <code>ScanDirConfigMXBean</code> registered
 * in the MBeanServer as you like, but only one of them will be identified as
 * the current configuration of the {@link ScanManagerMXBean}.
 * You can switch to another configuration by calling {@link
 * ScanManagerMXBean#setConfigurationMBean
 * ScanManagerMXBean.setConfigurationMBean}.
 * </p>
 * <p>
 * Once the current configuration has been loaded (by calling {@link #load})
 * or modified (by calling one of {@link #addDirectoryScanner
 * addDirectoryScanner}, {@link #removeDirectoryScanner removeDirectoryScanner}
 * or {@link #setConfiguration setConfiguration}) it can be pushed
 * to the {@link ScanManagerMXBean} by calling {@link
 * ScanManagerMXBean#applyConfiguration
 * ScanManagerMXBean.applyConfiguration(true)} -
 * <code>true</code> means that we apply the configuration from memory,
 * without first reloading the file.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public interface ScanDirConfigMXBean {
    /**
     * This state tells whether the configuration reflected by the
     * {@link ScanDirConfigMXBean} was loaded in memory, saved to the
     * configuration file, or modified since last saved.
     * Note that this state doesn't tell whether the configuration was
     * applied by the {@link ScanManagerMXBean}.
     **/
    public enum SaveState {
        /**
         * Initial state: the {@link ScanDirConfigMXBean} is created, but
         * neither {@link #load} or  {@link #save} was yet called.
         **/
        CREATED,

        /**
         * The configuration reflected by the {@link ScanDirConfigMXBean} has
         * been loaded, but not modified yet.
         **/
        LOADED,

        /**
         * The configuration was modified. The modifications are held in memory.
         * Call {@link #save} to save them to the file, or {@link #load} to
         * reload the file and discard them.
         **/
        MODIFIED,

        /**
         * The configuration was saved.
         **/
        SAVED
    };

    /**
     * Loads the configuration from the {@link
     * #getConfigFilename configuration file}.
     * <p>Any unsaved modification will be lost. The {@link #getSaveState state}
     * is switched to {@link SaveState#LOADED LOADED}.
     * </p>
     * <p>
     * This action has no effect on the {@link ScanManagerMXBean} until
     * {@link ScanManagerMXBean#getConfigurationMBean ScanManagerMXBean}
     * points to this MBean and {@link ScanManagerMXBean#applyConfiguration
     * ScanManagerMXBean.applyConfiguration} is called.
     * </p>
     * @see #getSaveState()
     * @throws IOException The configuration couldn't be loaded from the file,
     *                     e.g. because the file doesn't exist or isn't
     *                     readable.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void load()
        throws IOException, InstanceNotFoundException;

    /**
     * Saves the configuration to the {@link
     * #getConfigFilename configuration file}.
     *
     * <p>If the configuration file doesn't exists, this method will
     *    attempt to create it. Otherwise, the existing file will
     *    be renamed by appending a '~' to its name, and a new file
     *    will be created, in which the configuration will be saved.
     * The {@link #getSaveState state}
     * is switched to {@link SaveState#SAVED SAVED}.
     * </p>
     * <p>
     * This action has no effect on the {@link ScanManagerMXBean}.
     * </p>
     * @see #getSaveState()
     *
     * @throws IOException The configuration couldn't be saved to the file,
     *                     e.g. because the file couldn't be created.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void save()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the name of the configuration file.
     * <p>If the configuration file doesn't exists, {@link #load} will fail
     * and {@link #save} will attempt to create the file.
     * </p>
     *
     * @return The configuration file name for this MBean.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public String getConfigFilename()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the current configuration data.
     * <p>
     * This method returns the configuration data which is currently held
     * in memory.
     * </p>
     * <p>Call {@link #load} to reload the data from the configuration
     *    file, and {@link #save} to save the data to the configuration
     *    file.
     * </p>
     * @see #getSaveState()
     * @return The current configuration data in memory.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public ScanManagerConfig getConfiguration()
        throws IOException, InstanceNotFoundException;

    /**
     * Sets the current configuration data.
     * <p>
     * This method replaces the configuration data in memory.
     * The {@link #getSaveState state} is switched to {@link
     * SaveState#MODIFIED MODIFIED}.
     * </p>
     * <p>Calling {@link #load} will reload the data from the configuration
     *    file, and all modifications will be lost.
     *    Calling {@link #save} will save the modified data to the configuration
     *    file.
     * </p>
     * <p>
     * This action has no effect on the {@link ScanManagerMXBean} until
     * {@link ScanManagerMXBean#getConfigurationMBean ScanManagerMXBean}
     * points to this MBean and {@link ScanManagerMXBean#applyConfiguration
     * ScanManagerMXBean.applyConfiguration} is called.
     * </p>
     * @param config The new configuration data.
     * @see #getSaveState()
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     */
    public void setConfiguration(ScanManagerConfig config)
        throws IOException, InstanceNotFoundException;

    /**
     * Adds a new directory scanner to the current configuration data.
     * <p>
     * This method updates the configuration data in memory, adding
     * a {@link DirectoryScannerConfig} to the {@link
     * ScanManagerConfig#getScanList directory scanner list}.
     * The {@link #getSaveState state} is switched to {@link
     * SaveState#MODIFIED MODIFIED}.
     * </p>
     * <p>Calling {@link #load} will reload the data from the configuration
     *    file, and all modifications will be lost.
     *    Calling {@link #save} will save the modified data to the configuration
     *    file.
     * </p>
     * <p>
     * This action has no effect on the {@link ScanManagerMXBean} until
     * {@link ScanManagerMXBean#getConfigurationMBean ScanManagerMXBean}
     * points to this MBean and {@link ScanManagerMXBean#applyConfiguration
     * ScanManagerMXBean.applyConfiguration} is called.
     * </p>
     * @param name A name for the new directory scanner. This is the value
     *             that will be later used in the {@link DirectoryScannerMXBean}
     *             ObjectName for the <code>name=</code> key.
     * @param dir The root directory at which this scanner will start scanning.
     * @param filePattern A {@link java.util.regex.Pattern regular expression}
     *        to match against a selected file name.
     * @param sizeExceedsMaxBytes Only file whose size exceeds that limit will
     *        be selected. <code.0</code> or  a
     *        negative value means no limit.
     * @param sinceLastModified Select files which haven't been modified for
     *        that number of milliseconds - i.e.
     *        {@code sinceLastModified=3600000} will exclude files which
     *        have been modified in the last hour.
     *        The date of last modification is ignored if <code>0</code> or  a
     *        negative value is provided.
     * @see #getSaveState()
     * @return The added <code>DirectoryScannerConfig</code>.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public DirectoryScannerConfig
            addDirectoryScanner(String name, String dir, String filePattern,
                                long sizeExceedsMaxBytes, long sinceLastModified)
        throws IOException, InstanceNotFoundException;

    /**
     * Removes a directory scanner from the current configuration data.
     * <p>
     * This method updates the configuration data in memory, removing
     * a {@link DirectoryScannerConfig} from the {@link
     * ScanManagerConfig#getScanList directory scanner list}.
     * The {@link #getSaveState state} is switched to {@link
     * SaveState#MODIFIED MODIFIED}.
     * </p>
     * <p>Calling {@link #load} will reload the data from the configuration
     *    file, and all modifications will be lost.
     *    Calling {@link #save} will save the modified data to the configuration
     *    file.
     * </p>
     * <p>
     * This action has no effect on the {@link ScanManagerMXBean} until
     * {@link ScanManagerMXBean#getConfigurationMBean ScanManagerMXBean}
     * points to this MBean and {@link ScanManagerMXBean#applyConfiguration
     * ScanManagerMXBean.applyConfiguration} is called.
     * </p>
     * @param name The name of the new directory scanner. This is the value
     *             that is used in the {@link DirectoryScannerMXBean}
     *             ObjectName for the <code>name=</code> key.
     * @return The removed <code>DirectoryScannerConfig</code>.
     * @throws IllegalArgumentException if there's no directory scanner by
     *         that name in the current configuration data.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public DirectoryScannerConfig
            removeDirectoryScanner(String name)
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the save state of the current configuration data.
     * <p>
     * {@link SaveState#CREATED CREATED} means that the configuration data was just
     * created. It has not been loaded from the configuration file.
     * Calling {@link #load} will load the data from the configuration file.
     * Calling {@link #save} will write the empty data to the configuration
     * file.
     * </p>
     * <p>
     * {@link SaveState#LOADED LOADED} means that the configuration data
     * was loaded from the configuration file.
     * </p>
     * <p>
     * {@link SaveState#MODIFIED MODIFIED} means that the configuration data
     * was modified since it was last loaded or saved.
     * Calling {@link #load} will reload the data from the configuration file,
     * and all modifications will be lost.
     * Calling {@link #save} will write the modified data to the configuration
     * file.
     * </p>
     * <p>
     * {@link SaveState#SAVED SAVED} means that the configuration data
     * was saved to the configuration file.
     * </p>
     * <p>
     * This state doesn't indicate whether this MBean configuration data
     * was {@link ScanManagerMXBean#applyConfiguration applied} by the
     * {@link ScanManagerMXBean}.
     * </p>
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     * @return The save state of the {@code ScanDirConfigMXBean}.
     */
    public SaveState getSaveState()
        throws IOException, InstanceNotFoundException;

}
