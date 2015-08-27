/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.tools.jjs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.internal.runtime.JSType;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

/*
 * "edit" top level script function which shows an external Window
 * for editing and evaluating scripts from it.
 */
final class EditObject extends AbstractJSObject {
    private static final Set<String> props;
    static {
        final HashSet<String> s = new HashSet<>();
        s.add("editor");
        props = Collections.unmodifiableSet(s);
    }

    private final Console console;
    private final Consumer<String> errorHandler;
    private final Consumer<String> evaluator;
    private String editor;

    EditObject(final Console console, final Consumer<String> errorHandler,
            final Consumer<String> evaluator) {
        this.console = console;
        this.errorHandler = errorHandler;
        this.evaluator = evaluator;
    }

    @Override
    public Object getDefaultValue(final Class<?> hint) {
        if (hint == String.class) {
            return toString();
        }
        return UNDEFINED;
    }

    @Override
    public String toString() {
        return "function edit() { [native code] }";
    }

    @Override
    public Set<String> keySet() {
        return props;
    }

    @Override
    public Object getMember(final String name) {
        if (name.equals("editor")) {
            return editor;
        }
        return UNDEFINED;
    }

    @Override
    public void setMember(final String name, final Object value) {
        if (name.equals("editor")) {
            this.editor = value != null && value != UNDEFINED? JSType.toString(value) : "";
        }
    }

    // called whenever user 'saves' script in editor
    class SaveHandler implements Consumer<String> {
         private String lastStr; // last seen code

         SaveHandler(final String str) {
             this.lastStr = str;
         }

         @Override
         public void accept(final String str) {
             // ignore repeated save of the same code!
             if (! str.equals(lastStr)) {
                 this.lastStr = str;
                 // evaluate the new code
                 evaluator.accept(str);
             }
         }
    }

    @Override
    public Object call(final Object thiz, final Object... args) {
        final String initText = args.length > 0? JSType.toString(args[0]) : "";
        final SaveHandler saveHandler = new SaveHandler(initText);
        if (editor != null && !editor.isEmpty()) {
            ExternalEditor.edit(editor, errorHandler, initText, saveHandler, console);
        } else if (! Main.HEADLESS) {
            EditPad.edit(errorHandler, initText, saveHandler);
        } else {
            errorHandler.accept(Main.getMessage("no.editor"));
        }
        return UNDEFINED;
    }

    @Override
    public boolean isFunction() {
        return true;
    }
}
