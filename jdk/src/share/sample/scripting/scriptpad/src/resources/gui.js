/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */

/*
 * Few user interface utilities.
 */

if (this.window === undefined) {
    this.window = null;
}

/**
 * Swing invokeLater - invokes given function in AWT event thread
 */
Function.prototype.invokeLater = function() {
    var SwingUtilities = javax.swing.SwingUtilities;
    var func = this;
    var args = arguments;
    SwingUtilities.invokeLater(new java.lang.Runnable() {
                       run: function() {
                           func.apply(func, args);
                       }
                  });
};

/**
 * Swing invokeAndWait - invokes given function in AWT event thread
 * and waits for it's completion
 */
Function.prototype.invokeAndWait = function() {
    var SwingUtilities = javax.swing.SwingUtilities;
    var func = this;
    var args = arguments;
    SwingUtilities.invokeAndWait(new java.lang.Runnable() {
                       run: function() {
                           func.apply(func, args);
                       }
                  });
};

/**
 * Am I running in AWT event dispatcher thread?
 */
function isEventThread() {
    var SwingUtilities = javax.swing.SwingUtilities;
    return SwingUtilities.isEventDispatchThread();
}
isEventThread.docString = "returns whether the current thread is GUI thread";

/**
 * Opens a file dialog box
 *
 * @param curDir current directory [optional]
 * @param save flag tells whether this is a save dialog or not
 * @return selected file or else null
 */
function fileDialog(curDir, save) {
    var result;
    function _fileDialog() {
        if (curDir == undefined) {
            curDir = new java.io.File(".");
        }

        var JFileChooser = javax.swing.JFileChooser;
        var dialog = new JFileChooser(curDir);
        var res = save ? dialog.showSaveDialog(window):
            dialog.showOpenDialog(window);

        if (res == JFileChooser.APPROVE_OPTION) {
           result = dialog.getSelectedFile();
        } else {
           result = null;
        }
    }

    if (isEventThread()) {
        _fileDialog();
    } else {
        _fileDialog.invokeAndWait();
    }

    return result;
}
fileDialog.docString = "show a file dialog box";

/**
 * Opens a color chooser dialog box
 *
 * @param title of the dialog box [optional]
 * @param color default color [optional]
 * @return chosen color or default color
 */
function colorDialog(title, color) {
    var result;

    function _colorDialog() {
        if (title == undefined) {
            title = "Choose Color";
        }

        if (color == undefined) {
            color = java.awt.Color.BLACK;
        }

        var chooser = new javax.swing.JColorChooser();
        var res = chooser.showDialog(window, title, color);
        result = res ? res : color;
    }

    if (isEventThread()) {
        _colorDialog();
    } else {
        _colorDialog.invokeAndWait();
    }

    return result;
}
colorDialog.docString = "shows a color chooser dialog box";

/**
 * Shows a message box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 * @param msgType type of message box [constants in JOptionPane]
 */
function msgBox(msg, title, msgType) {
    function _msgBox() {
        var JOptionPane = javax.swing.JOptionPane;
        if (msg === undefined) msg = "undefined";
        if (msg === null) msg = "null";
        if (title == undefined) title = msg;
        if (msgType == undefined) msgType = JOptionPane.INFORMATION_MESSAGE;
        JOptionPane.showMessageDialog(window, msg, title, msgType);
    }

    if (isEventThread()) {
        _msgBox();
    } else {
        _msgBox.invokeAndWait();
    }
}
msgBox.docString = "shows MessageBox to the user";

/**
 * Shows an information alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */
function alert(msg, title) {
    var JOptionPane = javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.INFORMATION_MESSAGE);
}
alert.docString = "shows an alert message box to the user";

/**
 * Shows an error alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */
function error(msg, title) {
    var JOptionPane = javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.ERROR_MESSAGE);
}
error.docString = "shows an error message box to the user";

/**
 * Shows a warning alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */
function warn(msg, title) {
    var JOptionPane = javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.WARNING_MESSAGE);
}
warn.docString = "shows a warning message box to the user";

/**
 * Shows a prompt dialog box
 *
 * @param question question to be asked
 * @param answer default answer suggested [optional]
 * @return answer given by user
 */
function prompt(question, answer) {
    var result;
    function _prompt() {
        var JOptionPane = javax.swing.JOptionPane;
        if (answer == undefined) answer = "";
        result = JOptionPane.showInputDialog(window, question, answer);
    }

    if (isEventThread()) {
        _prompt();
    } else {
        _prompt.invokeAndWait();
    }

    return result;
}
prompt.docString = "shows a prompt box to the user and returns the answer";

/**
 * Shows a confirmation dialog box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 * @return boolean (yes->true, no->false)
 */
function confirm(msg, title) {
    var result;
    var JOptionPane = javax.swing.JOptionPane;

    function _confirm() {
        if (title == undefined) title = msg;
        var optionType = JOptionPane.YES_NO_OPTION;
        result = JOptionPane.showConfirmDialog(window, msg, title, optionType);
    }

    if (isEventThread()) {
        _confirm();
    } else {
        _confirm.invokeAndWait();
    }

    return result == JOptionPane.YES_OPTION;
}
confirm.docString = "shows a confirmation message box to the user";

/**
 * Exit the process after confirmation from user
 *
 * @param exitCode return code to OS [optional]
 */
function exit(exitCode) {
    if (exitCode == undefined) exitCode = 0;
    if (confirm("Do you really want to exit?")) {
        java.lang.System.exit(exitCode);
    }
}
exit.docString = "exits jconsole";

// synonym to exit
var quit = exit;

// if echo function is not defined, define it as synonym
// for println function
if (this.echo == undefined) {
    function echo(str) {
        println(str);
    }
}

