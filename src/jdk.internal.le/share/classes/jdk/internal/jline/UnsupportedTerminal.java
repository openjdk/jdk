/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

/**
 * An unsupported terminal.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.0
 */
public class UnsupportedTerminal
    extends TerminalSupport
{
    public UnsupportedTerminal() {
        super(false);
        setAnsiSupported(false);
        setEchoEnabled(true);
    }
}
