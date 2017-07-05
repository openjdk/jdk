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

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The <code>DirectoryScannerConfig</code> Java Bean is used to model
 * the configuration of a {@link
 * com.sun.jmx.examples.scandir.DirectoryScannerMXBean}.
 * <p>
 * This class is annotated for XML binding.
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
@XmlRootElement(name="DirectoryScanner",
        namespace=XmlConfigUtils.NAMESPACE)
public class DirectoryScannerConfig {

    //
    // A logger for this class.
    //
    // private static final Logger LOG =
    //        Logger.getLogger(DirectoryScannerConfig.class.getName());

    /**
     * This enumeration is used to model the actions that a {@link
     * com.sun.jmx.examples.scandir.DirectoryScannerMXBean
     * DirectoryScannerMXBean} should take when a file matches its set
     * of matching criteria.
     **/
    public enum Action {
        /**
         * Indicates that the {@code DirectoryScannerMXBean} should
         * emit a {@code Notification} when a matching file is found.
         */
        NOTIFY,
        /**
         * Indicates that the {@code DirectoryScannerMXBean} should
         * delete the matching files.
         */
        DELETE,
        /**
         * Indicates that the {@code DirectoryScannerMXBean} should
         * log the actions that were taken on the matching files.
         */
        LOGRESULT };

    // A short name for the Directory Scanner
    // This name is used for the value of the {@code name=} key in the
    // {@code DirectoryScannerMXBean} ObjectName.
    private String name;

    // The root directory of the Directory Scanner
    private String rootDirectory;

    // List of filters identifying files that should be selected.
    // A file is selected if at least one filter matches.
    //
    private final List<FileMatch> includeFiles =
            new ArrayList<FileMatch>();

    // List of filters identifying files that should be excluded.
    // A file is excluded if at least one filter matches.
    //
    private final List<FileMatch> excludeFiles =
            new ArrayList<FileMatch>();


    // The actions that this Directory Scanner should carry out when a
    // file is selected. Default is NOTIFY and LOGRESULT.
    //
    private Action[] actions = { Action.NOTIFY, Action.LOGRESULT };

    /**
     * Creates a new instance of {@code DirectoryScannerConfig}.
     * We keep this empty constructor to make XML binding easier.
     * You shouldn't use this constructor directly:
     * use {@link #DirectoryScannerConfig(String)
     * DirectoryScannerConfig(String name)} instead.
     * @deprecated <p>Tagged deprecated so that a compiler warning is issued.
     *             Use {@link #DirectoryScannerConfig(String)
     *                  DirectoryScannerConfig(String name)} instead.
     *             </p>
     **/
    public DirectoryScannerConfig() {
        this(null);
    }

    /**
     * Creates a new instance of {@code DirectoryScannerConfig}.
     * @param name A short name for the Directory Scanner. This name is used for
     *        the value of the {@code name=} key in the
     *        {@code DirectoryScannerMXBean} ObjectName.
     **/
    public DirectoryScannerConfig(String name) {
        this.name = name;
        rootDirectory = null;
    }

    /**
     * Gets the root directory configured for that Directory Scanner.
     * @return the root directory at which the directory scanner should start
     *         scanning.
     **/
    @XmlElement(name="RootDirectory",namespace=XmlConfigUtils.NAMESPACE)
    public String getRootDirectory() {
        return rootDirectory;
    }

    /**
     * Configures a root directory for that Directory Scanner.
     * @param root The root directory at which the directory scanner should
     *        start scanning.
     **/
    public void setRootDirectory(String root) {
        rootDirectory=root;
    }


    /**
     * Gets the short name of this directory scanner.
     *
     * <p>
     * This name is used for the value of the {@code name=} key in the
     * {@code DirectoryScannerMXBean} ObjectName.
     * </p>
     *
     * @return the short name of this directory scanner.
     **/
    @XmlAttribute(name="name",required=true)
    public String getName() {
        return this.name;
    }

    /**
     * Setter for property {@link #getName() name}.
     * Once set its value cannot change.
     * @param name New value of property name.
     * @throws IllegalArgumentException if {@code name} is already set to a
     *         different non null value.
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
     * Getter for property includeFiles.
     * This is an array of filters identifying files that should be selected.
     * A file is selected if at least one filter matches.
     * @return Value of property includeFiles.
     */
    @XmlElementWrapper(name="IncludeFiles",
            namespace=XmlConfigUtils.NAMESPACE)
    @XmlElementRef
    public FileMatch[] getIncludeFiles() {
        synchronized(includeFiles) {
            return includeFiles.toArray(new FileMatch[0]);
        }
    }

    /**
     * Adds a filter to the includeFiles property.
     * A file is selected if at least one filter matches.
     * @param include A filter identifying files that should be selected.
     */
    public void addIncludeFiles(FileMatch include) {
        if (include == null)
            throw new IllegalArgumentException("null");
        synchronized (includeFiles) {
            includeFiles.add(include);
        }
    }

    /**
     * Setter for property includeFiles.
     * @param includeFiles New value of property includeFiles.
     *        This is an array of filters identifying files
     *        that should be selected. A file is selected if at least
     *        one filter matches.
     */
    public void setIncludeFiles(FileMatch[] includeFiles) {
        synchronized (this.includeFiles) {
            this.includeFiles.clear();
            if (includeFiles == null) return;
            this.includeFiles.addAll(Arrays.asList(includeFiles));
        }
    }

    /**
     * Getter for property excludeFiles.
     * This is an array of filters identifying files that should be excluded.
     * A file is excluded if at least one filter matches.
     * @return Value of property excludeFiles.
     */
    @XmlElementWrapper(name="ExcludeFiles",
            namespace=XmlConfigUtils.NAMESPACE)
    @XmlElementRef
    public FileMatch[] getExcludeFiles() {
        synchronized(excludeFiles) {
            return excludeFiles.toArray(new FileMatch[0]);
        }
    }

    /**
     * Setter for property excludeFiles.
     * @param excludeFiles New value of property excludeFiles.
     *        This is an array of filters identifying files
     *        that should be excluded. A file is excluded if at least
     *        one filter matches.
     */
    public void setExcludeFiles(FileMatch[] excludeFiles) {
        synchronized (this.excludeFiles) {
            this.excludeFiles.clear();
            if (excludeFiles == null) return;
            this.excludeFiles.addAll(Arrays.asList(excludeFiles));
        }
    }

    /**
     * Adds a filter to the excludeFiles property.
     * A file is excluded if at least one filter matches.
     * @param exclude A filter identifying files that should be excluded.
     */
    public void addExcludeFiles(FileMatch exclude) {
        if (exclude == null)
            throw new IllegalArgumentException("null");
        synchronized (excludeFiles) {
            this.excludeFiles.add(exclude);
        }
    }

    /**
     * Gets the list of actions that this Directory Scanner should carry
     * out when a file is selected. Default is NOTIFY and LOGRESULT.

     * @return The list of actions that this Directory Scanner should carry
     * out when a file is selected.
     */
    @XmlElement(name="Actions",namespace=XmlConfigUtils.NAMESPACE)
    @XmlList
    public Action[] getActions() {
       return  (actions == null)?null:actions.clone();
    }

    /**
     * Sets the list of actions that this Directory Scanner should carry
     * out when a file is selected. Default is NOTIFY and LOGRESULT.

     * @param actions The list of actions that this Directory Scanner should
     * carry out when a file is selected.
     */
    public void setActions(Action[] actions) {
        this.actions = (actions == null)?null:actions.clone();
    }

    /**
     * Builds a {@code FileFilter} from the {@link #getIncludeFiles
     * includeFiles} and {@link #getExcludeFiles excludeFiles} lists.
     * A file will be accepted if it is selected by at least one of
     * the filters in {@link #getIncludeFiles includeFiles}, and is
     * not excluded by any of the filters in {@link
     * #getExcludeFiles excludeFiles}. If there's no filter in
     * {@link #getIncludeFiles includeFiles}, then a file is accepted
     * simply if it is not excluded by any of the filters in {@link
     * #getExcludeFiles excludeFiles}.
     *
     * @return A new {@code FileFilter}  created from the current snapshot
     *         of the {@link #getIncludeFiles
     * includeFiles} and {@link #getExcludeFiles excludeFiles} lists.
     *         Later modification of these lists will not affect the
     *         returned {@code FileFilter}.
     **/
    public FileFilter buildFileFilter() {
        final FileFilter[] ins = getIncludeFiles();
        final FileFilter[] outs = getExcludeFiles();
        final FileFilter filter = new FileFilter() {
            public boolean accept(File f) {
                boolean result = false;
                // If no include filter, all files are included.
                if (ins != null) {
                    for (FileFilter in: ins) {
                        // if one filter accepts it, file is included
                        if (!in.accept(f)) continue;

                        // file is accepted, include it
                        result=true;
                        break;
                    }
                } else result= true;
                if (result == false) return false;

                // The file is in the include list. Let's see if it's not
                // in the exclude list...
                //
                if (outs != null) {
                    for (FileFilter out: outs) {
                        // if one filter accepts it, file is excluded
                        if (!out.accept(f)) continue;

                        // file is accepted, exclude it.
                        result=false;
                        break;
                    }
                }
                return result;
            }
        };
        return filter;
    }

    // Used for equality - see equals().
    private Object[] toArray() {
        final Object[] thisconfig = {
            name,rootDirectory,actions,excludeFiles,includeFiles
        };
        return thisconfig;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DirectoryScannerConfig)) return false;
        final DirectoryScannerConfig other = (DirectoryScannerConfig)o;
        final Object[] thisconfig = toArray();
        final Object[] otherconfig = other.toArray();
        return Arrays.deepEquals(thisconfig,otherconfig);
    }

    @Override
    public int hashCode() {
        final String key = name;
        if (key == null) return 0;
        else return key.hashCode();
    }


}
