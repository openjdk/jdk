/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4992178
 * @summary REGRESSION: Allow unlimited number of edits in an UndoManager
 * @run main bug4992178
 */

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

public class bug4992178 {

    public static void main(String[] argv) throws Exception {
        TestUndoManager manager = new TestUndoManager();
        manager.setLimit(1);
        AbstractUndoableEdit edit = new MyUndoableEdit();
        manager.addEdit(edit);

        manager.setLimit(-1);

        manager.discardAllEdits();

        if (manager.getVectorSize() != 0) {
            throw new RuntimeException(
                "UndoManager's vector size should be 0 after discarding all changes");
        }
    }

    static class TestUndoManager extends UndoManager {
        public int getVectorSize() {
            return edits.size();
        }
    }

    static class MyUndoableEdit extends AbstractUndoableEdit {
        @Override
        public void undo() throws CannotUndoException {}
        @Override
        public void redo() throws CannotRedoException {}
    }

}
