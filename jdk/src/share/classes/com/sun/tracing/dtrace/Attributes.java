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
 * This annotation describes the interface's field attributes
 * for the probes in a provider.
 *
 * This annotation provides the contents of field-specific annotations
 * that specify the stability attributes and dependency class of a
 * particular field, for the probes in a provider.
 * <p>
 * The default interface attributes for unspecified fields is
 * Private/Private/Unknown.
 * <p>
 * @see <a href="http://docs.sun.com/app/docs/doc/817-6223/6mlkidlnp?a=view">Solaris Dynamic Tracing Guide, Chapter 39: Stability</a>
 * @since 1.7
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Attributes {
  /**
   * The stability level of the name.
   */
  StabilityLevel name() default StabilityLevel.PRIVATE;

  /**
   * The stability level of the data.
   */
  StabilityLevel data() default StabilityLevel.PRIVATE;

  /**
   * The interface attribute's dependency class.
   */
  DependencyClass dependency()  default DependencyClass.UNKNOWN;
}
