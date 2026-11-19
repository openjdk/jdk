/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import sun.security.util.SecurityProperties;
import jdk.internal.misc.VM;

/**
 * Contains static utility methods which can filter exception
 * message strings for sensitive information.
 *
 * Code using this mechanism should use formatMsg()
 * to generate a formatted (enhanced or restricted) string for exception
 * messages.
 *
 * The methods above take variable numbers of SensitiveInfo objects
 * as parameters which contain the text that may have to be filtered.
 *
 * The SensitiveInfo objects should be generated with one of the following:
 *     public static SensitiveInfo filterSocketInfo(String s)
 *     public static SensitiveInfo filterNonSocketInfo(String s)
 *     public static SensitiveInfo filterJarName(String name)
 *     public static SensitiveInfo filterUserName(String name)
 */
public final class Exceptions {
    private Exceptions() {}

    private static volatile boolean enhancedSocketExceptionText;
    private static volatile boolean enhancedNonSocketExceptionText;
    private static volatile boolean enhancedUserExceptionText;
    private static volatile boolean enhancedJarExceptionText;
    private static volatile boolean initialized = false;

    /**
     * Base class for generating exception messages that may
     * contain sensitive information which in certain contexts
     * needs to be filtered out, in case it gets revealed in
     * unexpected places. Exception messages are either enhanced
     * or restricted. Enhanced messages include sensitive information.
     * Restricted messages don't.
     *
     * Sub-class for any new category that needs to be independently
     * controlled. Consider using a unique value for the
     * SecurityProperties.includedInExceptions(String value) mechanism
     * Current values defined are "jar", "userInfo"
     * "hostInfo", "hostInfoExclSocket".
     *
     * New code can also piggy back on existing categories
     *
     * A SensitiveInfo contains the following components
     * all of which default to empty strings.
     *
     * prefix, the sensitive info itself, a suffix
     * and a replacement string.
     *
     * The composeFilteredText(boolean enhance) method generates
     * an enhanced string when enhance is true.
     * This comprises (enhance == true)
     *     prefix + info + suffix
     * When (enhance == false), then by default the output is:
     *     "" empty string
     * However, if a replacement is set, then when enhance == false
     * the output is the replacement string.
     */
    public abstract static class SensitiveInfo {
        String info, suffix, prefix, replacement;
        boolean enhanced;

        SensitiveInfo(String info) {
            this.info = info;
            prefix = suffix = replacement = "";
        }
        public SensitiveInfo prefixWith(String prefix) {
            this.prefix = prefix;
            return this;
        }
        public SensitiveInfo suffixWith(String suffix) {
            this.suffix = suffix;
            return this;
        }
        public SensitiveInfo replaceWith(String replacement) {
            this.replacement = replacement;
            return this;
        }

        public boolean enhanced() {
            return enhanced;
        }

        /**
         * Implementation should call composeFilteredText(boolean flag)
         * where flag contains the boolean value of whether
         * the category is enabled or not.
         */
        public abstract String output();

        protected String composeFilteredText(boolean enhance) {
            if (enhance) {
                this.enhanced = true;
                return prefix + info + suffix;
            } else {
                return replacement;
            }
        }
    }

    static final class SocketInfo extends SensitiveInfo {
        public SocketInfo(String host) {
             super(host);
        }
        @Override
        public String output() {
            setup();
            return super.composeFilteredText(enhancedSocketExceptionText);
        }
    }

    static final class NonSocketInfo extends SensitiveInfo {
        public NonSocketInfo(String host) {
             super(host);
        }
        @Override
        public String output() {
            setup();
            return super.composeFilteredText(enhancedNonSocketExceptionText);
        }
    }

    static final class JarInfo extends SensitiveInfo {
        public JarInfo(String name) {
             super(name);
        }
        @Override
        public String output() {
            setup();
            return super.composeFilteredText(enhancedJarExceptionText);
        }
    }

    static final class UserInfo extends SensitiveInfo {
        public UserInfo(String host) {
             super(host);
        }
        @Override
        public String output() {
            setup();
            return super.composeFilteredText(enhancedUserExceptionText);
        }
    }

    // remove leading, trailing and duplicated space characters
    static String trim(String s) {
        int len = s.length();
        if (len == 0) return s;

        StringBuilder sb = new StringBuilder();

        // initial value deals with leading spaces
        boolean inSpace = true;
        for (int i=0; i<len; i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (inSpace)
                    continue;
                inSpace = true;
            } else
                inSpace = false;
            sb.append(c);
        }
        int sblen = sb.length();
        // last char could be a space
        if (sblen > 0 && sb.charAt(sblen - 1) == ' ')
            sb.deleteCharAt(sblen - 1);
        return sb.toString();
    }

    public static SensitiveInfo filterSocketInfo(String host) {
        return new SocketInfo(host);
    }

    public static SensitiveInfo filterNonSocketInfo(String host) {
        return new NonSocketInfo(host);
    }

    public static SensitiveInfo filterJarName(String name) {
        return new JarInfo(name);
    }

    public static SensitiveInfo filterUserName(String name) {
        return new UserInfo(name);
    }

    /**
     * Transform each SensitiveInfo into a String argument which is passed
     * to String.format(). This string is then trimmed.
     */
    public static String formatMsg(String format, SensitiveInfo... infos) {
        String[] args = new String[infos.length];

        int i = 0;

        for (SensitiveInfo info : infos) {
            args[i++] = info.output();
        }
        return trim(String.format(format, (Object[])args));
    }

    /**
     * Simplification of above. Equivalent to:
     *       formatMsg("%s", SensitiveInfo[1]); // ie with one arg
     */
    public static String formatMsg(SensitiveInfo info) {
        return trim(info.output());
    }

    public static void setup() {
        if (initialized || !VM.isBooted())
            return;
        enhancedSocketExceptionText = SecurityProperties.includedInExceptions("hostInfo");
        enhancedNonSocketExceptionText = SecurityProperties.includedInExceptions("hostInfoExclSocket")
                                      | enhancedSocketExceptionText;

        enhancedUserExceptionText = SecurityProperties.includedInExceptions("userInfo");
        enhancedJarExceptionText = SecurityProperties.INCLUDE_JAR_NAME_IN_EXCEPTIONS;
        initialized = true;
    }

    public static boolean enhancedNonSocketExceptions() {
        setup();
        return enhancedNonSocketExceptionText;
    }

    public static boolean enhancedSocketExceptions() {
        setup();
        return enhancedSocketExceptionText;
    }

    /**
     * The enhanced message text is the socket address appended to
     * the original IOException message
     */
    public static IOException ioException(IOException e, SocketAddress addr) {
        setup();
        if (!enhancedSocketExceptionText || addr == null) {
            return e;
        }
        if (addr instanceof UnixDomainSocketAddress) {
            return ofUnixDomain(e, (UnixDomainSocketAddress)addr);
        } else if (addr instanceof InetSocketAddress) {
            return ofInet(e, (InetSocketAddress)addr);
        } else {
            return e;
        }
    }

    private static IOException ofInet(IOException e, InetSocketAddress addr) {
        return create(e, String.join(": ", e.getMessage(), addr.toString()));
    }

    private static IOException ofUnixDomain(IOException e, UnixDomainSocketAddress addr) {
        String path = addr.getPath().toString();
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        sb.append(": ");
        sb.append(path);
        String enhancedMsg = sb.toString();
        return create(e, enhancedMsg);
    }

    // return a new instance of the same type with the given detail
    // msg, or if the type doesn't support detail msgs, return given
    // instance.
    private static <T extends Exception> T create(T e, String msg) {
        try {
            Class<? extends Exception> clazz = e.getClass();
            @SuppressWarnings("unchecked")
            Constructor<T> ctor = (Constructor<T>)clazz.getConstructor(String.class);
            T e1 = (ctor.newInstance(msg));
            e1.setStackTrace(e.getStackTrace());
            return e1;
        } catch (Exception e0) {
            // Some eg AsynchronousCloseException have no detail msg
            return e;
        }
    }
}
