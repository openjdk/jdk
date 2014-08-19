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
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The <code>FileMatch</code> Java Bean is used to model
 * the configuration of a {@link FileFilter} which
 * matches {@link File files} against a set of criteria.
 * <p>
 * The <code>FileMatch</code> class also implements
 * {@link FileFilter} - applying an {@code AND} on all
 * its conditions. {@code OR} conditions can be obtained
 * by supplying several instances of <code>FileMatch</code>
 * to the encapsulating {@link DirectoryScannerConfig}, which
 * respectively applies an {@code OR} on all its
 * {@code <FileFilter>} elements.
 * </p>
 *
 * <p>
 * This class is annotated for XML binding.
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
@XmlRootElement(name="FileFilter",
        namespace=XmlConfigUtils.NAMESPACE)
public class FileMatch implements FileFilter {

    //
    // A logger for this class.
    //
    // private static final Logger LOG =
    //        Logger.getLogger(FileMatch.class.getName());

    /**
     * A regular expression against which directory names should be matched.
     */
    private String directoryPattern;

    /**
     * A regular expression against which file names should be matched.
     */
    private String filePattern;

    /**
     * File whose size in bytes exceeds this limit will be selected.
     */
    private long sizeExceedsMaxBytes;

    /**
     * A file which will be selected only if it was last modified after
     * this date
     */
    private Date lastModifiedAfter;

    /**
     * A file which will be selected only if it was last modified before
     * this date
     */
    private Date lastModifiedBefore;

    /**
     * Creates a new instance of FileMatch
     */
    public FileMatch() {
    }

    /**
     * Getter for property directoryPattern. This is a regular expression
     * against which directory names should be matched.
     * Applies only to directory, and tells whether a directory should be
     * included or excluded from the search.
     * <p>If File.isDirectory() && directoryPattern!=null &&
     *    File.getName().matches(directoryPattern),
     *    then File matches this filter.<br>
     *    If File.isDirectory() && directoryPattern!=null &&
     *    File.getName().matches(directoryPattern)==false,
     *    then File doesn't match this filter.<br>
     * </p>
     * @see java.util.regex.Pattern
     * @see java.lang.String#matches(java.lang.String)
     * @return Value of property directoryPattern.
     */
    @XmlElement(name="DirectoryPattern",namespace=XmlConfigUtils.NAMESPACE)
    public String getDirectoryPattern() {
        return this.directoryPattern;
    }

    /**
     * Setter for property directoryPattern.
     * @param directoryPattern New value of property directoryPattern.
     * This is a regular expression
     * against which directory names should be {@link #getDirectoryPattern
     * matched}.
     * @see java.util.regex.Pattern
     * @see java.lang.String#matches(java.lang.String)
     */
    public void setDirectoryPattern(String directoryPattern) {
        this.directoryPattern = directoryPattern;
    }

    /**
     * Getter for property filePattern. This is a regular expression
     * against which file names should be matched.
     * Applies only to files.
     * <p>
     *    If File.isDirectory()==false && filePattern!=null &&
     *    File.getName().matches(filePattern)==false,
     *    then File doesn't match this filter.
     * </p>
     * @see java.util.regex.Pattern
     * @see java.lang.String#matches(java.lang.String)
     * @return Value of property filePatern.
     */
    @XmlElement(name="FilePattern",namespace=XmlConfigUtils.NAMESPACE)
    public String getFilePattern() {
        return this.filePattern;
    }

    /**
     * Setter for property filePattern.
     * @param filePattern New value of property filePattern.
     * This is a regular expression
     * against which file names should be {@link #getFilePattern matched}.
     * @see java.util.regex.Pattern
     * @see java.lang.String#matches(java.lang.String)
     */
    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    /**
     * Getter for property sizeExceedsMaxBytes.
     * Ignored if 0 or negative. Otherwise, files whose size in bytes does
     * not exceed this limit will be excluded by this filter.
     *
     * @return Value of property sizeExceedsMaxBytes.
     */
    @XmlElement(name="SizeExceedsMaxBytes",namespace=XmlConfigUtils.NAMESPACE)
    public long getSizeExceedsMaxBytes() {
        return this.sizeExceedsMaxBytes;
    }

    /**
     * Setter for property sizeExceedsMaxBytes.
     * @param sizeLimitInBytes New value of property sizeExceedsMaxBytes.
     * Ignored if 0 or negative. Otherwise, files whose size in bytes does
     * not exceed this limit will be excluded by this filter.
     *
     */
    public void setSizeExceedsMaxBytes(long sizeLimitInBytes) {
        this.sizeExceedsMaxBytes = sizeLimitInBytes;
    }

    /**
     * Getter for property {@code lastModifiedAfter}.
     * A file will be selected only if it was last modified after
     * {@code lastModifiedAfter}.
     * <br>This condition is ignored if {@code lastModifiedAfter} is
     * {@code null}.
     * @return Value of property {@code lastModifiedAfter}.
     */
    @XmlElement(name="LastModifiedAfter",namespace=XmlConfigUtils.NAMESPACE)
    public Date getLastModifiedAfter() {
        return (lastModifiedAfter==null)?null:(Date)lastModifiedAfter.clone();
    }

    /**
     * Setter for property {@code lastModifiedAfter}.
     * @param lastModifiedAfter  A file will be selected only if it was
     * last modified after  {@code lastModifiedAfter}.
     * <br>This condition is ignored if {@code lastModifiedAfter} is
     * {@code null}.
     */
    public void setLastModifiedAfter(Date lastModifiedAfter) {
        this.lastModifiedAfter =
                (lastModifiedAfter==null)?null:(Date)lastModifiedAfter.clone();
    }

    /**
     * Getter for property {@code lastModifiedBefore}.
     * A file will be selected only if it was last modified before
     * {@code lastModifiedBefore}.
     * <br>This condition is ignored if {@code lastModifiedBefore} is
     * {@code null}.
     * @return Value of property {@code lastModifiedBefore}.
     */
    @XmlElement(name="LastModifiedBefore",namespace=XmlConfigUtils.NAMESPACE)
    public Date getLastModifiedBefore() {
        return (lastModifiedBefore==null)?null:(Date)lastModifiedBefore.clone();
    }

    /**
     * Setter for property {@code lastModifiedBefore}.
     * @param lastModifiedBefore  A file will be selected only if it was
     * last modified before {@code lastModifiedBefore}.
     * <br>This condition is ignored if {@code lastModifiedBefore} is
     * {@code null}.
     */
    public void setLastModifiedBefore(Date lastModifiedBefore) {
        this.lastModifiedBefore =
             (lastModifiedBefore==null)?null:(Date)lastModifiedBefore.clone();
    }

    // Accepts or rejects a file with regards to the values of the fields
    // configured in this bean. The accept() method is the implementation
    // of FileFilter.accept(File);
    //
    /**
     * A file is accepted when all the criteria that have been set
     * are matched.
     * @param f The file to match against the configured criteria.
     * @return {@code true} if the file matches all criteria,
     * {@code false} otherwise.
     */
    public boolean accept(File f) {

        // Directories are accepted if they match against the directory pattern.
        //
        if (f.isDirectory()) {
            if (directoryPattern != null
                && !f.getName().matches(directoryPattern))
                return false;
            else return true;
        }

        // If we reach here, the f is not a directory.
        //
        // Files are accepted if they match all other conditions.

        // Check whether f matches filePattern
        if (filePattern != null
                && !f.getName().matches(filePattern))
            return false;

        // Check whether f exceeeds size limit
        if (sizeExceedsMaxBytes > 0 && f.length() <= sizeExceedsMaxBytes)
            return false;

        // Check whether f was last modified after lastModifiedAfter
        if (lastModifiedAfter != null &&
                lastModifiedAfter.after(new Date(f.lastModified())))
            return false;

        // Check whether f was last modified before lastModifiedBefore
        if (lastModifiedBefore != null &&
                lastModifiedBefore.before(new Date(f.lastModified())))
            return false;

        // All conditions were met: accept file.
        return true;
    }

    // used by equals()
    private Object[] toArray() {
        final Object[] thisconfig = {
            directoryPattern, filePattern, lastModifiedAfter,
            lastModifiedBefore, sizeExceedsMaxBytes
        };
        return thisconfig;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof FileMatch)) return false;
        final FileMatch other = (FileMatch)o;
        final Object[] thisconfig = toArray();
        final Object[] otherconfig = other.toArray();
        return Arrays.deepEquals(thisconfig,otherconfig);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(toArray());
    }

}
