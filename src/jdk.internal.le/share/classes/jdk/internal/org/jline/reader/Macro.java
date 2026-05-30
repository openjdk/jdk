/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * A macro that executes a sequence of keystrokes when invoked.
 * <p>
 * The Macro class is a type of {@link Binding} that represents a sequence of keystrokes
 * to be executed when a key sequence bound to this macro is pressed. When triggered,
 * the LineReader will process each keystroke in the macro's sequence as if they were
 * typed by the user.
 * <p>
 * Macros are useful for automating repetitive sequences of editing operations by
 * binding them to a single key combination. They can include any valid key sequence,
 * including control characters and escape sequences.
 * <p>
 * For example, a macro might be used to:
 * <ul>
 *   <li>Move the cursor to the beginning of the line and insert a specific prefix</li>
 *   <li>Delete a word and replace it with another string</li>
 *   <li>Execute a series of editing commands in sequence</li>
 * </ul>
 *
 * @see Binding
 * @see LineReader#runMacro(String)
 */
public class Macro implements Binding {

    private final String sequence;

    public Macro(String sequence) {
        this.sequence = sequence;
    }

    /**
     * Returns the keystroke sequence that this macro will execute.
     *
     * @return the keystroke sequence
     */
    public String getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Macro macro = (Macro) o;
        return sequence.equals(macro.sequence);
    }

    @Override
    public int hashCode() {
        return sequence.hashCode();
    }

    @Override
    public String toString() {
        return "Macro[" + sequence + ']';
    }
}
