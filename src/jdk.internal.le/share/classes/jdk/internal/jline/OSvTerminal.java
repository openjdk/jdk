/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

import jdk.internal.jline.internal.Log;

/**
 * Terminal that is used for OSv. This is seperate to unix terminal
 * implementation because exec cannot be used as currently used by UnixTerminal.
 *
 * This implimentation is derrived from the implementation at
 * https://github.com/cloudius-systems/mgmt/blob/master/crash/src/main/java/com/cloudius/cli/OSvTerminal.java
 * authored by Or Cohen.
 *
 * @author <a href-"mailto:orc@fewbytes.com">Or Cohen</a>
 * @author <a href="mailto:arun.neelicattu@gmail.com">Arun Neelicattu</a>
 * @since 2.13
 */
public class OSvTerminal
    extends TerminalSupport
{

    public Class<?> sttyClass = null;
    public Object stty = null;

    @SuppressWarnings("deprecation")
    public OSvTerminal() {
        super(true);

        setAnsiSupported(true);

        try {
            if (stty == null) {
                sttyClass = Class.forName("com.cloudius.util.Stty");
                stty = sttyClass.newInstance();
            }
        } catch (Exception e) {
            Log.warn("Failed to load com.cloudius.util.Stty", e);
        }
    }

    @Override
    public void init() throws Exception {
        super.init();

        if (stty != null) {
            sttyClass.getMethod("jlineMode").invoke(stty);
        }
    }

    @Override
    public void restore() throws Exception {
        if (stty != null) {
            sttyClass.getMethod("reset").invoke(stty);
        }
        super.restore();

        // Newline in end of restore like in jline.UnixTerminal
        System.out.println();
    }

}
