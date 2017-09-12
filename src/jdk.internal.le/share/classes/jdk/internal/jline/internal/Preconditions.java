/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

// Some bits lifted from Guava's ( http://code.google.com/p/guava-libraries/ ) Preconditions.

/**
 * Preconditions.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.7
 */
public class Preconditions
{
    public static <T> T checkNotNull(final T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }
}
