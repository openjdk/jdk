/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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



package com.sun.org.glassfish.gmbal;

import java.lang.annotation.*;

/** This is taken directly from JDK 7 in order to support this feature in
 * JDK 5.
 *
 * <p>Meta-annotation that describes how an annotation element relates
 * to a field in a Descriptor.  This can be the Descriptor for
 * an MBean, or for an attribute, operation, or constructor in an
 * MBean, or for a parameter of an operation or constructor.</p>
 *
 * <p>(The DescriptorFields annotation
 * provides another way to add fields to a {@code Descriptor}.  See
 * the documentation for that annotation for a comparison of the
 * two possibilities.)</p>
 *
 * <p>Consider this annotation for example:</p>
 *
 * <pre>
 * &#64;Documented
 * &#64;Target(ElementType.METHOD)
 * &#64;Retention(RetentionPolicy.RUNTIME)
 * public &#64;interface Units {
 *     <b>&#64;DescriptorKey("units")</b>
 *     String value();
 * }
 * </pre>
 *
 * <p>and this use of the annotation:</p>
 *
 * <pre>
 * public interface CacheControlMBean {
 *     <b>&#64;Units("bytes")</b>
 *     public long getCacheSize();
 * }
 * </pre>
 *
 * <p>When a Standard MBean is made from the {@code CacheControlMBean},
 * the usual rules mean that it will have an attribute called
 * {@code CacheSize} of type {@code long}.  The {@code @Units}
 * annotation, given the above definition, will ensure that the
 * MBeanAttributeInfo for this attribute will have a
 * {@code Descriptor} that has a field called {@code units} with
 * corresponding value {@code bytes}.</p>
 *
 * <p>Similarly, if the annotation looks like this:</p>
 *
 * <pre>
 * &#64;Documented
 * &#64;Target(ElementType.METHOD)
 * &#64;Retention(RetentionPolicy.RUNTIME)
 * public &#64;interface Units {
 *     <b>&#64;DescriptorKey("units")</b>
 *     String value();
 *
 *     <b>&#64;DescriptorKey("descriptionResourceKey")</b>
 *     String resourceKey() default "";
 *
 *     <b>&#64;DescriptorKey("descriptionResourceBundleBaseName")</b>
 *     String resourceBundleBaseName() default "";
 * }
 * </pre>
 *
 * <p>and it is used like this:</p>
 *
 * <pre>
 * public interface CacheControlMBean {
 *     <b>&#64;Units("bytes",
 *            resourceKey="bytes.key",
 *            resourceBundleBaseName="com.example.foo.MBeanResources")</b>
 *     public long getCacheSize();
 * }
 * </pre>
 *
 * <p>then the resulting {@code Descriptor} will contain the following
 * fields:</p>
 *
 * <table border="2">
 * <tr><th>Name</th><th>Value</th></tr>
 * <tr><td>units</td><td>"bytes"</td></tr>
 * <tr><td>descriptionResourceKey</td><td>"bytes.key"</td></tr>
 * <tr><td>descriptionResourceBundleBaseName</td>
 *     <td>"com.example.foo.MBeanResources"</td></tr>
 * </table>
 *
 * <p>An annotation such as {@code @Units} can be applied to:</p>
 *
 * <ul>
 * <li>a Standard MBean or MXBean interface;
 * <li>a method in such an interface;
 * <li>a parameter of a method in a Standard MBean or MXBean interface
 * when that method is an operation (not a getter or setter for an attribute);
 * <li>a public constructor in the class that implements a Standard MBean
 * or MXBean;
 * <li>a parameter in such a constructor.
 * </ul>
 *
 * <p>Other uses of the annotation are ignored.</p>
 *
 * <p>Interface annotations are checked only on the exact interface
 * that defines the management interface of a Standard MBean or an
 * MXBean, not on its parent interfaces.  Method annotations are
 * checked only in the most specific interface in which the method
 * appears; in other words, if a child interface overrides a method
 * from a parent interface, only {@code @DescriptorKey} annotations in
 * the method in the child interface are considered.
 *
 * <p>The Descriptor fields contributed in this way by different
 * annotations on the same program element must be consistent with
 * each other and with any fields contributed by a
 * DescriptorFields annotation.  That is, two
 * different annotations, or two members of the same annotation, must
 * not define a different value for the same Descriptor field.  Fields
 * from annotations on a getter method must also be consistent with
 * fields from annotations on the corresponding setter method.</p>
 *
 * <p>The Descriptor resulting from these annotations will be merged
 * with any Descriptor fields provided by the implementation, such as
 * the <a href="Descriptor.html#immutableInfo">{@code
 * immutableInfo}</a> field for an MBean.  The fields from the annotations
 * must be consistent with these fields provided by the implementation.</p>
 *
 * <p>An annotation element to be converted into a descriptor field
 * can be of any type allowed by the Java language, except an annotation
 * or an array of annotations.  The value of the field is derived from
 * the value of the annotation element as follows:</p>
 *
 * <table border="2">
 * <tr><th>Annotation element</th><th>Descriptor field</th></tr>
 * <tr><td>Primitive value ({@code 5}, {@code false}, etc)</td>
 *     <td>Wrapped value ({@code Integer.valueOf(5)},
 *         {@code Boolean.FALSE}, etc)</td></tr>
 * <tr><td>Class constant (e.g. {@code Thread.class})</td>
 *     <td>Class name from Class.getName()
 *         (e.g. {@code "java.lang.Thread"})</td></tr>
 * <tr><td>Enum constant (e.g. ElementType.FIELD)</td>
 *     <td>Constant name from Enum.name()
 *         (e.g. {@code "FIELD"})</td></tr>
 * <tr><td>Array of class constants or enum constants</td>
 *     <td>String array derived by applying these rules to each
 *         element</td></tr>
 * <tr><td>Value of any other type<br>
 *         ({@code String}, {@code String[]}, {@code int[]}, etc)</td>
 *     <td>The same value</td></tr>
 * </table>
 *
 * @since 1.6
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface DescriptorKey {
    String value();

    /**
     * <p>Do not include this field in the Descriptor if the annotation
     * element has its default value.  For example, suppose {@code @Units} is
     * defined like this:</p>
     *
     * <pre>
     * &#64;Documented
     * &#64;Target(ElementType.METHOD)
     * &#64;Retention(RetentionPolicy.RUNTIME)
     * public &#64;interface Units {
     *     &#64;DescriptorKey("units")
     *     String value();
     *
     *     <b>&#64;DescriptorKey(value = "descriptionResourceKey",
     *                    omitIfDefault = true)</b>
     *     String resourceKey() default "";
     *
     *     <b>&#64;DescriptorKey(value = "descriptionResourceBundleBaseName",
     *                    omitIfDefault = true)</b>
     *     String resourceBundleBaseName() default "";
     * }
     * </pre>
     *
     * <p>Then consider a usage such as {@code @Units("bytes")} or
     * {@code @Units(value = "bytes", resourceKey = "")}, where the
     * {@code resourceKey} and {@code resourceBundleBaseNames} elements
     * have their default values.  In this case the Descriptor resulting
     * from these annotations will not include a {@code descriptionResourceKey}
     * or {@code descriptionResourceBundleBaseName} field.</p>
     */
    boolean omitIfDefault() default false;
}
