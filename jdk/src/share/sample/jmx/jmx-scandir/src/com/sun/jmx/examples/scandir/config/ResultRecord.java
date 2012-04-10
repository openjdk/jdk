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

import java.util.Date;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig.Action;
import java.io.File;
import java.util.Arrays;

/**
 * The <code>ResultRecord</code> Java Bean is used to write the
 * results of a directory scan to a result log.
 *
 * <p>
 * This class is annotated for XML binding.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
@XmlRootElement(name="ResultRecord",namespace=XmlConfigUtils.NAMESPACE)
public class ResultRecord {

    /**
     * The name of the file for which this result record is built.
     */
    private String filename;

    /**
     * The Date at which this result was obtained.
     */
    private Date date;

    /**
     * The short name of the directory scanner which performed the operation.
     * @see DirectoryScannerConfig#getName()
     */
    private String directoryScanner;

    /**
     * The list of actions that were successfully carried out.
     */
    private Action[] actions;

    /**
     * Creates a new empty instance of ResultRecord.
     */
    public ResultRecord() {
    }

    /**
     * Creates a new instance of ResultRecord.
     * @param scan The DirectoryScannerConfig for which this result was
     *        obtained.
     * @param actions The list of actions that were successfully carried out.
     * @param f The file for which these actions were successfully carried out.
     */
    public ResultRecord(DirectoryScannerConfig scan, Action[] actions,
                     File f) {
        directoryScanner = scan.getName();
        this.actions = actions;
        date = new Date();
        filename = f.getAbsolutePath();
    }

    /**
     * Gets the name of the file for which this result record is built.
     * @return The name of the file for which this result record is built.
     */
    @XmlElement(name="Filename",namespace=XmlConfigUtils.NAMESPACE)
    public String getFilename() {
        return this.filename;
    }

    /**
     * Sets the name of the file for which this result record is being built.
     * @param filename the name of the file for which this result record is
     *        being built.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Gets the Date at which this result was obtained.
     * @return the Date at which this result was obtained.
     */
    @XmlElement(name="Date",namespace=XmlConfigUtils.NAMESPACE)
    public Date getDate() {
        synchronized(this) {
            return (date==null)?null:(new Date(date.getTime()));
        }
    }

    /**
     * Sets the Date at which this result was obtained.
     * @param date the Date at which this result was obtained.
     */
    public void setDate(Date date) {
        synchronized (this) {
            this.date = (date==null)?null:(new Date(date.getTime()));
        }
    }

    /**
     * Gets the short name of the directory scanner which performed the
     * operation.
     * @see DirectoryScannerConfig#getName()
     * @return the short name of the directory scanner which performed the
     * operation.
     */
    @XmlElement(name="DirectoryScanner",namespace=XmlConfigUtils.NAMESPACE)
    public String getDirectoryScanner() {
        return this.directoryScanner;
    }

    /**
     * Sets the short name of the directory scanner which performed the
     * operation.
     * @see DirectoryScannerConfig#getName()
     * @param directoryScanner the short name of the directory scanner which
     * performed the operation.
     */
    public void setDirectoryScanner(String directoryScanner) {
        this.directoryScanner = directoryScanner;
    }

    /**
     * Gets the list of actions that were successfully carried out.
     * @return the list of actions that were successfully carried out.
     */
    @XmlElement(name="Actions",namespace=XmlConfigUtils.NAMESPACE)
    @XmlList
    public Action[] getActions() {
        return (actions == null)?null:actions.clone();
    }

    /**
     * Sets the list of actions that were successfully carried out.
     * @param actions the list of actions that were successfully carried out.
     */
    public void setActions(Action[] actions) {
        this.actions = (actions == null)?null:actions.clone();
    }

    // Used for equality
    private Object[] toArray() {
        final Object[] thisconfig = {
            filename, date, directoryScanner, actions
        };
        return thisconfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultRecord)) return false;
        return Arrays.deepEquals(toArray(),((ResultRecord)o).toArray());
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(toArray());
    }
}
