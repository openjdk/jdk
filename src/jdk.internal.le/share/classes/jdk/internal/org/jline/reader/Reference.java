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
 * A reference to a {@link Widget} by name.
 * <p>
 * The Reference class is a type of {@link Binding} that refers to a widget by its name
 * rather than directly holding the widget implementation. When a key sequence bound to
 * a Reference is pressed, the LineReader will look up the referenced widget by name
 * and execute it.
 * <p>
 * This indirection allows for more flexible key bindings, as it enables binding keys
 * to widgets that might be defined or redefined after the key binding is established.
 * It also allows multiple key sequences to reference the same widget without duplicating
 * the widget implementation.
 * <p>
 * References are particularly useful in configuration files where widgets are referred
 * to by name rather than by direct object references.
 *
 * @see Widget
 * @see Binding
 * @see LineReader#callWidget(String)
 */
public class Reference implements Binding {

    private final String name;

    public Reference(String name) {
        this.name = name;
    }

    /**
     * Returns the name of the referenced widget.
     *
     * @return the widget name
     */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reference func = (Reference) o;
        return name.equals(func.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Reference[" + name + ']';
    }
}
