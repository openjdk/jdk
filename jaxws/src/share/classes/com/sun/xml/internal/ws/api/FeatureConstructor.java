/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api;

import com.sun.xml.internal.ws.developer.MemberSubmissionAddressing;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;
import com.sun.xml.internal.ws.developer.Stateful;
import com.sun.xml.internal.ws.developer.StatefulFeature;

import javax.xml.ws.WebServiceFeature;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * <p>
 * This annotation should be used on a constructor of classes extending {@link WebServiceFeature} other than
 * Spec defined features, to help JAX-WS runtime recognize feature extensions.
 * </p>
 * <p>
 * For WebServiceFeature annotations to be recognizable by JAX-WS runtime, the feature annotation should point
 * to a corresponding bean (class extending WebServiceFeature). Only one of the constructors in the bean MUST be marked
 * with @FeatureConstructor whose value captures the annotaion attribute names for the corresponding parameters.
 * </p>
 * For example,
 * @see MemberSubmissionAddressingFeature
 * @see MemberSubmissionAddressing
 *
 * @see Stateful
 * @see StatefulFeature
 *
 * @author Rama Pulavarthi
 */
@Retention(RUNTIME)
@Target(ElementType.CONSTRUCTOR)

public @interface FeatureConstructor {
    /**
     * The name of the parameter.
     */
    String[] value() default {};
}
