/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

import sun.management.VMOptionCompositeData;
import javax.management.openmbean.CompositeData;

/**
 * Information about a VM option including its value and
 * where the value came from which is referred as its
 * {@link VMOption.Origin <i>origin</i>}.
 * <p>
 * Each VM option has a default value.  A VM option can
 * be set at VM creation time typically as a command line
 * argument to the launcher or an argument passed to the
 * VM created using the JNI invocation interface.
 * In addition, a VM option may be set via an environment
 * variable or a configuration file. A VM option can also
 * be set dynamically via a management interface after
 * the VM was started.
 *
 * A <tt>VMOption</tt> contains the value of a VM option
 * and the origin of that value at the time this <tt>VMOption</tt>
 * object was constructed.  The value of the VM option
 * may be changed after the <tt>VMOption</tt> object was constructed,
 *
 * @see <a href="{@docRoot}/../../../../technotes/guides/vm/index.html">
 *         Java Virtual Machine</a>
 * @author Mandy Chung
 * @since 1.6
 */
public class VMOption {
    private String name;
    private String value;
    private boolean writeable;
    private Origin origin;

    /**
     * Origin of the value of a VM option.  It tells where the
     * value of a VM option came from.
     *
     * @since 1.6
     */
    public enum Origin {
        /**
         * The VM option has not been set and its value
         * is the default value.
         */
        DEFAULT,
        /**
         * The VM option was set at VM creation time typically
         * as a command line argument to the launcher or
         * an argument passed to the VM created using the
         * JNI invocation interface.
         */
        VM_CREATION,
        /**
         * The VM option was set via an environment variable.
         */
        ENVIRON_VAR,
        /**
         * The VM option was set via a configuration file.
         */
        CONFIG_FILE,
        /**
         * The VM option was set via the management interface after the VM
         * was started.
         */
        MANAGEMENT,
        /**
         * The VM option was set via the VM ergonomic support.
         */
        ERGONOMIC,
        /**
         * The VM option was set via some other mechanism.
         */
        OTHER
    }

    /**
     * Constructs a <tt>VMOption</tt>.
     *
     * @param name Name of a VM option.
     * @param value Value of a VM option.
     * @param writeable <tt>true</tt> if a VM option can be set dynamically,
     *                  or <tt>false</tt> otherwise.
     * @param origin where the value of a VM option came from.
     *
     * @throws NullPointerException if the name or value is <tt>null</tt>
     */
    public VMOption(String name, String value, boolean writeable, Origin origin) {
        this.name = name;
        this.value = value;
        this.writeable = writeable;
        this.origin = origin;
    }

    /**
     * Constructs a <tt>VMOption</tt> object from a
     * {@link CompositeData CompositeData}.
     */
    private VMOption(CompositeData cd) {
        // validate the input composite data
        VMOptionCompositeData.validateCompositeData(cd);

        this.name = VMOptionCompositeData.getName(cd);
        this.value = VMOptionCompositeData.getValue(cd);
        this.writeable = VMOptionCompositeData.isWriteable(cd);
        this.origin = VMOptionCompositeData.getOrigin(cd);
    }

    /**
     * Returns the name of this VM option.
     *
     * @return the name of this VM option.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of this VM option at the time when
     * this <tt>VMOption</tt> was created. The value could have been changed.
     *
     * @return the value of the VM option at the time when
     *         this <tt>VMOption</tt> was created.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the origin of the value of this VM option. That is,
     * where the value of this VM option came from.
     *
     * @return where the value of this VM option came from.
     */
    public Origin getOrigin() {
        return origin;
    }

    /**
     * Tests if this VM option is writeable.  If this VM option is writeable,
     * it can be set by the {@link HotSpotDiagnosticMXBean#setVMOption
     * HotSpotDiagnosticMXBean.setVMOption} method.
     *
     * @return <tt>true</tt> if this VM option is writeable; <tt>false</tt>
     * otherwise.
     */
    public boolean isWriteable() {
        return writeable;
    }

    public String toString() {
        return "VM option: " + getName() +
               " value: " + value + " " +
               " origin: " + origin + " " +
               (writeable ? "(read-only)" : "(read-write)");
    }

    /**
     * Returns a <tt>VMOption</tt> object represented by the
     * given <tt>CompositeData</tt>. The given <tt>CompositeData</tt>
     * must contain the following attributes:
     * <p>
     * <blockquote>
     * <table border>
     * <tr>
     *   <th align=left>Attribute Name</th>
     *   <th align=left>Type</th>
     * </tr>
     * <tr>
     *   <td>name</td>
     *   <td><tt>java.lang.String</tt></td>
     * </tr>
     * <tr>
     *   <td>value</td>
     *   <td><tt>java.lang.String</tt></td>
     * </tr>
     * <tr>
     *   <td>origin</td>
     *   <td><tt>java.lang.String</tt></td>
     * </tr>
     * <tr>
     *   <td>writeable</td>
     *   <td><tt>java.lang.Boolean</tt></td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * @param cd <tt>CompositeData</tt> representing a <tt>VMOption</tt>
     *
     * @throws IllegalArgumentException if <tt>cd</tt> does not
     *   represent a <tt>VMOption</tt> with the attributes described
     *   above.
     *
     * @return a <tt>VMOption</tt> object represented by <tt>cd</tt>
     *         if <tt>cd</tt> is not <tt>null</tt>;
     *         <tt>null</tt> otherwise.
     */
    public static VMOption from(CompositeData cd) {
        if (cd == null) {
            return null;
        }

        if (cd instanceof VMOptionCompositeData) {
            return ((VMOptionCompositeData) cd).getVMOption();
        } else {
            return new VMOption(cd);
        }

    }


}
