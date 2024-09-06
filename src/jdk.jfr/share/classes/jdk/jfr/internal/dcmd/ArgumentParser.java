/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.dcmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import jdk.jfr.internal.util.SpellChecker;
import jdk.jfr.internal.util.TimespanUnit;

final class ArgumentParser {
    private final Map<String, Object> options = new HashMap<>();
    private final Map<String, Object> extendedOptions = new HashMap<>();
    private final List<String> conflictedOptions = new ArrayList<>();
    private final StringBuilder builder = new StringBuilder();
    private final String text;
    private final char delimiter;
    private final String keyValueDelimiter;
    private final String valueDelimiter;
    private final Argument[] arguments;
    private int position;
    private int argumentIndex;

    ArgumentParser(Argument[] arguments, String text, char delimiter) {
        this.text = text;
        this.delimiter = delimiter;
        this.arguments = arguments;
        this.keyValueDelimiter = "=" + delimiter;
        this.valueDelimiter = Character.toString(delimiter);
    }

    public Map<String, Object> parse() {
        eatDelimiter();
        while (!atEnd()) {
            String key = readText(keyValueDelimiter);
            String value = null;
            if (accept('=')) {
                value = readText(valueDelimiter);
            } else {
                if (hasArgumentsLeft()) {
                    value = key;
                    key = nextArgument().name();
                }
            }
            if (!atEnd() && !accept(delimiter)) { // must be followed by delimiter
                throw new IllegalArgumentException("Expected delimiter, but found " + currentChar());
            }
            addOption(key, value);
            eatDelimiter();
        }
        checkConflict();
        checkMandatory();
        return options;
    }

    private boolean hasArgumentsLeft() {
        for (int index = argumentIndex; index < arguments.length; index++) {
            if (!arguments[index].option()) {
                return true;
            }
        }
        return false;
    }

    private Argument nextArgument() {
        while (argumentIndex < arguments.length) {
            Argument argument = arguments[argumentIndex++];
            if (!argument.option()) {
                return argument;
            }
        }
        return null;
    }

    protected void checkConflict() {
        if (conflictedOptions.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Option");

        // If multiple options conflict, the following blocks are executed
        if (conflictedOptions.size() > 1) {
            sb.append("s ");
            StringJoiner sj = new StringJoiner(", ");
            while (conflictedOptions.size() > 1) {
                sj.add(conflictedOptions.removeFirst());
            }
            sb.append(sj);
            sb.append(" and");
        }

        sb.append(" ");
        sb.append(conflictedOptions.removeFirst());
        sb.append(" can only be specified once.");
        throw new IllegalArgumentException(sb.toString());
    }

    public boolean checkMandatory() {
        for (Argument arg : arguments) {
            if (!options.containsKey(arg.name())) {
                if (arg.mandatory()) {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addOption(String key, String value) {
        boolean found = false;
        for (Argument arg : arguments) {
            if (arg.name().equals(key)) {
                found = true;
                Object v = value(key, arg.type(), value);
                if (arg.allowMultiple()) {
                    var list = (List<Object>) options.computeIfAbsent(key, x -> new ArrayList<>());
                    if (v instanceof List l) {
                        list.addAll(l);
                    } else {
                        list.add(v);
                    }
                } else {
                    if (options.containsKey(key)) {
                        if (!conflictedOptions.contains(key)) {
                            conflictedOptions.add(key);
                        }
                    } else {
                        options.put(key, v);
                    }
                }
            }
        }
        if (!found) {
            extendedOptions.put(key, value);
        }
    }

    private char currentChar() {
        return text.charAt(position);
    }

    private char lastChar() {
        return text.charAt(position -1);
    }

    private boolean atEnd() {
        return !(position < text.length());
    }

    private void eatDelimiter() {
        while (!atEnd() && currentChar() == delimiter) {
            position++;
        }
    }

    private boolean accept(char c) {
        if (!atEnd() && currentChar() == c) {
            position++;
            return true;
        }
        return false;
    }

    // Mostly copied from native DCmdParser
    private String readText(String abortChars) {
        builder.setLength(0);
        boolean quoted = false; ;
        while (position <= text.length() - 1 && abortChars.indexOf(currentChar()) == -1) {
          if (currentChar() == '\"' || currentChar() == '\'') {
            char quote =currentChar();
            quoted = true;
            while (position < text.length() - 1) {
              position++;
              if (currentChar() == quote && lastChar() != '\\') {
                break;
              }
              builder.append(currentChar());
            }
            if (currentChar() != quote) {
              throw new IllegalArgumentException("Format error in diagnostic command arguments");
            }
            break;
          }
          builder.append(currentChar());
          position++;
        }
        if (quoted) {
            position++;
        }
        return builder.toString();
    }

    private Object value(String name, String type, String text) {
        return switch (type) {
            case "JULONG" -> parseLong(name, text);
            case "STRING", "STRING SET" -> text == null ? "" : text;
            case "BOOLEAN" -> parseBoolean(name, text);
            case "NANOTIME" -> parseNanotime(name, text);
            case "MEMORY SIZE" -> parseMemorySize(name, text);
            default -> throw new InternalError("Unknown type: " + type);
        };
    }

    private Long parseLong(String name, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Parsing error long value: syntax error, value is null");
        }
        try {
            long value = Long.parseLong(text);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException nfe) {
          // fall through
        }
        String msg = "Integer parsing error in command argument '" + name + "'. Could not parse: " + text + ".";
        throw new IllegalArgumentException(msg);
    }

    private Boolean parseBoolean(String name, String text) {
        if ("true".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text)) {
            return Boolean.FALSE;
        }
        String msg = "Boolean parsing error in command argument '" + name + "'. Could not parse: " + text + ".";
        throw new IllegalArgumentException(msg);
    }

    private Object parseMemorySize(String name, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Parsing error memory size value: syntax error, value is null");
        }
        int index = indexOfUnit(text);
        String textValue = text.substring(0, index);
        String unit = text.substring(index);
        long bytes;
        try {
            bytes = Long.parseLong(textValue);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Parsing error memory size value: invalid value");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("Parsing error memory size value: negative values not allowed");
        }
        if (unit.isEmpty()) {
            return bytes;
        }
        return switch(unit.toLowerCase()) {
            case "k", "kb" -> bytes * 1024;
            case "m", "mb"-> bytes * 1024 * 1024;
            case "g", "gb" -> bytes * 1024 * 1024 * 1024;
            default -> throw new IllegalArgumentException("Parsing error memory size value: invalid value");
        };
    }

    private Object parseNanotime(String name, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Integer parsing error nanotime value: syntax error, value is null");
        }
        int index = indexOfUnit(text);
        String textValue = text.substring(0, index);
        String unit = text.substring(index);
        long time;
        try {
            time = Long.parseLong(textValue);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Integer parsing error nanotime value: syntax error");
        }
        if (unit.isEmpty()) {
            if (time == 0) {
                return Long.valueOf(0);
            }
            throw new IllegalArgumentException("Integer parsing error nanotime value: unit required");
        }
        TimespanUnit tu = TimespanUnit.fromText(unit);
        if (tu == null) {
            throw new IllegalArgumentException("Integer parsing error nanotime value: illegal unit");
        }
        return tu.toNanos(time);
    }

    int indexOfUnit(String text) {
        for (int i = 0; i< text.length(); i++) {
            char c = text.charAt(i);
            if (i == 0 && c == '-') { // Accept negative values.
                continue;
            }
            if (!Character.isDigit(c)) {
                return i;
            }
        }
        return text.length();
    }

    @SuppressWarnings("unchecked")
    <T> T getOption(String name) {
        return (T) options.get(name);
    }

    Map<String, Object> getOptions() {
        return options;
    }

    void checkUnknownArguments() {
        if (!extendedOptions.isEmpty()) {
            String name = extendedOptions.keySet().iterator().next();
            throw new IllegalArgumentException("Unknown argument '"  + name + "' in diagnostic command.");
        }
    }

    Map<String, Object> getExtendedOptions() {
        return extendedOptions;
    }

    boolean hasExtendedOptions() {
        return !extendedOptions.isEmpty();
    }

    void checkSpelling(Set<String> excludeSet) {
        List<String> alternatives = new ArrayList<>();
        for (Argument a : arguments) {
            alternatives.add(a.name());
        }
        for (String name : extendedOptions.keySet()) {
            if (!excludeSet.contains(name)) { // ignore names specified in .jfc
                String suggestion = SpellChecker.check(name, alternatives);
                if (suggestion != null) {
                    throw new IllegalArgumentException("Error! Did you mean '" + suggestion + "' instead of '" + name + "'?");
                }
            }
        }
    }
}