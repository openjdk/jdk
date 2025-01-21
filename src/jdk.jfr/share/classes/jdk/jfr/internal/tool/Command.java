/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import jdk.jfr.internal.util.UserDataException;
import jdk.jfr.internal.util.UserSyntaxException;

abstract class Command {
    public static final String title = "Tool for working with Flight Recorder files";
    private static final Command HELP = new Help();
    private static final List<Command> COMMANDS = createCommands();

    private static List<Command> createCommands() {
        List<Command> commands = new ArrayList<>();
        commands.add(new Print());
        // Uncomment when developing new queries for the view command
        commands.add(new Query());
        commands.add(new View());
        commands.add(new Configure());
        commands.add(new Metadata());
        commands.add(new Scrub());
        commands.add(new Summary());
        commands.add(new Assemble());
        commands.add(new Disassemble());
        commands.add(new Version());
        commands.add(HELP);
        return Collections.unmodifiableList(commands);
    }

    static void displayHelp() {
        System.out.println(title);
        System.out.println();
        displayAvailableCommands(System.out);
    }

    public abstract String getName();

    public abstract String getDescription();

    public abstract void execute(Deque<String> argList) throws UserSyntaxException, UserDataException;

    protected String getTitle() {
        return getDescription();
    }

    static void displayAvailableCommands(PrintStream stream) {
        boolean first = true;
        for (Command c : Command.COMMANDS) {
            if (!first) {
                System.out.println();
            }
            displayCommand(stream, c);
            stream.println("     " + c.getDescription());
            first = false;
        }
    }

    protected static void displayCommand(PrintStream stream, Command c) {
        boolean firstSyntax = true;
        String alias = buildAlias(c);
        String initial = " jfr " + c.getName();
        for (String syntaxLine : c.getOptionSyntax()) {
            if (firstSyntax) {
                if (syntaxLine.length() != 0) {
                   stream.println(initial + " " + syntaxLine + alias);
                } else {
                   stream.println(initial + alias);
                }
            } else {
                for (int i = 0; i < initial.length(); i++) {
                    stream.print(" ");
                }
                stream.println(" " + syntaxLine);
            }
            firstSyntax = false;
        }
    }

    private static String buildAlias(Command c) {
        List<String> aliases = c.getAliases();
        if (aliases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (aliases.size() == 1) {
            sb.append(" (alias ");
            sb.append(aliases.getFirst());
            sb.append(")");
            return sb.toString();
         }
         sb.append(" (aliases ");
         for (int i = 0; i< aliases.size(); i ++ ) {
             sb.append(aliases.get(i));
             if (i < aliases.size() -1) {
                 sb.append(", ");
             }
         }
         sb.append(")");
         return sb.toString();
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

    public List<String> getOptionSyntax() {
        return Collections.singletonList("");
    }

    public void displayOptionUsage(PrintStream stream) {
    }

    protected boolean acceptSwitch(Deque<String> options, String expected) throws UserSyntaxException {
        if (!options.isEmpty() && options.peek().equals(expected)) {
            options.remove();
            return true;
        }
        return false;
    }

    protected boolean acceptOption(Deque<String> options, String expected) throws UserSyntaxException {
        if (expected.equals(options.peek())) {
            if (options.size() < 2) {
                throw new UserSyntaxException("missing value for " + options.peek());
            }
            options.remove();
            return true;
        }
        return false;
    }

    protected int acceptInt(Deque<String> options, String text) throws UserSyntaxException {
        if (options.size() < 1) {
            throw new UserSyntaxException("missing integer value");
        }
        String t = options.remove();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException nfe) {
            throw new UserSyntaxException("could not parse integer value " + t);
        }
    }

    protected void warnForWildcardExpansion(String option, String filter) throws UserDataException {
        // Users should quote their wildcards to avoid expansion by the shell
        try {
            if (!filter.contains(File.pathSeparator)) {
                Path p = Path.of(".", filter);
                if (!Files.exists(p)) {
                    return;
                }
            }
            throw new UserDataException("wildcards should be quoted, for example " + option + " \"Foo*\"");
        } catch (InvalidPathException ipe) {
            // ignore
        }
    }

    protected boolean acceptFilterOption(Deque<String> options, String expected) throws UserSyntaxException {
        if (!acceptOption(options, expected)) {
            return false;
        }
        if (options.isEmpty()) {
            throw new UserSyntaxException("missing filter after " + expected);
        }
        String filter = options.peek();
        if (filter.startsWith("--")) {
            throw new UserSyntaxException("missing filter after " + expected);
        }
        return true;
    }

    protected final void ensureMaxArgumentCount(Deque<String> options, int maxCount) throws UserSyntaxException {
        if (options.size() > maxCount) {
            throw new UserSyntaxException("too many arguments");
        }
    }

    protected final void ensureMinArgumentCount(Deque<String> options, int minCount) throws UserSyntaxException {
        if (options.size() < minCount) {
            throw new UserSyntaxException("too few arguments");
        }
    }

    protected final Path getDirectory(String pathText) throws UserDataException {
        try {
            Path path = Paths.get(pathText).toAbsolutePath();
            if (!Files.exists((path))) {
                throw new UserDataException("directory does not exist, " + pathText);
            }
            if (!Files.isDirectory(path)) {
                throw new UserDataException("path must be directory, " + pathText);
            }
            return path;
        } catch (InvalidPathException ipe) {
            throw new UserDataException("invalid path '" + pathText + "'");
        }
    }

    protected final Path getJFRInputFile(Deque<String> options) throws UserSyntaxException, UserDataException {
        if (options.isEmpty()) {
            throw new UserSyntaxException("missing file");
        }
        String file = options.removeLast();
        if (file.startsWith("--")) {
            throw new UserSyntaxException("missing file");
        }
        try {
            Path path = Paths.get(file).toAbsolutePath();
            ensureAccess(path);
            ensureFileExtension(path, ".jfr");
            return path;
        } catch (IOError ioe) {
            throw new UserDataException("i/o error reading file '" + file + "', " + ioe.getMessage());
        } catch (InvalidPathException ipe) {
            throw new UserDataException("invalid path '" + file + "'");
        }
    }

    protected final void ensureAccess(Path path) throws UserDataException {
        try (RandomAccessFile rad = new RandomAccessFile(path.toFile(), "r")) {
            if (rad.length() == 0) {
                throw new UserDataException("file is empty '" + path + "'");
            }
            rad.read(); // try to read 1 byte
        } catch (FileNotFoundException e) {
            throw new UserDataException("could not open file " + e.getMessage());
        } catch (IOException e) {
            throw new UserDataException("i/o error reading file '" + path + "', " + e.getMessage());
        }
    }

    protected final void couldNotReadError(Path p, IOException e) throws UserDataException {
        throw new UserDataException("could not read recording at " + p.toAbsolutePath() + ". " + e.getMessage());
    }

    protected final Path ensureFileDoesNotExist(Path file) throws UserDataException {
        if (Files.exists(file)) {
            throw new UserDataException("file '" + file + "' already exists");
        }
        return file;
    }

    protected final void ensureFileExtension(Path path, String extension) throws UserDataException {
        if (!path.toString().endsWith(extension)) {
            throw new UserDataException("filename must end with '" + extension + "'");
        }
    }

    protected void displayUsage(PrintStream stream) {
        displayCommand(stream, this);
        stream.println();
        displayOptionUsage(stream);
    }

    protected static char quoteCharacter() {
        return File.pathSeparatorChar == ';' ? '"' : '\'';
    }

    protected final void println() {
        System.out.println();
    }

    protected final void print(String text) {
        System.out.print(text);
    }

    protected final void println(String text) {
        System.out.println(text);
    }

    public static void checkCommonError(Deque<String> options, String typo, String correct) throws UserSyntaxException {
        if (typo.equals(options.peek())) {
            throw new UserSyntaxException("unknown option " + typo + ", did you mean " + correct + "?");
        }
    }

    protected final boolean matches(String command) {
        for (String s : getNames()) {
            if (s.equals(command)) {
                return true;
            }
        }
        return false;
    }

    protected List<String> getAliases() {
        return Collections.emptyList();
    }

    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        names.add(getName());
        names.addAll(getAliases());
        return names;
    }

}
