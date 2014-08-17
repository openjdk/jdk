/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.jmx.examples.scandir.config.ResultRecord;
import java.io.IOException;
import javax.management.InstanceNotFoundException;

/**
 * The <code>ResultLogManagerMXBean</code> is in charge of managing result logs.
 * {@link DirectoryScanner DirectoryScanners} can be configured to log a
 * {@link ResultRecord} whenever they take action upon a file that
 * matches their set of matching criteria.
 * The <code>ResultLogManagerMXBean</code> is responsible for storing these
 * results in its result logs.
 * <p>The <code>ResultLogManagerMXBean</code>
 * will let you interactively clear these result logs, change their
 * capacity, and decide where (memory or file or both) the
 * {@link ResultRecord ResultRecords} should be stored.
 * <p>The memory log is useful in so far that its content can be interactively
 * returned by the <code>ResultLogManagerMXBean</code>.
 * The file log doesn't have this facility.
 * <p>The result logs are intended to be used by e.g. an offline program that
 * would take some actions on the files that were matched by the scanners
 * criteria:
 * <p>The <i>scandir</i> application could be configured to only produce logs
 * (i.e. takes no action but logging the matching files), and the real
 * action (e.g. mail the result log to the engineer which maintains the lab,
 * or parse the log and prepare and send a single mail to the matching
 * files owners, containing the list of file he/she should consider deleting)
 * could be performed by such another program/module.
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public interface ResultLogManagerMXBean {

    /**
     * Creates a new log file in which to store results.
     * <p>When this method is called, the {@link ResultLogManager} will stop
     * logging in its current log file and use the new specified file instead.
     * If that file already exists, it will be renamed by appending a '~' to
     * its name, and a new empty file with the name specified by
     * <var>basename</var> will be created.
     * </p>
     * <p>Calling this method has no side effect on the {@link
     * com.sun.jmx.examples.scandir.config.ScanManagerConfig#getInitialResultLogConfig
     * InitialResultLogConfig} held in the {@link ScanDirConfigMXBean}
     * configuration. To apply these new values to the
     * {@link ScanDirConfigMXBean}
     * configuration, you must call {@link
     * ScanManagerMXBean#applyCurrentResultLogConfig
     * ScanManagerMXBean.applyCurrentResultLogConfig}.
     *<p>
     * @param basename The name of the new log file. This will be the
     *        new name returned by {@link #getLogFileName}.
     * @param maxRecord maximum number of records to log in the specified file
     *        before creating a new file. <var>maxRecord</var> will be the
     *        new value returned by {@link #getLogFileCapacity}.
     *        When that maximum number of
     *        records is reached the {@link ResultLogManager} will rename
     *        the file by appending a '~' to its name, and a new empty
     *        log file will be created.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void newLogFile(String basename, long maxRecord)
        throws IOException, InstanceNotFoundException;

    /**
     * Logs a result record to the active result logs (memory,file,both,or none)
     * depending on how this MBean is currently configured.
     * @see #getLogFileName()
     * @see #getMemoryLogCapacity()
     * @param record The result record to log.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     */
    public void log(ResultRecord record)
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the name of the current result log file.
     * <p><code>null</code> means that no log file is configured: logging
     * to file is disabled.
     * </p>
     * @return The name of the current result log file, or <code>null</code>
     *         if logging to file is disabled.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public String getLogFileName()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the whole content of the memory log. This cannot exceed
     * {@link #getMemoryLogCapacity} records.
     *
     * @return the whole content of the memory log.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public ResultRecord[] getMemoryLog()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the maximum number of records that can be logged in the
     * memory log.
     * <p>
     * A non positive value - <code>0</code> or negative - means that
     * logging in memory is disabled.
     * </p>
     * <p>The memory log is a FIFO: when its maximum capacity is reached, its
     *    head element is removed to make place for a new element at its tail.
     * </p>
     * @return The maximum number of records that can be logged in the
     * memory log. A value {@code <= 0} means that logging in memory is
     * disabled.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public int getMemoryLogCapacity()
        throws IOException, InstanceNotFoundException;

    /**
     * Sets the maximum number of records that can be logged in the
     * memory log.
     * <p>The memory log is a FIFO: when its maximum capacity is reached, its
     *    head element is removed to make place for a new element at its tail.
     * </p>
     * @param size The maximum number of result records that can be logged in the memory log.  <p>
     * A non positive value - <code>0</code> or negative - means that
     * logging in memory is disabled. It will also have the side
     * effect of clearing the memory log.
     * </p>
     *
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     */
    public void setMemoryLogCapacity(int size)
        throws IOException, InstanceNotFoundException;

    /**
     * Sets the maximum number of records that can be logged in the result log
     * file.
     * <p>When that maximum number of
     * records is reached the {@link ResultLogManager} will rename
     * the result log file by appending a '~' to its name, and a new empty
     * log file will be created.
     * </p>
     * <p>If logging to file is disabled calling this method
     *    is irrelevant.
     * </p>
     * @param maxRecord maximum number of records to log in the result log file.
     * @see #getLogFileName()
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void setLogFileCapacity(long maxRecord)
        throws IOException, InstanceNotFoundException;

    /**
     * Gets the maximum number of records that can be logged in the result log
     * file.
     * <p>When that maximum number of
     * records is reached the {@link ResultLogManager} will rename
     * the result log file by appending a '~' to its name, and a new empty
     * log file will be created.
     * </p>
     * @see #getLogFileName()
     * @return The maximum number of records that can be logged in the result
     *         log file.
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public long getLogFileCapacity()
        throws IOException, InstanceNotFoundException;

    /**
     * Gets The number of records that have been logged in the
     * current result log file. This will always be less than
     * {@link #getLogFileCapacity()}.
     * @return The number of records in the
     *         current result log file.
     *
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public long getLoggedCount()
        throws IOException, InstanceNotFoundException;

    /**
     * Clears the memory log and result log file.
     *
     * @throws IOException A connection problem occurred when accessing
     *                     the underlying resource.
     * @throws InstanceNotFoundException The underlying MBean is not
     *         registered in the MBeanServer.
     **/
    public void clearLogs()
        throws IOException, InstanceNotFoundException;
}
