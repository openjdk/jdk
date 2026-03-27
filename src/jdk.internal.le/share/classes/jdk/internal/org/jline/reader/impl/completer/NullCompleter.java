/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl.completer;

import java.util.List;

import jdk.internal.org.jline.reader.Candidate;
import jdk.internal.org.jline.reader.Completer;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.ParsedLine;

/**
 * Null completer.
 *
 * @since 2.3
 */
public final class NullCompleter implements Completer {
    public static final NullCompleter INSTANCE = new NullCompleter();

    /**
     * Creates a new NullCompleter.
     */
    public NullCompleter() {
        // Default constructor
    }

    public void complete(LineReader reader, final ParsedLine line, final List<Candidate> candidates) {}
}
