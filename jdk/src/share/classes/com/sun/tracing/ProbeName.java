/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.tracing;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

/**
 * An annotation used to override the name of a probe.
 * <p>
 * This annotation can be added to a method in a user-defined {@code Provider}
 * interface, to set the name that will be used for the generated probe
 * associated with that method.  Without this annotation, the name will be the
 * name of the method.
 * <p>
 * @since 1.7
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProbeName {
    String value();
}

