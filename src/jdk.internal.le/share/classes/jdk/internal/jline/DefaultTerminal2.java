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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdk.internal.jline.internal.InfoCmp;

/**
 * Terminal wrapper with default ansi capabilities
 */
public class DefaultTerminal2 implements Terminal2 {

    private final Terminal terminal;
    private final Set<String> bools = new HashSet<String>();
    private final Map<String, String> strings = new HashMap<String, String>();

    public DefaultTerminal2(Terminal terminal) {
        this.terminal = terminal;
        registerCap("key_backspace", "^H");
        registerCap("bell", "^G");
        registerCap("carriage_return", "^M");
        if (true/*isSupported() && isAnsiSupported()*/) {
            registerCap("clr_eol", "\\E[K");
            registerCap("clr_bol", "\\E[1K");
            registerCap("cursor_up", "\\E[A");
            registerCap("cursor_down", "^J");
            registerCap("column_address", "\\E[%i%p1%dG");
            registerCap("clear_screen", "\\E[H\\E[2J");
            registerCap("parm_down_cursor", "\\E[%p1%dB");
            registerCap("cursor_left", "^H");
            registerCap("cursor_right", "\\E[C");
        }
        if (hasWeirdWrap()) {
            registerCap("eat_newline_glitch");
            registerCap("auto_right_margin");
        }
    }

    public void init() throws Exception {
        terminal.init();
    }

    public void restore() throws Exception {
        terminal.restore();
    }

    public void reset() throws Exception {
        terminal.reset();
    }

    public boolean isSupported() {
        return terminal.isSupported();
    }

    public int getWidth() {
        return terminal.getWidth();
    }

    public int getHeight() {
        return terminal.getHeight();
    }

    public boolean isAnsiSupported() {
        return terminal.isAnsiSupported();
    }

    public OutputStream wrapOutIfNeeded(OutputStream out) {
        return terminal.wrapOutIfNeeded(out);
    }

    public InputStream wrapInIfNeeded(InputStream in) throws IOException {
        return terminal.wrapInIfNeeded(in);
    }

    public boolean hasWeirdWrap() {
        return terminal.hasWeirdWrap();
    }

    public boolean isEchoEnabled() {
        return terminal.isEchoEnabled();
    }

    public void setEchoEnabled(boolean enabled) {
        terminal.setEchoEnabled(enabled);
    }

    public void disableInterruptCharacter() {
        terminal.disableInterruptCharacter();
    }

    public void enableInterruptCharacter() {
        terminal.enableInterruptCharacter();
    }

    public String getOutputEncoding() {
        return terminal.getOutputEncoding();
    }

    private void registerCap(String cap, String value) {
        for (String key : InfoCmp.getNames(cap)) {
            strings.put(key, value);
        }
    }

    private void registerCap(String cap) {
        Collections.addAll(bools, InfoCmp.getNames(cap));
    }

    public boolean getBooleanCapability(String capability) {
        return bools.contains(capability);
    }

    public Integer getNumericCapability(String capability) {
        return null;
    }

    public String getStringCapability(String capability) {
        return strings.get(capability);
    }

}
