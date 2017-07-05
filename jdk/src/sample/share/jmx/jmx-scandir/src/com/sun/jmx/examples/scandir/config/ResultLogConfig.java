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


package com.sun.jmx.examples.scandir.config;

import java.util.Arrays;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The <code>ResultLogConfig</code> Java Bean is used to model
 * the initial configuration of the {@link
 * com.sun.jmx.examples.scandir.ResultLogManagerMXBean}.
 *
 * <p>
 * This class is annotated for XML binding.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
@XmlRootElement(name="ResultLogConfig",
        namespace=XmlConfigUtils.NAMESPACE)
public class ResultLogConfig {

    //
    // A logger for this class.
    //
    // private static final Logger LOG =
    //        Logger.getLogger(ResultLogConfig.class.getName());

    /**
     * The path to the result log file. {@code null} means that logging to
     * file is disabled.
     */
    private String logFileName;

    /**
     * Maximum number of record that will be logged in the log file before
     * switching to a new log file.
     */
    private long logFileMaxRecords;

    /**
     * The maximum number of records that can be contained in the memory log.
     * When this number is reached, the memory log drops its eldest record
     * to make way for the new one.
     */
    private int memoryMaxRecords;

    /**
     * Creates a new instance of ResultLogConfig
     */
    public ResultLogConfig() {
    }

    /**
     * Gets the path to the result log file. {@code null} means that logging to
     * file is disabled.
     * @return the path to the result log file.
     */
    @XmlElement(name="LogFileName",namespace=XmlConfigUtils.NAMESPACE)
    public String getLogFileName() {
        return this.logFileName;
    }

    /**
     * Sets the path to the result log file. {@code null} means that
     * logging to file is disabled.
     * @param logFileName the path to the result log file.
     */
    public void setLogFileName(String logFileName) {
        this.logFileName = logFileName;
    }

    /**
     * Gets the maximum number of record that will be logged in the log file
     * before switching to a new log file.
     * A 0 or negative value means no limit.
     * @return the maximum number of record that will be logged in the log file.
     */
    @XmlElement(name="LogFileMaxRecords",namespace=XmlConfigUtils.NAMESPACE)
    public long getLogFileMaxRecords() {
        return this.logFileMaxRecords;
    }

    /**
     * Sets the maximum number of record that will be logged in the log file
     * before switching to a new log file.
     * A 0 or negative value means no limit.
     * @param logFileMaxRecords the maximum number of record that will be
     * logged in the log file.
     */
    public void setLogFileMaxRecords(long logFileMaxRecords) {
        this.logFileMaxRecords = logFileMaxRecords;
    }

    /**
     * Gets the maximum number of records that can be contained in the memory
     * log.
     * When this number is reached, the memory log drops its eldest record
     * to make way for the new one.
     * @return the maximum number of records that can be contained in the
     * memory log.
     */
    @XmlElement(name="MemoryMaxRecords",namespace=XmlConfigUtils.NAMESPACE)
    public int getMemoryMaxRecords() {
        return this.memoryMaxRecords;
    }

    /**
     * Sets the maximum number of records that can be contained in the memory
     * log.
     * When this number is reached, the memory log drops its eldest record
     * to make way for the new one.
     * @param memoryMaxRecords the maximum number of records that can be
     * contained in the memory log.
     */
    public void setMemoryMaxRecords(int memoryMaxRecords) {
        this.memoryMaxRecords = memoryMaxRecords;
    }

    private Object[] toArray() {
        final Object[] thisconfig = {
            memoryMaxRecords,logFileMaxRecords,logFileName
        };
        return thisconfig;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ResultLogConfig)) return false;
        final ResultLogConfig other = (ResultLogConfig)o;
        return Arrays.deepEquals(toArray(),other.toArray());
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(toArray());
    }
}
