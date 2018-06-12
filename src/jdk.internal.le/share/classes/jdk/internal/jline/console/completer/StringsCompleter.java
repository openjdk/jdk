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
import java.util.SortedSet;
import java.util.TreeSet;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Completer for a set of strings.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public class StringsCompleter
    implements Completer
{
    private final SortedSet<String> strings = new TreeSet<String>();

    public StringsCompleter() {
        // empty
    }

    public StringsCompleter(final Collection<String> strings) {
        checkNotNull(strings);
        getStrings().addAll(strings);
    }

    public StringsCompleter(final String... strings) {
        this(Arrays.asList(strings));
    }

    public Collection<String> getStrings() {
        return strings;
    }

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        // buffer could be null
        checkNotNull(candidates);

        if (buffer == null) {
            candidates.addAll(strings);
        }
        else {
            for (String match : strings.tailSet(buffer)) {
                if (!match.startsWith(buffer)) {
                    break;
                }

                candidates.add(match);
            }
        }

        return candidates.isEmpty() ? -1 : 0;
    }
}
