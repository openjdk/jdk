/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.cmd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

abstract class Command {

    private final static Command HELP = new HelpCommand();
    private final static List<Command> COMMANDS = createCommands();

    static void displayHelp() {
        System.out.println("Usage: java " + Execute.class.getName() + " <command> [<options>]");
        System.out.println();
        displayAvailableCommands();
    }

    static void displayAvailableCommands() {
        System.out.println("Available commands are:");
        System.out.println();
        boolean first = true;
        for (Command c : Command.COMMANDS) {
            if (!first) {
                System.out.println();
            }
            System.out.println("  " + c.getName() + " " + c.getOptionSyntax());
            System.out.println("    " + c.getDescription());
            first = false;
        }
    }

    public static List<Command> getCommands() {
        return COMMANDS;
    }

    public static Command valueOf(String commandName) {
        for (Command command : COMMANDS) {
            if (command.getName().equals(commandName)) {
                return command;
            }
        }
        return null;
    }

    abstract public String getOptionSyntax();

    abstract public String getName();

    abstract public String getDescription();

    abstract public void displayOptionUsage();

    abstract public void execute(Deque<String> options);

    final protected void userFailed(String message) {
        println();
        println(message);
        displayUsage();
        throw new IllegalArgumentException(message);
    }

    final protected void ensureMaxArgumentCount(Deque<String> options, int maxCount) {
        if (options.size() > maxCount) {
            userFailed("Too many arguments");
        }
    }

    final protected void ensureMinArgumentCount(Deque<String> options, int minCount) {
        if (options.size() < minCount) {
            userFailed("Too few arguments");
        }
    }

    final protected void ensureFileExist(Path file) {
        if (!Files.exists(file)) {
            userFailed("Could not find file " + file);
        }
    }

    final protected Path ensureFileDoesNotExist(Path file) {
        if (Files.exists(file)) {
            userFailed("File " + file + " already exists");
        }
        return file;
    }

    final protected void ensureJFRFile(Path path) {
        if (!path.toString().endsWith(".jfr")) {
            userFailed("Filename must end with .jfr");
        }
    }

    final protected void displayUsage() {
        String javaText = "java " + Execute.class.getName();
        println();
        println("Usage: " + javaText + " " + getName() + " " + getOptionSyntax());
        println();
        displayOptionUsage();
    }

    final protected void println() {
        System.out.println();
    }

    final protected void print(String text) {
        System.out.print(text);
    }

    final protected void println(String text) {
        System.out.println(text);
    }

    private static List<Command> createCommands() {
        List<Command> commands = new ArrayList<>();
        commands.add(new PrintCommand());
        commands.add(new SummaryCommand());
        commands.add(new ReconstructCommand());
        commands.add(new SplitCommand());
        commands.add(HELP);
        return Collections.unmodifiableList(commands);
    }
}
