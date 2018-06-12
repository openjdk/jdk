/*
 * Copyright (c) 2002-2016, the original author or authors.
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
    private String intr;

    public NoInterruptUnixTerminal() throws Exception {
        super();
    }

    @Override
    public void init() throws Exception {
        super.init();
        intr = getSettings().getPropertyAsString("intr");
        if ("<undef>".equals(intr)) {
            intr = null;
        }
        if (intr != null) {
            getSettings().undef("intr");
        }
    }

    @Override
    public void restore() throws Exception {
        if (intr != null) {
            getSettings().set("intr", intr);
        }
        super.restore();
    }
}
