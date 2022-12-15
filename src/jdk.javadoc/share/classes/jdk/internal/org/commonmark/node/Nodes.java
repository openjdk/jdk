package jdk.internal.org.commonmark.node;

import java.util.Iterator;

/**
 * Utility class for working with multiple {@link Node}s.
 *
 * @since 0.16.0
 */
public class Nodes {

    private Nodes() {
    }

    /**
     * The nodes between (not including) start and end.
     */
    public static Iterable<Node> between(Node start, Node end) {
        return new NodeIterable(start.getNext(), end);
    }

    private static class NodeIterable implements Iterable<Node> {

        private final Node first;
        private final Node end;

        private NodeIterable(Node first, Node end) {
            this.first = first;
            this.end = end;
        }

        @Override
        public Iterator<Node> iterator() {
            return new NodeIterator(first, end);
        }
    }

    private static class NodeIterator implements Iterator<Node> {

        private Node node;
        private final Node end;

        private NodeIterator(Node first, Node end) {
            node = first;
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return node != null && node != end;
        }

        @Override
        public Node next() {
            Node result = node;
            node = node.getNext();
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}

