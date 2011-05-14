/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.demo.scripting.jconsole;

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;


/**
 * A JPanel subclass containing a scrollable text area displaying the
 * jconsole's script console.
 */

class ScriptShellPanel extends JPanel {

    private static final long serialVersionUID = 4116273141148726319L;

    // interface to evaluate script command and script prompt
    interface CommandProcessor {
        // execute given String as script and return the result
        public String executeCommand(String cmd);
        // get prompt used for interactive read-eval-loop
        public String getPrompt();
    }

    // my script command processor
    private CommandProcessor commandProcessor;
    // editor component for command editing
    private JTextComponent editor;

    private final ExecutorService commandExecutor =
            Executors.newSingleThreadExecutor();

    // document management
    private boolean updating;

    public ScriptShellPanel(CommandProcessor cmdProc) {
        setLayout(new BorderLayout());
        this.commandProcessor = cmdProc;
        this.editor = new JTextArea();
        editor.setDocument(new EditableAtEndDocument());
        JScrollPane scroller = new JScrollPane();
        scroller.getViewport().add(editor);
        add(scroller, BorderLayout.CENTER);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (updating) return;
                beginUpdate();
                editor.setCaretPosition(editor.getDocument().getLength());
                if (insertContains(e, '\n')) {
                    String cmd = getMarkedText();
                    // Handle multi-line input
                    if ((cmd.length() == 0) ||
                        (cmd.charAt(cmd.length() - 1) != '\\')) {
                        // Trim "\\n" combinations
                        final String cmd1 = trimContinuations(cmd);
                        commandExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                final String result = executeCommand(cmd1);

                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (result != null) {
                                            print(result + "\n");
                                        }
                                        printPrompt();
                                        setMark();
                                        endUpdate();
                                    }
                                });
                            }
                        });
                    } else {
                        endUpdate();
                    }
                } else {
                    endUpdate();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
            }
        });

        // This is a bit of a hack but is probably better than relying on
        // the JEditorPane to update the caret's position precisely the
        // size of the insertion
        editor.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                int len = editor.getDocument().getLength();
                if (e.getDot() > len) {
                    editor.setCaretPosition(len);
                }
            }
        });

        Box hbox = Box.createHorizontalBox();
        hbox.add(Box.createGlue());
        JButton button = new JButton("Clear"); // FIXME: i18n?
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clear();
            }
        });
        hbox.add(button);
        hbox.add(Box.createGlue());
        add(hbox, BorderLayout.SOUTH);

        clear();
    }

    public void dispose() {
        commandExecutor.shutdown();
    }

    @Override
    public void requestFocus() {
        editor.requestFocus();
    }

    public void clear() {
        clear(true);
    }

    public void clear(boolean prompt) {
        EditableAtEndDocument d = (EditableAtEndDocument) editor.getDocument();
        d.clear();
        if (prompt) printPrompt();
        setMark();
        editor.requestFocus();
    }

    public void setMark() {
        ((EditableAtEndDocument) editor.getDocument()).setMark();
    }

    public String getMarkedText() {
        try {
            String s = ((EditableAtEndDocument) editor.getDocument()).getMarkedText();
            int i = s.length();
            while ((i > 0) && (s.charAt(i - 1) == '\n')) {
                i--;
            }
            return s.substring(0, i);
        } catch (BadLocationException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void print(String s) {
        Document d = editor.getDocument();
        try {
            d.insertString(d.getLength(), s, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }


    //
    // Internals only below this point
    //

    private String executeCommand(String cmd) {
        return commandProcessor.executeCommand(cmd);
    }

    private String getPrompt() {
        return commandProcessor.getPrompt();
    }

    private void beginUpdate() {
        editor.setEditable(false);
        updating = true;
    }

    private void endUpdate() {
        editor.setEditable(true);
        updating = false;
    }

    private void printPrompt() {
        print(getPrompt());
    }

    private boolean insertContains(DocumentEvent e, char c) {
        String s = null;
        try {
            s = editor.getText(e.getOffset(), e.getLength());
            for (int i = 0; i < e.getLength(); i++) {
                if (s.charAt(i) == c) {
                    return true;
                }
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private String trimContinuations(String text) {
        int i;
        while ((i = text.indexOf("\\\n")) >= 0) {
            text = text.substring(0, i) + text.substring(i+1, text.length());
        }
        return text;
    }
}
