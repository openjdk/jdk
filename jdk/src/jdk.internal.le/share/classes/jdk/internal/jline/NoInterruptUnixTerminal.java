/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

// Based on Apache Karaf impl

/**
 * Non-interruptible (via CTRL-C) {@link UnixTerminal}.
 *
 * @since 2.0
 */
public class NoInterruptUnixTerminal
    extends UnixTerminal
{
    public NoInterruptUnixTerminal() throws Exception {
        super();
    }

    @Override
    public void init() throws Exception {
        super.init();
        getSettings().set("intr undef");
    }

    @Override
    public void restore() throws Exception {
        getSettings().set("intr ^C");
        super.restore();
    }
}
