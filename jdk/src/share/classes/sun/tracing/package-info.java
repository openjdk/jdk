/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/**
 * This package contains internal common code for implementing tracing
 * frameworks, and defined a number of existing frameworks.
 * <p>
 * There are four tracing frameworks currently defined.  The "Null" and
 * "Multiplex" frameworks are used internally as part of the implementation.
 * The "DTrace" framework is the prime consumer framework at the moment,
 * while the "PrintStream" framework is a functional, but hidden, framework
 * which can be used to track probe firings.  All but the "DTrace" framework
 * are defined in this package.  The "DTrace" framework is implemented in the
 * {@code sun.tracing.dtrace} package.
 * <p>
 * This package also contains the {@code ProviderSkeleton} class, which
 * holds most of the common code needed for implementing frameworks.
 * <p>
 * The "Null" framework is used when there are no other active frameworks.
 * It accomplishes absolutely nothing and is merely a placeholder so that
 * the application can call the tracing routines without error.
 * <p>
 * The "Multiplex" framework is used when there are multiple active frameworks.
 * It is initialized with the framework factories and create providers and
 * probes that dispatch to each active framework in turn.
 * <p>
 * The "PrintStream" framework is currently a debugging framework which
 * dispatches trace calls to a user-defined PrintStream class, defined by
 * a property.  It may some day be opened up to general use.
 * <p>
 * See the {@code sun.tracing.dtrace} and {@code com.sun.tracing.dtrace}
 * packages for information on the "DTrace" framework.
 */

package sun.tracing;
