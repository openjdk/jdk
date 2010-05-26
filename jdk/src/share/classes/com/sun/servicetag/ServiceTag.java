/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.servicetag;

import java.util.Date;
import java.io.IOException;
import static com.sun.servicetag.RegistrationDocument.*;

/**
 * A service tag is an XML-based data structure that identifies a product or
 * a component on a system. The service tag schema is defined by the
 * Service Tags Technology. The location of the DTD file is platform dependent.
 * On Solaris, see <tt>/usr/share/lib/xml/dtd/servicetag.dtd</tt>.
 * <p>
 * A valid {@code ServiceTag} instance must comply to the service tag schema
 * and contain the following fields:
 * <ul>
 *   <li>{@link #getInstanceURN <tt>instance_urn</tt>}</li>
 *   <li>{@link #getProductName <tt>product_name</tt>}</li>
 *   <li>{@link #getProductVersion <tt>product_version</tt>}</li>
 *   <li>{@link #getProductURN <tt>product_urn</tt>}</li>
 *   <li>{@link #getProductParent <tt>product_parent</tt>}</li>
 *   <li>{@link #getProductParentURN <tt>product_parent_urn</tt>}</li>
 *   <li>{@link #getProductDefinedInstanceID <tt>product_defined_inst_id</tt>}</li>
 *   <li>{@link #getProductVendor <tt>product_vendor</tt>}</li>
 *   <li>{@link #getPlatformArch <tt>platform_arch</tt>}</li>
 *   <li>{@link #getContainer <tt>container</tt>}</li>
 *   <li>{@link #getSource <tt>source</tt>}</li>
 *   <li>{@link #getInstallerUID <tt>installer_uid</tt>}</li>
 *   <li>{@link #getTimestamp <tt>timestamp</tt>}</li>
 * </ul>
 *
 * The <tt>instance_urn</tt> can be specified when a {@code ServiceTag}
 * object is created, or it can be generated when it is added to
 * a {@link RegistrationData} object, or {@link Registry
 * system service tag registry}. The <tt>installer_uid</tt> and
 * <tt>timestamp</tt> are set when a {@code ServiceTag} object
 * is added to a {@link RegistrationData} object, or {@link Registry
 * system service tag registry}.
 *
 * @see <a href="https://sunconnection.sun.com/FAQ/sc_faq.html">Service Tags FAQ</a>
 */
public class ServiceTag {

    private String instanceURN;
    private String productName;
    private String productVersion;
    private String productURN;
    private String productParent;
    private String productParentURN;
    private String productDefinedInstanceID;
    private String productVendor;
    private String platformArch;
    private String container;
    private String source;
    private int installerUID;
    private Date timestamp;

    // Service Tag Field Lengths (defined in sthelper.h)
    // Since the constants defined in sthelper.h includes the null-terminated
    // character, so minus 1 from the sthelper.h defined values.
    private final int MAX_URN_LEN             = 256 - 1;
    private final int MAX_PRODUCT_NAME_LEN    = 256 - 1;
    private final int MAX_PRODUCT_VERSION_LEN = 64 - 1;
    private final int MAX_PRODUCT_PARENT_LEN  = 256 - 1;
    private final int MAX_PRODUCT_VENDOR_LEN  = 64 - 1;
    private final int MAX_PLATFORM_ARCH_LEN   = 64 - 1;
    private final int MAX_CONTAINER_LEN       = 64 - 1;
    private final int MAX_SOURCE_LEN          = 64 - 1;

    // private constructors
    private ServiceTag() {
    }
    // package private
    ServiceTag(String instanceURN,
               String productName,
               String productVersion,
               String productURN,
               String productParent,
               String productParentURN,
               String productDefinedInstanceID,
               String productVendor,
               String platformArch,
               String container,
               String source,
               int installerUID,
               Date timestamp) {
        setInstanceURN(instanceURN);
        setProductName(productName);
        setProductVersion(productVersion);
        setProductURN(productURN);
        setProductParentURN(productParentURN);
        setProductParent(productParent);
        setProductDefinedInstanceID(productDefinedInstanceID);
        setProductVendor(productVendor);
        setPlatformArch(platformArch);
        setContainer(container);
        setSource(source);
        setInstallerUID(installerUID);
        setTimestamp(timestamp);
    }

    /**
     * Creates a service tag object with no <tt>instance_urn</tt>.
     *
     * @param productName               the name of the product.
     * @param productVersion            the version of the product.
     * @param productURN                the uniform resource name of the product
     * @param productParent             the name of the product's parent.
     * @param productParentURN          the uniform resource name of the product's parent.
     * @param productDefinedInstanceID  the instance identifier.
     * @param productVendor             the vendor of the product.
     * @param platformArch              the operating system architecture.
     * @param container                 the container of the product.
     * @param source                    the source of the product.
     *
     * @throws IllegalArgumentException if any value of the input fields
     *    does not conform to the service tag XML schema.
     */
    public static ServiceTag newInstance(String productName,
                                         String productVersion,
                                         String productURN,
                                         String productParent,
                                         String productParentURN,
                                         String productDefinedInstanceID,
                                         String productVendor,
                                         String platformArch,
                                         String container,
                                         String source) {
          return new ServiceTag("", /* empty instance_urn */
                                productName,
                                productVersion,
                                productURN,
                                productParent,
                                productParentURN,
                                productDefinedInstanceID,
                                productVendor,
                                platformArch,
                                container,
                                source,
                                -1,
                                null);
    }

    /**
     * Creates a service tag object with a specified <tt>instance_urn</tt>.
     *
     * @param instanceURN               the uniform resource name of this instance.
     * @param productName               the name of the product.
     * @param productVersion            the version of the product.
     * @param productURN                the uniform resource name of the product
     * @param productParent             the name of the product's parent.
     * @param productParentURN          the uniform resource name of the product's parent.
     * @param productDefinedInstanceID  the instance identifier.
     * @param productVendor             the vendor of the product.
     * @param platformArch              the operating system architecture.
     * @param container                 the container of the product.
     * @param source                    the source of the product.
     *
     * @throws IllegalArgumentException if any value of the input fields
     *    does not conform to the service tag XML schema.
     */
    public static ServiceTag newInstance(String instanceURN,
                                         String productName,
                                         String productVersion,
                                         String productURN,
                                         String productParent,
                                         String productParentURN,
                                         String productDefinedInstanceID,
                                         String productVendor,
                                         String platformArch,
                                         String container,
                                         String source) {
          return new ServiceTag(instanceURN,
                                productName,
                                productVersion,
                                productURN,
                                productParent,
                                productParentURN,
                                productDefinedInstanceID,
                                productVendor,
                                platformArch,
                                container,
                                source,
                                -1,
                                null);
    }

    // Creates a copy of the ServiceTag instance
    // with instance_urn and timestamp initialized
    static ServiceTag newInstanceWithUrnTimestamp(ServiceTag st) {
        String instanceURN =
            (st.getInstanceURN().length() == 0 ? Util.generateURN() :
                                                 st.getInstanceURN());
        ServiceTag svcTag = new ServiceTag(instanceURN,
                                           st.getProductName(),
                                           st.getProductVersion(),
                                           st.getProductURN(),
                                           st.getProductParent(),
                                           st.getProductParentURN(),
                                           st.getProductDefinedInstanceID(),
                                           st.getProductVendor(),
                                           st.getPlatformArch(),
                                           st.getContainer(),
                                           st.getSource(),
                                           st.getInstallerUID(),
                                           new Date());
        return svcTag;
    }

    /**
     * Returns a uniform resource name (URN) in this format:
     * <blockquote>
     * "<tt>urn:st:<32-char {@link java.util.UUID uuid}></tt>"
     * </blockquote>
     * @return a URN.
     */
    public static String generateInstanceURN() {
        return Util.generateURN();
    }

    /**
     * Returns the uniform resource name of this service tag instance.
     *
     * @return  the <tt>instance_urn</tt> of this service tag.
     */
    public String getInstanceURN() {
        return instanceURN;
    }

    /**
     * Returns the name of the product.
     *
     * @return the product name.
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Returns the version of the product.
     *
     * @return the product version.
     */
    public String getProductVersion() {
        return productVersion;
    }

    /**
     * Returns the uniform resource name of the product.
     *
     * @return the product URN.
     */
    public String getProductURN() {
        return productURN;
    }

    /**
     * Returns the uniform resource name of the product's parent.
     *
     * @return the product's parent URN.
     */
    public String getProductParentURN() {
        return productParentURN;
    }

    /**
     * Returns the name of the product's parent.
     *
     * @return the product's parent name.
     */
    public String getProductParent() {
        return productParent;
    }

    /**
     * Returns the identifier defined for this product instance.
     *
     * @return  the identifier defined for this product instance.
     */
    public String getProductDefinedInstanceID() {
        return productDefinedInstanceID;
    }

    /**
     * Returns the vendor of the product.
     *
     * @return the product vendor.
     */
    public String getProductVendor() {
        return productVendor;
    }

    /**
     * Returns the platform architecture on which the product
     * is running on.
     *
     * @return the platform architecture on which the product is running on.
     */
    public String getPlatformArch() {
        return platformArch;
    }

    /**
     * Returns the timestamp.  This timestamp is set when this service tag
     * is added to or updated in a {@code RegistrationData} object or
     * the system service tag registry.
     * This method may return {@code null}.
     *
     * @return timestamp when this service tag
     * is added to or updated in a {@code RegistrationData} object or
     * the system service tag registry, or {@code null}.
     */
    public Date getTimestamp() {
        if (timestamp != null) {
            return (Date) timestamp.clone();
        } else {
            return null;
        }
    }


    /**
     * Returns the container of the product.
     *
     * @return the container of the product.
     */
    public String getContainer() {
        return container;
    }

    /**
     * Returns the source of this service tag.
     *
     * @return  source of this service tag.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the UID. The UID is set when this service tag
     * is added to or updated in the system service tag registry.
     * This is platform dependent whose default value is {@code -1}.
     * When this service tag is added to a {@code RegistrationData},
     * the UID is not set.
     *
     * @return the UID of whom this service tag
     * is added to or updated in the system service tag registry,
     * or {@code -1}.
     */
    public int getInstallerUID() {
        return installerUID;
    }

    // The following setter methods are used to validate the
    // input field when constructing a ServiceTag instance

    private void setInstanceURN(String instanceURN) {
        if (instanceURN == null) {
            throw new NullPointerException("Parameter instanceURN cannot be null");
        }
        if (instanceURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("instanceURN \"" + instanceURN +
                "\" exceeds maximum length " + MAX_URN_LEN);
        }
        this.instanceURN = instanceURN;
    }

    private void setProductName(String productName) {
        if (productName == null) {
            throw new NullPointerException("Parameter productName cannot be null");
        }
        if (productName.length() == 0) {
            throw new IllegalArgumentException("product name cannot be empty");
        }
        if (productName.length() > MAX_PRODUCT_NAME_LEN) {
            throw new IllegalArgumentException("productName \"" + productName +
                "\" exceeds maximum length " + MAX_PRODUCT_NAME_LEN);
        }
        this.productName = productName;
    }

    private void setProductVersion(String productVersion) {
        if (productVersion == null) {
            throw new NullPointerException("Parameter productVersion cannot be null");
        }

        if (productVersion.length() == 0) {
            throw new IllegalArgumentException("product version cannot be empty");
        }
        if (productVersion.length() > MAX_PRODUCT_VERSION_LEN) {
            throw new IllegalArgumentException("productVersion \"" +
                productVersion + "\" exceeds maximum length " +
                MAX_PRODUCT_VERSION_LEN);
        }
        this.productVersion = productVersion;
    }

    private void setProductURN(String productURN) {
        if (productURN == null) {
            throw new NullPointerException("Parameter productURN cannot be null");
        }
        if (productURN.length() == 0) {
            throw new IllegalArgumentException("product URN cannot be empty");
        }
        if (productURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productURN \"" + productURN +
                "\" exceeds maximum length " + MAX_URN_LEN);
        }
        this.productURN = productURN;
    }

    private void setProductParentURN(String productParentURN) {
        if (productParentURN == null) {
            throw new NullPointerException("Parameter productParentURN cannot be null");
        }
        // optional field - can be empty
        if (productParentURN.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productParentURN \"" +
                productParentURN + "\" exceeds maximum length " +
                MAX_URN_LEN);
        }
        this.productParentURN = productParentURN;
    }

    private void setProductParent(String productParent) {
        if (productParent == null) {
            throw new NullPointerException("Parameter productParent cannot be null");
        }
        if (productParent.length() == 0) {
            throw new IllegalArgumentException("product parent cannot be empty");
        }
        if (productParent.length() > MAX_PRODUCT_PARENT_LEN) {
            throw new IllegalArgumentException("productParent \"" +
                productParent + "\" exceeds maximum length " +
                MAX_PRODUCT_PARENT_LEN);
        }
        this.productParent = productParent;
    }

    void setProductDefinedInstanceID(String productDefinedInstanceID) {
        if (productDefinedInstanceID == null) {
            throw new NullPointerException("Parameter productDefinedInstanceID cannot be null");
        }
        if (productDefinedInstanceID.length() > MAX_URN_LEN) {
            throw new IllegalArgumentException("productDefinedInstanceID \"" +
                productDefinedInstanceID + "\" exceeds maximum length " +
                MAX_URN_LEN);
        }
        // optional field - can be empty
        this.productDefinedInstanceID = productDefinedInstanceID;
    }

    private void setProductVendor(String productVendor) {
        if (productVendor == null) {
            throw new NullPointerException("Parameter productVendor cannot be null");
        }
        if (productVendor.length() == 0) {
            throw new IllegalArgumentException("product vendor cannot be empty");
        }
        if (productVendor.length() > MAX_PRODUCT_VENDOR_LEN) {
            throw new IllegalArgumentException("productVendor \"" +
                productVendor + "\" exceeds maximum length " +
                MAX_PRODUCT_VENDOR_LEN);
        }
        this.productVendor = productVendor;
    }

    private void setPlatformArch(String platformArch) {
        if (platformArch == null) {
            throw new NullPointerException("Parameter platformArch cannot be null");
        }
        if (platformArch.length() == 0) {
            throw new IllegalArgumentException("platform architecture cannot be empty");
        }
        if (platformArch.length() > MAX_PLATFORM_ARCH_LEN) {
            throw new IllegalArgumentException("platformArch \"" +
                platformArch + "\" exceeds maximum length " +
                MAX_PLATFORM_ARCH_LEN);
        }
        this.platformArch = platformArch;
    }

    private void setTimestamp(Date timestamp) {
        // can be null
        this.timestamp = timestamp;
    }

    private void setContainer(String container) {
        if (container == null) {
            throw new NullPointerException("Parameter container cannot be null");
        }
        if (container.length() == 0) {
            throw new IllegalArgumentException("container cannot be empty");
        }
        if (container.length() > MAX_CONTAINER_LEN) {
            throw new IllegalArgumentException("container \"" +
                container + "\" exceeds maximum length " +
                MAX_CONTAINER_LEN);
        }
        this.container = container;
    }

    private void setSource(String source) {
        if (source == null) {
            throw new NullPointerException("Parameter source cannot be null");
        }
        if (source.length() == 0) {
            throw new IllegalArgumentException("source cannot be empty");
        }
        if (source.length() > MAX_SOURCE_LEN) {
            throw new IllegalArgumentException("source \"" + source +
                "\" exceeds maximum length " + MAX_SOURCE_LEN);
        }
        this.source = source;
    }

    private void setInstallerUID(int installerUID) {
        this.installerUID = installerUID;
    }

    /**
     * Compares this service tag to the specified object.
     * The result is {@code true} if and only if the argument is
     * not {@code null} and is a {@code ServiceTag} object whose
     * <tt>instance_urn</tt> is the same as the
     * <tt>instance_urn</tt> of this service tag.
     *
     * @return {@code true} if this service tag is the same as
     * the specified object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ServiceTag)) {
            return false;
        }
        ServiceTag st = (ServiceTag) obj;
        if (st == this) {
            return true;
        }
        return st.getInstanceURN().equals(getInstanceURN());
    }

    /**
     * Returns the hash code value for this service tag.
     * @return the hash code value for this service tag.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + (this.instanceURN != null ? this.instanceURN.hashCode() : 0);
        return hash;
    }

    /**
     * Returns the string representation of this service tag.
     * The format is implementation specific.
     *
     * @return the string representation of this service tag.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ST_NODE_INSTANCE_URN).append("=").append(instanceURN).append("\n");
        sb.append(ST_NODE_PRODUCT_NAME).append("=").append(productName).append("\n");
        sb.append(ST_NODE_PRODUCT_VERSION).append("=").append(productVersion).append("\n");
        sb.append(ST_NODE_PRODUCT_URN).append("=").append(productURN).append("\n");
        sb.append(ST_NODE_PRODUCT_PARENT_URN).append("=").append(productParentURN).append("\n");
        sb.append(ST_NODE_PRODUCT_PARENT).append("=").append(productParent).append("\n");
        sb.append(ST_NODE_PRODUCT_DEFINED_INST_ID).append("=").append(productDefinedInstanceID).append("\n");
        sb.append(ST_NODE_PRODUCT_VENDOR).append("=").append(productVendor).append("\n");
        sb.append(ST_NODE_PLATFORM_ARCH).append("=").append(platformArch).append("\n");
        sb.append(ST_NODE_TIMESTAMP).append("=").append(Util.formatTimestamp(timestamp)).append("\n");
        sb.append(ST_NODE_CONTAINER).append("=").append(container).append("\n");
        sb.append(ST_NODE_SOURCE).append("=").append(source).append("\n");
        sb.append(ST_NODE_INSTALLER_UID).append("=").append(String.valueOf(installerUID)).append("\n");
        return sb.toString();
    }


    /**
     * Returns the {@link ServiceTag} instance for the running Java
     * platform. The {@link ServiceTag#setSource source} field
     * of the {@code ServiceTag} will be set to the given {@code source}.
     * This method will return {@code null} if there is no service tag
     * for the running Java platform.
     * <p>
     * This method is designed for Sun software that bundles the JDK
     * or the JRE to use. It is recommended that the {@code source}
     * string contains information about the bundling software
     * such as the name and the version of the software bundle,
     * for example,
     * <blockquote>
     * <tt>NetBeans IDE 6.0 with JDK 6 Update 5 Bundle</tt>
     * </blockquote>
     * in a NetBeans/JDK bundle.
     * <p>
     * At the first time to call this method the application
     * is required to have the write permission to the installed
     * directory of this running JDK or JRE instance.
     *
     * @param source the source that bundles the JDK or the JRE.
     * @return a {@code ServiceTag} object for the Java platform,
     *         or {@code null} if not supported.
     * @throws IOException if an error occurs in this operation.
     */
    public static ServiceTag getJavaServiceTag(String source) throws IOException {
        return Installer.getJavaServiceTag(source);
    }

}
