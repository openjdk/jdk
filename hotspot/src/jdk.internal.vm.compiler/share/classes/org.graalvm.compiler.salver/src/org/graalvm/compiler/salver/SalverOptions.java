/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.salver;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

public final class SalverOptions {

    //@formatter:off
    @Option(help = "Enable dumps via Salver trace events.", type = OptionType.Debug)
    public static final OptionValue<Boolean> Salver = new OptionValue<>(false);

    @Option(help = "Network address (Salver).", type = OptionType.Debug)
    public static final OptionValue<String> SalverAddress = new OptionValue<>("127.0.0.1");

    @Option(help = "Network port (Salver).", type = OptionType.Debug)
    public static final OptionValue<Integer> SalverPort = new OptionValue<>(2343);

    @Option(help = "Dump to files as opposed to sending them over the network (Salver).", type = OptionType.Debug)
    public static final OptionValue<Boolean> SalverToFile = new OptionValue<>(false);

    //@Option(help = "Use binary format for dumps (Salver).", type = OptionType.Debug)
    //public static final OptionValue<Boolean> SalverDumpBinary = new OptionValue<>(false);
    //@formatter:on
}
