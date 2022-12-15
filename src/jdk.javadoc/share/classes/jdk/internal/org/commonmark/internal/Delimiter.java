package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterRun;

import java.util.List;

/**
 * Delimiter (emphasis, strong emphasis or custom emphasis).
 */
public class Delimiter implements DelimiterRun {

    public final List<Text> characters;
    public final char delimiterChar;
    private final int originalLength;

    // Can open emphasis, see spec.
    private final boolean canOpen;

    // Can close emphasis, see spec.
    private final boolean canClose;

    public Delimiter previous;
    public Delimiter next;

    public Delimiter(List<Text> characters, char delimiterChar, boolean canOpen, boolean canClose, Delimiter previous) {
        this.characters = characters;
        this.delimiterChar = delimiterChar;
        this.canOpen = canOpen;
        this.canClose = canClose;
        this.previous = previous;
        this.originalLength = characters.size();
    }

    @Override
    public boolean canOpen() {
        return canOpen;
    }

    @Override
    public boolean canClose() {
        return canClose;
    }

    @Override
    public int length() {
        return characters.size();
    }

    @Override
    public int originalLength() {
        return originalLength;
    }

    @Override
    public Text getOpener() {
        return characters.get(characters.size() - 1);
    }

    @Override
    public Text getCloser() {
        return characters.get(0);
    }

    @Override
    public Iterable<Text> getOpeners(int length) {
        if (!(length >= 1 && length <= length())) {
            throw new IllegalArgumentException("length must be between 1 and " + length() + ", was " + length);
        }

        return characters.subList(characters.size() - length, characters.size());
    }

    @Override
    public Iterable<Text> getClosers(int length) {
        if (!(length >= 1 && length <= length())) {
            throw new IllegalArgumentException("length must be between 1 and " + length() + ", was " + length);
        }

        return characters.subList(0, length);
    }
}
