/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdk.internal.jline.internal.Configuration;
import jdk.internal.jline.internal.InfoCmp;
import jdk.internal.jline.internal.Log;
import jdk.internal.jline.internal.TerminalLineSettings;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Terminal that is used for unix platforms. Terminal initialization
 * is handled by issuing the <em>stty</em> command against the
 * <em>/dev/tty</em> file to disable character echoing and enable
 * character input. All known unix systems (including
 * Linux and Macintosh OS X) support the <em>stty</em>), so this
 * implementation should work for an reasonable POSIX system.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:dwkemp@gmail.com">Dale Kemp</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:jbonofre@apache.org">Jean-Baptiste Onofr\u00E9</a>
 * @since 2.0
 */
public class UnixTerminal
    extends TerminalSupport
    implements Terminal2
{
    private final TerminalLineSettings settings;
    private final String type;
    private String intr;
    private String lnext;
    private Set<String> bools = new HashSet<String>();
    private Map<String, Integer> ints = new HashMap<String, Integer>();
    private Map<String, String> strings = new HashMap<String, String>();

    public UnixTerminal() throws Exception {
        this(TerminalLineSettings.DEFAULT_TTY, null);
    }

    public UnixTerminal(String ttyDevice) throws Exception {
        this(ttyDevice, null);
    }

    public UnixTerminal(String ttyDevice, String type) throws Exception {
        super(true);
        checkNotNull(ttyDevice);
        this.settings = TerminalLineSettings.getSettings(ttyDevice);
        if (type == null) {
            type = System.getenv("TERM");
        }
        this.type = type;
        parseInfoCmp();
    }

    public TerminalLineSettings getSettings() {
        return settings;
    }

    /**
     * Remove line-buffered input by invoking "stty -icanon min 1"
     * against the current terminal.
     */
    @Override
    public void init() throws Exception {
        super.init();

        setAnsiSupported(true);

        // Set the console to be character-buffered instead of line-buffered.
        // Make sure we're distinguishing carriage return from newline.
        // Allow ctrl-s keypress to be used (as forward search)
        //
        // Please note that FreeBSD does not seem to support -icrnl and thus
        // has to be handled separately. Otherwise the console will be "stuck"
        // and will neither accept input nor print anything to stdout.
        if (Configuration.getOsName().contains(TerminalFactory.FREEBSD)) {
            settings.set("-icanon min 1 -inlcr -ixon");
        } else {
            settings.set("-icanon min 1 -icrnl -inlcr -ixon");
        }
        settings.undef("dsusp");

        setEchoEnabled(false);

        parseInfoCmp();
    }

    /**
     * Restore the original terminal configuration, which can be used when
     * shutting down the console reader. The ConsoleReader cannot be
     * used after calling this method.
     */
    @Override
    public void restore() throws Exception {
        settings.restore();
        super.restore();
    }

    /**
     * Returns the value of <tt>stty columns</tt> param.
     */
    @Override
    public int getWidth() {
        int w = settings.getProperty("columns");
        return w < 1 ? DEFAULT_WIDTH : w;
    }

    /**
     * Returns the value of <tt>stty rows>/tt> param.
     */
    @Override
    public int getHeight() {
        int h = settings.getProperty("rows");
        return h < 1 ? DEFAULT_HEIGHT : h;
    }

    @Override
    public boolean hasWeirdWrap() {
        return getBooleanCapability("auto_right_margin")
                && getBooleanCapability("eat_newline_glitch");
    }

    @Override
    public synchronized void setEchoEnabled(final boolean enabled) {
        try {
            if (enabled) {
                settings.set("echo");
            }
            else {
                settings.set("-echo");
            }
            super.setEchoEnabled(enabled);
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.error("Failed to ", enabled ? "enable" : "disable", " echo", e);
        }
    }

    public void disableInterruptCharacter()
    {
        try {
            intr = getSettings().getPropertyAsString("intr");
            if ("<undef>".equals(intr)) {
                intr = null;
            }
            settings.undef("intr");
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.error("Failed to disable interrupt character", e);
        }
    }

    public void enableInterruptCharacter()
    {
        try {
            if (intr != null) {
                settings.set("intr", intr);
            }
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.error("Failed to enable interrupt character", e);
        }
    }

    public void disableLitteralNextCharacter()
    {
        try {
            lnext = getSettings().getPropertyAsString("lnext");
            if ("<undef>".equals(lnext)) {
                lnext = null;
            }
            settings.undef("lnext");
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.error("Failed to disable litteral next character", e);
        }
    }

    public void enableLitteralNextCharacter()
    {
        try {
            if (lnext != null) {
                settings.set("lnext", lnext);
            }
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.error("Failed to enable litteral next character", e);
        }
    }

    public boolean getBooleanCapability(String capability) {
        return bools.contains(capability);
    }

    public Integer getNumericCapability(String capability) {
        return ints.get(capability);
    }

    public String getStringCapability(String capability) {
        return strings.get(capability);
    }

    private void parseInfoCmp() {
        String capabilities = null;
        if (type != null) {
            try {
                capabilities = InfoCmp.getInfoCmp(type);
            } catch (Exception e) {
            }
        }
        if (capabilities == null) {
            capabilities = InfoCmp.getAnsiCaps();
        }
        InfoCmp.parseInfoCmp(capabilities, bools, ints, strings);
    }
}
