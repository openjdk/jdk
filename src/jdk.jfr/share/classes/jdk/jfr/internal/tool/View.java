/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.tool;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.internal.query.ViewPrinter;
import jdk.jfr.internal.query.Configuration;
import jdk.jfr.internal.query.Configuration.Truncate;
import jdk.jfr.internal.util.UserDataException;
import jdk.jfr.internal.util.UserSyntaxException;
import jdk.jfr.internal.util.Output.BufferedPrinter;

public final class View extends Command {
    @Override
    public String getName() {
        return "view";
    }

    @Override
    protected String getTitle() {
        return "Display event values in a recording file (.jfr) in predefined views";
    }

    @Override
    public String getDescription() {
        return "Display events in a tabular format. See 'jfr help view' for details.";
    }

    @Override
    public void displayOptionUsage(PrintStream stream) {
        stream.println("  --verbose               Displays the query that makes up the view");
        stream.println("");
        stream.println("  --width <integer>       The width of the view in characters. Default value depends on the view");
        stream.println("");
        stream.println("  --truncate <mode>       How to truncate content that exceeds space in a table cell.");
        stream.println("                          Mode can be 'beginning' or 'end'. Default value is 'end'");
        stream.println("");
        stream.println("  --cell-height <integer> Maximum number of rows in a table cell. Default value depends on the view");
        stream.println("");
        stream.println("  <view>                  Name of the view or event type to display. See list below for");
        stream.println("                          available views");
        stream.println("");
        stream.println("  <file>                  Location of the recording file (.jfr)");
        stream.println();
        for (String line : ViewPrinter.getAvailableViews()) {
            stream.println(line);
        }
        stream.println(" The <view> parameter can be an event type name. Use the 'jfr view types <file>'");
        stream.println(" to see a list. To display all views, use 'jfr view all-views <file>'. To display");
        stream.println(" all events, use 'jfr view all-events <file>'.");
        stream.println();
        stream.println("Example usage:");
        stream.println();
        stream.println(" jfr view gc recording.jfr");
        stream.println();
        stream.println(" jfr view --width 160 hot-methods recording.jfr");
        stream.println();
        stream.println(" jfr view --verbose allocation-by-class recording.jfr");
        stream.println();
        stream.println(" jfr view contention-by-site recording.jfr");
        stream.println();
        stream.println(" jfr view jdk.GarbageCollection recording.jfr");
        stream.println();
        stream.println(" jfr view --cell-height 10 ThreadStart recording.jfr");
        stream.println();
        stream.println(" jfr view --truncate beginning SystemProcess recording.jfr");
        stream.println();
    }

    @Override
    public List<String> getOptionSyntax() {
        List<String> list = new ArrayList<>();
        list.add("[--verbose]");
        list.add("[--width <integer>]");
        list.add("[--truncate <mode>]");
        list.add("[--cell-height <integer>]");
        list.add("<view>");
        list.add("<file>");
        return list;
    }

    @Override
    public void execute(Deque<String> options) throws UserSyntaxException, UserDataException {
        Path file = getJFRInputFile(options);
        int optionCount = options.size();
        if (optionCount < 1) {
            throw new UserSyntaxException("must specify a view or event type");
        }
        Configuration configuration = new Configuration();
        BufferedPrinter printer = new BufferedPrinter(System.out);
        configuration.output = printer;
        while (true) {
            if (acceptSwitch(options, "--verbose")) {
                configuration.verbose = true;
            }
            if (acceptOption(options, "--truncate")) {
                String mode = options.remove();
                try {
                    configuration.truncate = Truncate.valueOf(mode.toUpperCase());
                } catch (IllegalArgumentException iae) {
                    throw new UserSyntaxException("truncate must be 'beginning' or 'end'");
                }
            }
            if (acceptOption(options, "--cell-height")) {
                configuration.cellHeight = acceptInt(options, "cell-height");
            }
            if (acceptOption(options, "--width")) {
                configuration.width = acceptInt(options, "width");
            }
            if (options.size() == 1 && !options.peek().startsWith("-")) {
                String view = options.pop();
                try (EventStream stream = EventStream.openFile(file)) {
                    ViewPrinter vp = new ViewPrinter(configuration, stream);
                    vp.execute(view);
                    printer.flush();
                    return;
                } catch (IOException ioe) {
                    couldNotReadError(file, ioe);
                }
            }
            if (optionCount == options.size()) {
                String peek = options.peek();
                if (peek == null) {
                    throw new UserSyntaxException("must specify option <view>");
                }
                throw new UserSyntaxException("unknown option " + peek);
            }
            optionCount = options.size();
        }
    }
}
