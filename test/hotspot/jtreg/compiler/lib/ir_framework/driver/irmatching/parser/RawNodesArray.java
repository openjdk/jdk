package compiler.lib.ir_framework.driver.irmatching.parser;

import java.util.Arrays;
import java.util.ListIterator;

class RawNodesArray {
    private final ListIterator<String> iterator;
    private String currentNode;
    private int currentIndex;
    private int currentRegexIndex; // Node regexes start at 1

    RawNodesArray(String[] rawNodes) {
        this.iterator = Arrays.stream(rawNodes).toList().listIterator();
        this.currentRegexIndex = 0;
    }

    public boolean hasNodesLeft() {
        return iterator.hasNext();
    }

    public String getNextNode() {
        currentRegexIndex++;
        return next();
    }

    public String next() {
        currentIndex = iterator.nextIndex();
        currentNode = iterator.next();
        return currentNode;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getCurrentRegexIndex() {
        return currentRegexIndex;
    }

    public String getCurrentNode() {
        return currentNode;
    }
}
