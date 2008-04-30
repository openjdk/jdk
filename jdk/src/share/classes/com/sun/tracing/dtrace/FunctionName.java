/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.tracing.dtrace;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

/**
 * An annotation used to specify the {@code function} field for a DTrace probe.
 *
 * This annotation can be added to a method in a user-defined Provider
 * specification interface to set the {@code function} field that is used
 * for the generated DTrace probe associated with that method.
 * <p>
 * @since 1.7
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FunctionName {
    String value();
}

