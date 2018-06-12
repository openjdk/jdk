/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.completer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.internal.jline.internal.Ansi;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Completer for a set of strings.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public class AnsiStringsCompleter
    implements Completer
{
    private final SortedMap<String, String> strings = new TreeMap<String, String>();

    public AnsiStringsCompleter() {
        // empty
    }

    public AnsiStringsCompleter(final Collection<String> strings) {
        checkNotNull(strings);
        for (String str : strings) {
            this.strings.put(Ansi.stripAnsi(str), str);
        }
    }

    public AnsiStringsCompleter(final String... strings) {
        this(Arrays.asList(strings));
    }

    public Collection<String> getStrings() {
        return strings.values();
    }

    public int complete(String buffer, final int cursor, final List<CharSequence> candidates) {
        // buffer could be null
        checkNotNull(candidates);

        if (buffer == null) {
            candidates.addAll(strings.values());
        }
        else {
            buffer = Ansi.stripAnsi(buffer);
            for (Map.Entry<String, String> match : strings.tailMap(buffer).entrySet()) {
                if (!match.getKey().startsWith(buffer)) {
                    break;
                }

                candidates.add(match.getValue());
            }
        }

        return candidates.isEmpty() ? -1 : 0;
    }
}
