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

/**
 * <p>Indicates that a method in an MBean class defines an MBean operation.
 * This annotation can be applied to:</p>
 *
 * <ul>
 * <li>A public method of a public class
 * that is itself annotated with an {@link MBean @MBean} or
 * {@link MXBean @MXBean} annotation, or inherits such an annotation from
 * a superclass.</li>
 * <li>A method of an MBean or MXBean interface.
 * </ul>
 *
 * <p>Every method in an MBean or MXBean interface defines an MBean
 * operation even without this annotation, but the annotation allows
 * you to specify the impact of the operation:</p>
 *
 * <pre>
 * public interface ConfigurationMBean {
 *     {@code @ManagedOperation}(impact = {@link Impact#ACTION Impact.ACTION})
 *     public void save();
 *     ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ManagedOperation {
    /**
     * <p>The impact of this operation, as shown by
     * {@link MBeanOperationInfo#getImpact()}.
     */
    Impact impact() default Impact.UNKNOWN;
}
