/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl.completer;

import java.util.*;

import jdk.internal.org.jline.reader.Candidate;
import jdk.internal.org.jline.reader.Completer;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.ParsedLine;
import jdk.internal.org.jline.utils.AttributedString;

/**
 * Completer which contains multiple completers and aggregates them together.
 *
 * @author <a href="mailto:matti.rintanikkola@gmail.com">Matti Rinta-Nikkola</a>
 */
public class SystemCompleter implements Completer {
    private Map<String,List<Completer>> completers = new HashMap<>();
    private Map<String,String> aliasCommand = new HashMap<>();
    private StringsCompleter commands;
    private boolean compiled = false;

    public SystemCompleter() {}

    @Override
    public void complete(LineReader reader, ParsedLine commandLine, List<Candidate> candidates) {
        if (!compiled) {
            throw new IllegalStateException();
        }
        assert commandLine != null;
        assert candidates != null;
        if (commandLine.words().size() > 0) {
            if (commandLine.words().size() == 1) {
                String buffer = commandLine.words().get(0);
                int eq = buffer.indexOf('=');
                if (eq < 0) {
                    commands.complete(reader, commandLine, candidates);
                } else if (reader.getParser().validVariableName(buffer.substring(0, eq))) {
                    String curBuf = buffer.substring(0, eq + 1);
                    for (String c: completers.keySet()) {
                        candidates.add(new Candidate(AttributedString.stripAnsi(curBuf+c)
                                    , c, null, null, null, null, true));
                    }
                }
            } else {
                String cmd = reader.getParser().getCommand(commandLine.words().get(0));
                if (command(cmd) != null) {
                    completers.get(command(cmd)).get(0).complete(reader, commandLine, candidates);
                }
            }
        }
    }

    public boolean isCompiled() {
        return compiled;
    }

    private String command(String cmd) {
        String out = null;
        if (cmd != null) {
            if (completers.containsKey(cmd)) {
                out = cmd;
            } else {
                out = aliasCommand.get(cmd);
            }
        }
        return out;
    }

    public void add(String command, List<Completer> completers) {
        for (Completer c : completers) {
            add(command, c);
        }
    }

    public void add(List<String> commands, Completer completer) {
        for (String c: commands) {
            add(c, completer);
        }
    }

    public void add(String command, Completer completer) {
        Objects.requireNonNull(command);
        if (compiled) {
            throw new IllegalStateException();
        }
        if (!completers.containsKey(command)) {
            completers.put(command, new ArrayList<Completer>());
        }
        if (completer instanceof ArgumentCompleter) {
            ((ArgumentCompleter) completer).setStrictCommand(false);
        }
        completers.get(command).add(completer);
    }

    public void add(SystemCompleter other) {
        if (other.isCompiled()) {
            throw new IllegalStateException();
        }
        for (Map.Entry<String, List<Completer>> entry: other.getCompleters().entrySet()) {
            for (Completer c: entry.getValue()) {
                add(entry.getKey(), c);
            }
        }
        addAliases(other.getAliases());
    }

    public void addAliases(Map<String,String> aliasCommand) {
        if (compiled) {
            throw new IllegalStateException();
        }
        this.aliasCommand.putAll(aliasCommand);
    }

    private Map<String,String> getAliases() {
        return aliasCommand;
    }

    public void compile() {
        if (compiled) {
            return;
        }
        Map<String, List<Completer>> compiledCompleters = new HashMap<>();
        for (Map.Entry<String, List<Completer>> entry: completers.entrySet()) {
            if (entry.getValue().size() == 1) {
                compiledCompleters.put(entry.getKey(), entry.getValue());
            } else {
                compiledCompleters.put(entry.getKey(), new ArrayList<Completer>());
                compiledCompleters.get(entry.getKey()).add(new AggregateCompleter(entry.getValue()));
            }
        }
        completers = compiledCompleters;
        Set<String> cmds = new HashSet<>(completers.keySet());
        cmds.addAll(aliasCommand.keySet());
        commands = new StringsCompleter(cmds);
        compiled = true;
    }

    public Map<String,List<Completer>> getCompleters() {
        return completers;
    }

}
