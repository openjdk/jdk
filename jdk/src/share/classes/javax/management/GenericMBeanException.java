/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.management;

import javax.management.openmbean.CompositeData;

/**
 * <p>A customizable exception that has an optional error code string and
 * payload.  By using this exception in an MBean, you can avoid requiring
 * clients of the MBean to have custom exception classes.</p>
 *
 * <p>An instance of this class has an optional {@linkplain #getErrorCode()
 * error code}, and an optional {@linkplain #getUserData() payload} known as
 * {@code userData}.  This allows you to distinguish between different
 * sorts of exception while still using this class for all of them.</p>
 *
 * <p>To produce a suitable {@code userData}, it is often simplest to use
 * the MXBean framework.  For example, suppose you want to convey a severity
 * and a subsystem with your exception, which are respectively an int and a
 * String.  You could define a class like this:</p>
 *
 * <pre>
 * public class ExceptionDetails {
 *     private final int severity;
 *     private final String subsystem;
 *
 *     {@link java.beans.ConstructorProperties &#64;ConstructorProperties}(<!--
 * -->{"severity", "subsystem"})
 *     public ExceptionDetails(int severity, String subsystem) {
 *         this.severity = severity;
 *         this.subsystem = subsystem;
 *     }
 *
 *     public int getSeverity() {
 *         return severity;
 *     }
 *
 *     public String getSubsystem() {
 *         return subsystem;
 *     }
 * }
 * </pre>
 *
 * <p>Then you can get the MXBean framework to transform {@code ExceptionDetails}
 * into {@link CompositeData} like this:</p>
 *
 * <pre>
 * static final <!--
 * -->{@link javax.management.openmbean.MXBeanMapping MXBeanMapping} <!--
 * -->exceptionDetailsMapping = <!--
 * -->{@link javax.management.openmbean.MXBeanMappingFactory#DEFAULT
 *     MXBeanMappingFactory.DEFAULT}.mappingForType(
 *         ExceptionDetails.class, MXBeanMappingFactory.DEFAULT);
 *
 * public static GenericMBeanException newGenericMBeanException(
 *         String message, String errorCode, int severity, String subsystem) {
 *     ExceptionDetails details = new ExceptionDetails(severity, subsystem);
 *     CompositeData userData = (CompositeData)
 *             exceptionDetailsMapping.toOpenValue(details);
 *     return new GenericMBeanException(
 *             message, errorCode, userData, (Throwable) null);
 * }
 *
 * ...
 *     throw newGenericMBeanException(message, errorCode, 25, "foosystem");
 * </pre>
 *
 * <p>A client that knows the {@code ExceptionDetails} class can convert
 * back from the {@code userData} of a {@code GenericMBeanException}
 * that was generated as above:</p>
 *
 * <pre>
 * ...
 *     try {
 *         mbeanProxy.foo();
 *     } catch (GenericMBeanException e) {
 *         CompositeData userData = e.getUserData();
 *         ExceptionDetails details = (ExceptionDetails)
 *                 exceptionDetailsMapping.fromOpenValue(userData);
 *         System.out.println("Exception Severity: " + details.getSeverity());
 *     }
 * ...
 * </pre>
 *
 * <p>The Descriptor field <a href="Descriptor.html#exceptionErrorCodes"><!--
 * -->exceptionErrorCodes</a> can be used to specify in the
 * {@link MBeanOperationInfo} for an operation what the possible
 * {@linkplain #getErrorCode() error codes} are when that operation throws
 * {@code GenericMBeanException}.  It can also be used in an {@link
 * MBeanConstructorInfo} or {@link MBeanAttributeInfo} to specify what the
 * possible error codes are for {@code GenericMBeanException} when invoking
 * that constructor or getting that attribute, respectively.  The field
 * <a href="Descriptor.html#setExceptionErrorCodes"><!--
 * -->setExceptionErrorCodes</a> can be used to specify what the possible
 * error codes are when setting an attribute.</p>
 *
 * <p>You may want to use the {@link DescriptorKey &#64;DescriptorKey} facility
 * to define annotations that allow you to specify the error codes.  If you
 * define...</p>
 *
 * <pre>
 * {@link java.lang.annotation.Documented &#64;Documented}
 * {@link java.lang.annotation.Target &#64;Target}(ElementType.METHOD)
 * {@link java.lang.annotation.Retention &#64;Retention}(RetentionPolicy.RUNTIME)
 * public &#64;interface ErrorCodes {
 *     &#64;DescriptorKey("exceptionErrorCodes")
 *     String[] value();
 * }
 * </pre>
 *
 * <p>...then you can write MBean interfaces like this...</p>
 *
 * <pre>
 * public interface FooMBean {  // or FooMXBean
 *     &#64;ErrorCodes({"com.example.bad", "com.example.worse"})
 *     public void foo() throws GenericMBeanException;
 * }
 * </pre>
 *
 * <p>The Descriptor field <a href="Descriptor.html#exceptionUserDataTypes"><!--
 * -->exceptionUserDataTypes</a> can be used to specify in the
 * {@link MBeanOperationInfo} for an operation what the possible types of
 * {@linkplain #getUserData() userData} are when that operation throws
 * {@code GenericMBeanException}.  It is an array of
 * {@link javax.management.openmbean.CompositeType CompositeType} values
 * describing the possible {@link CompositeData} formats.  This field can also be used
 * in an {@link MBeanConstructorInfo} or {@link MBeanAttributeInfo} to specify
 * the possible types of user data for {@code GenericMBeanException} when
 * invoking that constructor or getting that attribute, respectively.  The
 * field
 * <a href="Descriptor.html#setExceptionUserDataTypes">setExceptionUserDataTypes</a>
 * can be used to specify the possible types of user data for exceptions when
 * setting an attribute.  If a Descriptor has both {@code exceptionErrorCodes}
 * and {@code exceptionUserDataTypes} then the two arrays should be the
 * same size; each pair of corresponding elements describes one kind
 * of exception.  Similarly for {@code setExceptionErrorCodes} and {@code
 * setExceptionUserDataTypes}.
 *
 *
 * <h4>Serialization</h4>
 *
 * <p>For compatibility reasons, instances of this class are serialized as
 * instances of {@link MBeanException}.  Special logic in that class converts
 * them back to instances of this class at deserialization time.  If the
 * serialized object is deserialized in an earlier version of the JMX API
 * that does not include this class, then it will appear as just an {@code
 * MBeanException} and the error code or userData will not be available.</p>
 *
 * @since 1.7
 */
public class GenericMBeanException extends MBeanException {
    private static final long serialVersionUID = -1560202003985932823L;

    /**
     * <p>Constructs a new {@code GenericMBeanException} with the given
     * detail message.  This constructor is
     * equivalent to {@link #GenericMBeanException(String, String,
     * CompositeData, Throwable) GenericMBeanException(message, "",
     * null, null)}.</p>
     *
     * @param message the exception detail message.
     */
    public GenericMBeanException(String message) {
        this(message, "", null, null);
    }

    /**
     * <p>Constructs a new {@code GenericMBeanException} with the given
     * detail message and cause.  This constructor is
     * equivalent to {@link #GenericMBeanException(String, String,
     * CompositeData, Throwable) GenericMBeanException(message, "",
     * null, cause)}.</p>
     *
     * @param message the exception detail message.
     * @param cause the cause of this exception.  Can be null.
     */
    public GenericMBeanException(String message, Throwable cause) {
        this(message, "", null, cause);
    }

    /**
     * <p>Constructs a new {@code GenericMBeanException} with the given
     * detail message, error code, and user data.  This constructor is
     * equivalent to {@link #GenericMBeanException(String, String,
     * CompositeData, Throwable) GenericMBeanException(message, errorCode,
     * userData, null)}.</p>
     *
     * @param message the exception detail message.
     * @param errorCode the exception error code.  Specifying a null value
     * is equivalent to specifying an empty string.  It is recommended to use
     * the same reverse domain name convention as package names, for example
     * "com.example.foo.UnexpectedFailure".  There is no requirement that the
     * error code be a syntactically valid Java identifier.
     * @param userData extra information about the exception.  Can be null.
     */
    public GenericMBeanException(
            String message, String errorCode, CompositeData userData) {
        this(message, errorCode, userData, null);
    }

    /**
     * <p>Constructs a new {@code GenericMBeanException} with the given
     * detail message, error code, user data, and cause.</p>
     *
     * @param message the exception detail message.
     * @param errorCode the exception error code.  Specifying a null value
     * is equivalent to specifying an empty string.  It is recommended to use
     * the same reverse domain name convention as package names, for example
     * "com.example.foo.UnexpectedFailure".  There is no requirement that the
     * error code be a syntactically valid Java identifier.
     * @param userData extra information about the exception.  Can be null.
     * @param cause the cause of this exception.  Can be null.
     */
    public GenericMBeanException(
            String message, String errorCode, CompositeData userData,
            Throwable cause) {
        super(message, (errorCode == null) ? "" : errorCode, userData, cause);
    }

    /**
     * <p>Returns the error code of this exception.</p>
     *
     * @return the error code.  This value is never null.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * <p>Returns the userData of this exception.</p>
     *
     * @return the userData.  Can be null.
     */
    public CompositeData getUserData() {
        return userData;
    }

    /**
     * <p>Instances of this class are serialized as instances of
     * {@link MBeanException}.  {@code MBeanException} has private fields that can
     * not be set by its public constructors.  They can only be set in objects
     * returned by this method.  When an {@code MBeanException} instance is
     * deserialized, if those fields are present then its {@code readResolve}
     * method will substitute a {@code GenericMBeanException} equivalent to
     * this one.</p>
     */
    Object writeReplace() {
        MBeanException x = new MBeanException(
                getMessage(), errorCode, userData, getCause());
        x.setStackTrace(this.getStackTrace());
        return x;
    }
}
