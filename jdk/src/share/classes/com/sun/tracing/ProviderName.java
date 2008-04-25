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
 * An annotation used to specify the name of a provider.
 * <p>
 * This annotation can be added to a user-defined {@code Provider}
 * interface, to set the name that will be used
 * for the provider in the generated probes.  Without this annotation,
 * the simple class name of the provider interface is used.
 * <p>
 * @since 1.7
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProviderName {
    String value();
}

