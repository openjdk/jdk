/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javacserver.options;

/**
 * Sjavac options can be classified as:
 *
 *  (1) relevant only for sjavac, such as --server
 *  (2) relevant for sjavac and javac, such as -d, or
 *  (3) relevant only for javac, such as -g.
 *
 * This enum represents all options from (1) and (2). Note that instances of
 * this enum only entail static information about the option. For storage of
 * option values, refer to Options.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum Option {
    SERVER("--server:", "Specify server configuration file of running server") {
        @Override
        protected void processMatching(ArgumentIterator iter, Options.ArgDecoderOptionHelper helper) {
            helper.serverConf(iter.current().substring(arg.length()));
        }
    },
    STARTSERVER("--startserver:", "Start server and use the given configuration file") {
        @Override
        protected void processMatching(ArgumentIterator iter, Options.ArgDecoderOptionHelper helper) {
            helper.startServerConf(iter.current().substring(arg.length()));
        }
    };


    public final String arg;

    final String description;

    private Option(String arg, String description) {
        this.arg = arg;
        this.description = description;
    }

    // Future cleanup: Change the "=" syntax to ":" syntax to be consistent and
    // to follow the javac-option style.

    public boolean hasOption() {
        return arg.endsWith(":") || arg.endsWith("=");
    }


    /**
     * Process current argument of argIter.
     *
     * It's final, since the option customization is typically done in
     * processMatching.
     *
     * @param argIter Iterator to read current and succeeding arguments from.
     * @param helper The helper to report back to.
     * @return true iff the argument was processed by this option.
     */
    public final boolean processCurrent(ArgumentIterator argIter,
                                        Options.ArgDecoderOptionHelper helper) {
        String fullArg = argIter.current(); // "-tr" or "-log=level"
        if (hasOption() ? fullArg.startsWith(arg) : fullArg.equals(arg)) {
            processMatching(argIter, helper);
            return true;
        }
        // Did not match
        return false;
    }

    /** Called by process if the current argument matches this option. */
    protected abstract void processMatching(ArgumentIterator argIter,
                                            Options.ArgDecoderOptionHelper helper);
}
