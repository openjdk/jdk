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


package com.sun.jmx.examples.scandir.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * The <code>ScanManagerConfig</code> Java Bean is used to model
 * the configuration of the {@link
 * com.sun.jmx.examples.scandir.ScanManagerMXBean ScanManagerMXBean}.
 *
 * The {@link
 * com.sun.jmx.examples.scandir.ScanManagerMXBean ScanManagerMXBean} will
 * use this configuration to initialize the {@link
 * com.sun.jmx.examples.scandir.ResultLogManagerMXBean ResultLogManagerMXBean}
 * and create the {@link
 * com.sun.jmx.examples.scandir.DirectoryScannerMXBean DirectoryScannerMXBeans}
 * <p>
 * This class is annotated for XML binding.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 **/
@XmlRootElement(name="ScanManager",
        namespace="jmx:com.sun.jmx.examples.scandir.config")
public class ScanManagerConfig {

    // A logger for this class
    //
    // private static final Logger LOG =
    //        Logger.getLogger(ScanManagerConfig.class.getName());

    /**
     * A set of DirectoryScannerConfig objects indexed by their names.
     **/
    private final Map<String, DirectoryScannerConfig> directoryScanners;

    /**
     * The initial Result Log configuration.
     */
    private ResultLogConfig initialResultLogConfig;

    /**
     * Holds value of property name. The name of the configuration
     *       usually corresponds to
     *       the value of the {@code name=} key of the {@code ObjectName}
     *       of the {@link
     *       com.sun.jmx.examples.scandir.ScanDirConfigMXBean
     *       ScanDirConfigMXBean} which owns this configuration.
     **/
    private String name;

    /**
     * Creates a new instance of ScanManagerConfig.
     * <p>You should not use this constructor directly, but use
     *    {@link #ScanManagerConfig(String)} instead.
     * </p>
     * <p>This constructor is tagged deprecated so that the compiler
     *    will generate a warning if it is used by mistake.
     * </p>
     * @deprecated Use {@link #ScanManagerConfig(String)} instead. This
     *             constructor is used through reflection by the XML
     *             binding framework.
     */
    public ScanManagerConfig() {
        this(null,true);
    }

    /**
     * Creates a new instance of ScanManagerConfig.
     * @param name The name of the configuration which usually corresponds to
     *       the value of the {@code name=} key of the {@code ObjectName}
     *       of the {@link
     *       com.sun.jmx.examples.scandir.ScanDirConfigMXBean
     *       ScanDirConfigMXBean} which owns this configuration.
     **/
    public ScanManagerConfig(String name) {
        this(name,false);
    }

    // Our private constructor...
    private ScanManagerConfig(String name, boolean allowsNull) {
        if (name == null && allowsNull==false)
            throw new IllegalArgumentException("name=null");
        this.name = name;
        directoryScanners = new LinkedHashMap<String,DirectoryScannerConfig>();
        this.initialResultLogConfig = new ResultLogConfig();
        this.initialResultLogConfig.setMemoryMaxRecords(1024);
    }

    // Creates an array for deep equality.
    private Object[] toArray() {
        final Object[] thisconfig = {
            name,directoryScanners,initialResultLogConfig
        };
        return thisconfig;
    }

    // equals
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ScanManagerConfig)) return false;
        final ScanManagerConfig other = (ScanManagerConfig)o;
        if (this.directoryScanners.size() != other.directoryScanners.size())
            return false;
        return Arrays.deepEquals(toArray(),other.toArray());
    }

    @Override
    public int hashCode() {
        final String key = name;
        if (key == null) return 0;
        else return key.hashCode();
    }

    /**
     * Gets the name of this configuration. The name of the configuration
     *       usually corresponds to
     *       the value of the {@code name=} key of the {@code ObjectName}
     *       of the {@link
     *       com.sun.jmx.examples.scandir.ScanDirConfigMXBean
     *       ScanDirConfigMXBean} which owns this configuration.
     * @return The name of this configuration.
     */
    @XmlAttribute(name="name",required=true)
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of this configuration. The name of the configuration
     *       usually corresponds to
     *       the value of the {@code name=} key of the {@code ObjectName}
     *       of the {@link
     *       com.sun.jmx.examples.scandir.ScanDirConfigMXBean
     *       ScanDirConfigMXBean} which owns this configuration.
     *       <p>Once set this value cannot change.</p>
     * @param name The name of this configuration.
     */
    public void setName(String name) {
        if (this.name == null)
            this.name = name;
        else if (name == null)
            throw new IllegalArgumentException("name=null");
        else if (!name.equals(this.name))
            throw new IllegalArgumentException("name="+name);
    }

   /**
    * Gets the list of Directory Scanner configured by this
    * configuration. From each element in this list, the
    * {@link com.sun.jmx.examples.scandir.ScanManagerMXBean ScanManagerMXBean}
    * will create, initialize, and register a {@link
    * com.sun.jmx.examples.scandir.DirectoryScannerMXBean}.
    * @return The list of Directory Scanner configured by this configuration.
    */
    @XmlElementWrapper(name="DirectoryScannerList",
            namespace=XmlConfigUtils.NAMESPACE)
    @XmlElementRef
    public DirectoryScannerConfig[] getScanList() {
        return directoryScanners.values().toArray(new DirectoryScannerConfig[0]);
    }

   /**
    * Sets the list of Directory Scanner configured by this
    * configuration. From each element in this list, the
    * {@link com.sun.jmx.examples.scandir.ScanManagerMXBean ScanManagerMXBean}
    * will create, initialize, and register a {@link
    * com.sun.jmx.examples.scandir.DirectoryScannerMXBean}.
    * @param scans The list of Directory Scanner configured by this configuration.
    */
    public void setScanList(DirectoryScannerConfig[] scans) {
        directoryScanners.clear();
        for (DirectoryScannerConfig scan : scans)
            directoryScanners.put(scan.getName(),scan);
    }

    /**
     * Get a directory scanner by its name.
     *
     * @param name The name of the directory scanner. This is the
     *             value returned by {@link
     *             DirectoryScannerConfig#getName()}.
     * @return The named {@code DirectoryScannerConfig}
     */
    public DirectoryScannerConfig getScan(String name) {
        return directoryScanners.get(name);
    }

    /**
     * Adds a directory scanner to the list.
     * <p>If a directory scanner
     * configuration by that name already exists in the list, it will
     * be replaced by the given <var>scan</var>.
     * </p>
     * @param scan The {@code DirectoryScannerConfig} to add to the list.
     * @return The replaced {@code DirectoryScannerConfig}, or {@code null}
     *         if there was no {@code DirectoryScannerConfig} by that name
     *         in the list.
     */
    public DirectoryScannerConfig putScan(DirectoryScannerConfig scan) {
        return this.directoryScanners.put(scan.getName(),scan);
    }

    // XML value of  this object.
    public String toString() {
        return XmlConfigUtils.toString(this);
    }

    /**
     * Removes the named directory scanner from the list.
     *
     * @param name The name of the directory scanner. This is the
     *             value returned by {@link
     *             DirectoryScannerConfig#getName()}.
     * @return The removed {@code DirectoryScannerConfig}, or {@code null}
     *         if there was no directory scanner by that name in the list.
     */
    public DirectoryScannerConfig removeScan(String name) {
       return this.directoryScanners.remove(name);
    }

    /**
     * Gets the initial Result Log Configuration.
     * @return The initial Result Log Configuration.
     */
    @XmlElement(name="InitialResultLogConfig",namespace=XmlConfigUtils.NAMESPACE)
    public ResultLogConfig getInitialResultLogConfig() {
        return this.initialResultLogConfig;
    }

    /**
     * Sets the initial Result Log Configuration.
     * @param initialLogConfig The initial Result Log Configuration.
     */
    public void setInitialResultLogConfig(ResultLogConfig initialLogConfig) {
        this.initialResultLogConfig = initialLogConfig;
    }

    /**
     * Creates a copy of this object, with the specified name.
     * @param newname the name of the copy.
     * @return A copy of this object.
     **/
    public ScanManagerConfig copy(String newname) {
        return copy(newname,this);
    }

    // Copy by XML cloning, then change the name.
    //
    private static ScanManagerConfig
            copy(String newname, ScanManagerConfig other) {
        ScanManagerConfig newbean = XmlConfigUtils.xmlClone(other);
        newbean.name = newname;
        return newbean;
    }
}
