/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.completer;

import java.util.List;

/**
 * Null completer.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public final class NullCompleter
    implements Completer
{
    public static final NullCompleter INSTANCE = new NullCompleter();

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        return -1;
    }
}
