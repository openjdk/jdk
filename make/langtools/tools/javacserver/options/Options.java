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

import java.util.List;

/**
 * Instances of this class represent values for sjavac command line options.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Options {
    private String logLevel = "info";

    private boolean startServer = false;

    // Server configuration string
    private String serverConf;

    /** Get the log level. */
    public String getLogLevel() {
        return logLevel;
    }

    /** Return true iff a new server should be started */
    public boolean startServerFlag() {
        return startServer;
    }

    /** Return the server configuration string. */
    public String getServerConf() {
        return serverConf;
    }

    /**
     * Parses the given argument array and returns a corresponding Options
     * instance.
     */
    public static Options parseArgs(String... args) {
        Options options = new Options();
        options.new ArgDecoderOptionHelper().traverse(args);
        return options;
    }

    // OptionHelper that records the traversed options in this Options instance.
    public class ArgDecoderOptionHelper {
        public void reportError(String msg) {
            throw new IllegalArgumentException(msg);
        }

        public void serverConf(String conf) {
            if (serverConf != null)
                reportError("Can not specify more than one server configuration.");
            else
                serverConf = conf;
        }

        public void startServerConf(String conf) {
            if (serverConf != null)
                reportError("Can not specify more than one server configuration.");
            else {
                startServer = true;
                serverConf = conf;
            }
        }

        /**
         * Traverses an array of arguments and performs the appropriate callbacks.
         *
         * @param args the arguments to traverse.
         */
        void traverse(String[] args) {
            Iterable<String> allArgs;
            try {
                allArgs = CommandLine.parse(List.of(args)); // Detect @file and load it as a command line.
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Problem reading @"+e.getMessage());
            }
            ArgumentIterator argIter = new ArgumentIterator(allArgs);

            nextArg:
            while (argIter.hasNext()) {

                String arg = argIter.next();

                if (arg.startsWith("-")) {
                    for (Option opt : Option.values()) {
                        if (opt.processCurrent(argIter, this))
                            continue nextArg;
                    }
                }
            }
        }
    }
}
