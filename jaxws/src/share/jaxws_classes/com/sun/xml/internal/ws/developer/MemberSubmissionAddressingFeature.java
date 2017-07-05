/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.xml.internal.ws.api.FeatureConstructor;

import javax.xml.ws.WebServiceFeature;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;


/**
 * Addressing Feature representing MemberSubmission Version.
 *
 * @author Rama Pulavarthi
 */

@ManagedData
public class MemberSubmissionAddressingFeature extends WebServiceFeature {
    /**
     * Constant value identifying the MemberSubmissionAddressingFeature
     */
    public static final String ID = "http://java.sun.com/xml/ns/jaxws/2004/08/addressing";

    /**
     * Constant ID for the <code>required</code> feature parameter
     */
    public static final String IS_REQUIRED = "ADDRESSING_IS_REQUIRED";

    private boolean required;

    /**
     * Create an MemberSubmissionAddressingFeature
     * The instance created will be enabled.
     */
    public MemberSubmissionAddressingFeature() {
        this.enabled = true;
    }

    /**
     * Create an MemberSubmissionAddressingFeature
     *
     * @param enabled specifies whether this feature should
     *                be enabled or not.
     */
    public MemberSubmissionAddressingFeature(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Create an <code>MemberSubmissionAddressingFeature</code>
     *
     * @param enabled specifies whether this feature should
     * be enabled or not.
     * @param required specifies the value that will be used
     * for the <code>required</code> attribute on the
     * <code>wsaw:UsingAddressing</code> element.
     */
    public MemberSubmissionAddressingFeature(boolean enabled, boolean required) {
        this.enabled = enabled;
        this.required = required;
    }

    /**
     * Create an <code>MemberSubmissionAddressingFeature</code>
     *
     * @param enabled specifies whether this feature should
     * be enabled or not.
     * @param required specifies the value that will be used
     * for the <code>required</code> attribute on the
     * <code>wsaw:UsingAddressing</code> element.
     * @param validation specifies the value that will be used
     * for validation for the incoming messages. If LAX, messages are not strictly checked for conformance with  the spec.
     */
    @FeatureConstructor({"enabled","required","validation"})
    public MemberSubmissionAddressingFeature(boolean enabled, boolean required, MemberSubmissionAddressing.Validation validation) {
        this.enabled = enabled;
        this.required = required;
        this.validation = validation;
    }


    @ManagedAttribute
    public String getID() {
        return ID;
    }

    @ManagedAttribute
    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    private MemberSubmissionAddressing.Validation validation = MemberSubmissionAddressing.Validation.LAX;
    public void setValidation(MemberSubmissionAddressing.Validation validation) {
        this.validation = validation;

    }

    @ManagedAttribute
    public MemberSubmissionAddressing.Validation getValidation() {
        return validation;
    }
}
