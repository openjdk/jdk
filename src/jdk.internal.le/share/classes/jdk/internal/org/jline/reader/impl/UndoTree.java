/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl;

import java.util.function.Consumer;

/**
 * Provides undo/redo functionality for the LineReader.
 * <p>
 * This class implements a simple undo tree that allows tracking and restoring
 * previous states of an object (typically the line buffer). It maintains a linear
 * history of states that can be navigated with undo and redo operations.
 * <p>
 * Key features:
 * <ul>
 *   <li>Tracks a sequence of states that can be undone and redone</li>
 *   <li>Uses a consumer to apply state changes when undoing or redoing</li>
 *   <li>Maintains the current position in the undo history</li>
 * </ul>
 * <p>
 * Note that the first added state (the initial state) cannot be undone.
 *
 * @param <T> the type of state object being tracked
 */
public class UndoTree<T> {

    private final Consumer<T> state;
    private final Node parent;
    private Node current;

    @SuppressWarnings("this-escape")
    public UndoTree(Consumer<T> s) {
        state = s;
        parent = new Node(null);
        parent.left = parent;
        clear();
    }

    public void clear() {
        current = parent;
    }

    public void newState(T state) {
        Node node = new Node(state);
        current.right = node;
        node.left = current;
        current = node;
    }

    public boolean canUndo() {
        return current.left != parent;
    }

    public boolean canRedo() {
        return current.right != null;
    }

    public void undo() {
        if (!canUndo()) {
            throw new IllegalStateException("Cannot undo.");
        }
        current = current.left;
        state.accept(current.state);
    }

    public void redo() {
        if (!canRedo()) {
            throw new IllegalStateException("Cannot redo.");
        }
        current = current.right;
        state.accept(current.state);
    }

    private class Node {
        private final T state;
        private Node left = null;
        private Node right = null;

        public Node(T s) {
            state = s;
        }
    }
}
