/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Representation of the input terminal for a platform.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.0
 */
public interface Terminal
{
    void init() throws Exception;

    void restore() throws Exception;

    void reset() throws Exception;

    boolean isSupported();

    int getWidth();

    int getHeight();

    boolean isAnsiSupported();

    /**
     * When ANSI is not natively handled, the output will have to be wrapped.
     */
    OutputStream wrapOutIfNeeded(OutputStream out);

    /**
     * When using native support, return the InputStream to use for reading characters
     * else return the input stream passed as a parameter.
     *
     * @since 2.6
     */
    InputStream wrapInIfNeeded(InputStream in) throws IOException;

    /**
     * For terminals that don't wrap when character is written in last column,
     * only when the next character is written.
     * These are the ones that have 'am' and 'xn' termcap attributes (xterm and
     * rxvt flavors falls under that category)
     */
    boolean hasWeirdWrap();

    boolean isEchoEnabled();

    void setEchoEnabled(boolean enabled);

    void disableInterruptCharacter();
    void enableInterruptCharacter();

    String getOutputEncoding();

}
