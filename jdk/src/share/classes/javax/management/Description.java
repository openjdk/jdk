/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ResourceBundle;

/**
 * <p>The textual description of an MBean or part of an MBean.  This
 * description is intended to be displayed to users to help them
 * understand what the MBean does.  Ultimately it will be the value of
 * the {@code getDescription()} method of an {@link MBeanInfo}, {@link
 * MBeanAttributeInfo}, or similar.</p>
 *
 * <p>This annotation applies to Standard MBean interfaces and to
 * MXBean interfaces, as well as to MBean classes defined using the
 * {@link MBean @MBean} or {@link MXBean @MXBean} annotations.  For
 * example, a Standard MBean might be defined like this:</p>
 *
 * <pre>
 * <b>{@code @Description}</b>("Application configuration")
 * public interface ConfigurationMBean {
 *     <b>{@code @Description}</b>("Cache size in bytes")
 *     public int getCacheSize();
 *     public void setCacheSize(int size);
 *
 *     <b>{@code @Description}</b>("Last time the configuration was changed, " +
 *                  "in milliseconds since 1 Jan 1970")
 *     public long getLastChangedTime();
 *
 *     <b>{@code @Description}</b>("Save the configuration to a file")
 *     public void save(
 *         <b>{@code @Description}</b>("Optional name of the file, or null for the default name")
 *         String fileName);
 * }
 * </pre>
 *
 * <p>The {@code MBeanInfo} for this MBean will have a {@link
 * MBeanInfo#getDescription() getDescription()} that is {@code
 * "Application configuration"}.  It will contain an {@code
 * MBeanAttributeInfo} for the {@code CacheSize} attribute that is
 * defined by the methods {@code getCacheSize} and {@code
 * setCacheSize}, and another {@code MBeanAttributeInfo} for {@code
 * LastChangedTime}.  The {@link MBeanAttributeInfo#getDescription()
 * getDescription()} for {@code CacheSize} will be {@code "Cache size
 * in bytes"}.  Notice that there is no need to add a
 * {@code @Description} to both {@code getCacheSize} and {@code
 * setCacheSize} - either alone will do.  But if you do add a
 * {@code @Description} to both, it must be the same.</p>
 *
 * <p>The {@code MBeanInfo} will also contain an {@link
 * MBeanOperationInfo} where {@link
 * MBeanOperationInfo#getDescription() getDescription()} is {@code
 * "Save the configuration to a file"}.  This {@code
 * MBeanOperationInfo} will contain an {@link MBeanParameterInfo}
 * where {@link MBeanParameterInfo#getDescription() getDescription()}
 * is {@code "Optional name of the file, or null for the default
 * name"}.</p>
 *
 * <p>The {@code @Description} annotation can also be applied to the
 * public constructors of the implementation class.  Continuing the
 * above example, the {@code Configuration} class implementing {@code
 * ConfigurationMBean} might look like this:</p>
 *
 * <pre>
 * public class Configuration implements ConfigurationMBean {
 *     <b>{@code @Description}</b>("A Configuration MBean with the default file name")
 *     public Configuration() {
 *         this(DEFAULT_FILE_NAME);
 *     }
 *
 *     <b>{@code @Description}</b>("A Configuration MBean with a specified file name")
 *     public Configuration(
 *         <b>{@code @Description}</b>("Name of the file the configuration is stored in")
 *         String fileName) {...}
 *     ...
 * }
 * </pre>
 *
 * <p>The {@code @Description} annotation also works in MBeans that
 * are defined using the {@code @MBean} or {@code @MXBean} annotation
 * on classes.  Here is an alternative implementation of {@code
 * Configuration} that does not use an {@code ConfigurationMBean}
 * interface.</p>
 *
 * <pre>
 * <b>{@code @MBean}</b>
 * <b>{@code @Description}</b>("Application configuration")
 * public class Configuration {
 *     <b>{@code @Description}</b>("A Configuration MBean with the default file name")
 *     public Configuration() {
 *         this(DEFAULT_FILE_NAME);
 *     }
 *
 *     <b>{@code @Description}</b>("A Configuration MBean with a specified file name")
 *     public Configuration(
 *         <b>{@code @Description}</b>("Name of the file the configuration is stored in")
 *         String fileName) {...}
 *
 *     <b>{@code @ManagedAttribute}</b>
 *     <b>{@code @Description}</b>("Cache size in bytes")
 *     public int getCacheSize() {...}
 *     <b>{@code @ManagedAttribute}</b>
 *     public void setCacheSize(int size) {...}
 *
 *     <b>{@code @ManagedOperation}</b>
 *     <b>{@code @Description}</b>("Last time the configuration was changed, " +
 *                  "in milliseconds since 1 Jan 1970")
 *     public long getLastChangedTime() {...}
 *
 *     <b>{@code @ManagedOperation}</b>
 *     <b>{@code @Description}</b>("Save the configuration to a file")
 *     public void save(
 *         <b>{@code @Description}</b>("Optional name of the file, or null for the default name")
 *         String fileName) {...}
 *     ...
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER,
         ElementType.TYPE})
public @interface Description {
    /**
     * <p>The description.</p>
     */
    String value();

    /**
     * <p>The base name for the {@link ResourceBundle} in which the key given in
     * the {@code descriptionResourceKey} field can be found, for example
     * {@code "com.example.myapp.MBeanResources"}.  If a non-default value
     * is supplied for this element, it will appear in the
     * <a href="Descriptor.html#descriptionResourceBundleBaseName"><!--
     * -->{@code Descriptor}</a> for the annotated item.</p>
     */
    @DescriptorKey(
        value = "descriptionResourceBundleBaseName", omitIfDefault = true)
    String bundleBaseName() default "";

    /**
     * <p>A resource key for the description of this element.  In
     * conjunction with the {@link #bundleBaseName bundleBaseName},
     * this can be used to find a localized version of the description.
     * If a non-default value
     * is supplied for this element, it will appear in the
     * <a href="Descriptor.html#descriptionResourceKey"><!--
     * -->{@code Descriptor}</a> for the annotated item.</p>
     */
    @DescriptorKey(value = "descriptionResourceKey", omitIfDefault = true)
    String key() default "";
}
