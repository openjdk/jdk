/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.lib.template_framework;

/**
 * To facilitate recursive uses of Templates, for example where a template uses
 * itself and needs to be referenced before it is fully defined,
 * one can use the indirection of a {@link TemplateBinding}. The {@link TemplateBinding}
 * is allocated first without any Template bound to it yet. At this stage,
 * it can be used with {@link #get} inside a Template. Later, we can {@link #bind}
 * a Template to the binding, such that {@link #get} returns that bound
 * Template.
 *
 * @param <T> Type of the template.
 */
public class TemplateBinding<T extends Template> {
    private T template = null;

    /**
     * Creates a new {@link TemplateBinding} that has no Template bound to it yet.
     */
    public TemplateBinding() {}

    /**
     * Retrieve the Template that was previously bound to the binding.
     *
     * @return The Template that was previously bound with {@link #bind}.
     * @throws RendererException if no Template was bound yet.
     */
    public T get() {
        if (template == null) {
            throw new RendererException("Cannot 'get' before 'bind'.");
        }
        return template;
    }

    /**
     * Binds a Template for future reference using {@link #get}.
     *
     * @param template The Template to be bound.
     * @throws RendererException if a Template was already bound.
     */
    public void bind(T template) {
         if (this.template != null) {
            throw new RendererException("Duplicate 'bind' not allowed.");
        }
        this.template = template;
    }
}
