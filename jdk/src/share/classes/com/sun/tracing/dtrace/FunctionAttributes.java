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
 * This annotation describes the interface attributes of the
 * {@code function} field for a single provider.
 *
 * This annotation can be added to a user-defined {@code Provider} specification
 * interface to set the stability attributes of the {@code function} field for
 * all probes specified in that provider.
 * <p>
 * If this annotation is not present, the interface attributes for the
 * {@code function} field are Private/Private/Unknown.
 * <p>
 * @see <a href="http://docs.sun.com/app/docs/doc/817-6223/6mlkidlnp?a=view">Solaris Dynamic Tracing Guide, Chapter 39: Stability</a>
 * @since 1.7
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface FunctionAttributes {
    Attributes value();
}
