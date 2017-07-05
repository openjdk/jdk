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

/*
 * This script creates a simple Notepad-like interface, which 
 * serves as a simple script editor, runner.
 *
 * File dependency:
 *
 *    gui.js -> for basic GUI functions
 */

/*
 * JavaImporter helps in avoiding pollution of JavaScript
 * global namespace. We can import multiple Java packages
 * with this and use the JavaImporter object with "with"
 * statement.
 */
var guiPkgs = new JavaImporter(java.awt, java.awt.event,
                         javax.swing, javax.swing.undo,
                         javax.swing.event, javax.swing.text);

with (guiPkgs) {   

     /*
      * within this "with" statement all Java classes in
      * packages defined in "guiPkgs" can be used as simple
      * names instead of the fully qualified names.
      */


     // main entry point of the scriptpad application
     function main() {
        function createEditor() {
            var c = new JTextArea();
            c.setDragEnabled(true);
            c.setFont(new Font("monospaced", Font.PLAIN, 12));
            return c;
        }

        /*const*/ var titleSuffix = "- Scriptpad";
        /*const*/ var defaultTitle = "Untitled" + titleSuffix;

        // Scriptpad's main frame
        var frame;
        // Scriptpad's main editor
        var editor;

        // To track the current file name
        var curFileName = null;

        // To track whether the current document 
        // has been modified or not
        var docChanged = false;

        // check and alert user for unsaved
        // but modified document        
        function checkDocChanged() {
            if (docChanged) {
                // ignore zero-content untitled document
                if (curFileName == null &&
                    editor.document.length == 0) {
                    return;
                }

                if (confirm(
                    "Do you want to save the changes?",
                    "The document has changed")) {
                    actionSave();
                }
            }
        }

        // set a document listener to track 
        // whether that is modified or not
        function setDocListener() {
            var doc = editor.getDocument();
            docChanged = false;
            doc.addDocumentListener(new DocumentListener() {
                    equals: function(o) { return this === o; },
                    toString: function() { return "doc listener"; },
                    changeUpdate: function() { docChanged = true; },
                    insertUpdate: function() { docChanged = true; },
                    removeUpdate: function() { docChanged = true; },
                });
        }

        // menu action functions

        // "File" menu 

        // create a "new" document
        function actionNew(){
            checkDocChanged();
            curFileName = null;
            editor.setDocument(new PlainDocument());
            setDocListener();
            frame.setTitle(defaultTitle);
            editor.revalidate();
        }

        // open an existing file
        function actionOpen() {
            checkDocChanged();
            var f = fileDialog();
            if (f == null) {
                return;
            }
            if (f.isFile() && f.canRead()) {                
                frame.setTitle(f.getName() + titleSuffix);
                editor.setDocument(new PlainDocument());
                var progress = new JProgressBar();
                progress.setMinimum(0);
                progress.setMaximum(f.length());
                var doc = editor.getDocument();
                var inp = new java.io.FileReader(f);
                var buff = java.lang.reflect.Array.newInstance(
                                java.lang.Character.TYPE, 4096);
                var nch;
                while ((nch = inp.read(buff, 0, buff.length)) != -1) {
                    doc.insertString(doc.getLength(),
                         new java.lang.String(buff, 0, nch), null);
                    progress.setValue(progress.getValue() + nch);
                }
                inp.close();
                curFileName = f.getAbsolutePath();
                setDocListener();
             } else {
                error("Can not open file: " + f,
                    "Error opening file: " + f);                    
             }               
        }

        // open script from a URL
        function actionOpenURL() {
            checkDocChanged();
            var url = prompt("Address:");
            if (url == null) {
                return;
            }

            try {
                var u = new java.net.URL(url); 
                editor.setDocument(new PlainDocument());
                frame.setTitle(url + titleSuffix);
                var progress = new JProgressBar();
                progress.setMinimum(0);
                progress.setIndeterminate(true);
                var doc = editor.getDocument();
                var inp = new java.io.InputStreamReader(u.openStream());
                var buff = java.lang.reflect.Array.newInstance(
                                java.lang.Character.TYPE, 4096);
                var nch;
                while ((nch = inp.read(buff, 0, buff.length)) != -1) {
                    doc.insertString(doc.getLength(), 
                          new java.lang.String(buff, 0, nch), null);
                    progress.setValue(progress.getValue() + nch);
                }    
                curFileName = null;
                setDocListener();
            } catch (e) {
                error("Error opening URL: " + e,
                      "Can not open URL: " + url);
            }   
        } 

        // factored out "save" function used by 
        // save, save as menu actions
        function save(file) {
            var doc = editor.getDocument();
            frame.setTitle(file.getName() + titleSuffix);
            curFileName = file;
            var progress = new JProgressBar();
            progress.setMinimum(0);
            progress.setMaximum(file.length());            
            var out = new java.io.FileWriter(file);
            var text = new Segment();
            text.setPartialReturn(true);
            var charsLeft = doc.getLength();
            var offset = 0;
            while (charsLeft > 0) {
                doc.getText(offset, java.lang.Math.min(4096, charsLeft), text);
                out.write(text.array, text.offset, text.count);
                charsLeft -= text.count;
                offset += text.count;
                progress.setValue(offset);
                java.lang.Thread.sleep(10);               
            }
            out.flush();
            out.close();           
            docChanged = false;           
        }

        // file-save as menu
        function actionSaveAs() {
            var ret = fileDialog(null, true);
            if (ret == null) {
                return;
            }
            save(ret);
        }

        // file-save menu
        function actionSave() {            
            if (curFileName) {
                save(new java.io.File(curFileName));
            } else {
                actionSaveAs();
            }
        }

        // exit from scriptpad
        function actionExit() {
            checkDocChanged();           
            java.lang.System.exit(0);
        }

        // "Edit" menu 

        // cut the currently selected text
        function actionCut() {
            editor.cut();
        }

        // copy the currently selected text to clipboard
        function actionCopy() {
            editor.copy();
        }

        // paste clipboard content to document
        function actionPaste() {
            editor.paste();
        }

        // select all the text in editor
        function actionSelectAll() {
            editor.selectAll();
        }

        // "Tools" menu 

        // run the current document as JavaScript
        function actionRun() {
            var doc = editor.getDocument();
            var script = doc.getText(0, doc.getLength());
            var oldFile = engine.get(javax.script.ScriptEngine.FILENAME);
            try {
                if (this.engine == undefined) {
                    var m = new javax.script.ScriptEngineManager();
                    engine = m.getEngineByName("js");
                }
                engine.put(javax.script.ScriptEngine.FILENAME, frame.title);
                engine.eval(script, context);
            } catch (e) {  
                error(e, "Script Error");
                // e.rhinoException.printStackTrace();
            } finally {
                engine.put(javax.script.ScriptEngine.FILENAME, oldFile);
            }
        }

        // "Examples" menu 

        // show given script as new document
        function showScript(title, str) { 
            actionNew();
            frame.setTitle("Example - " + title + titleSuffix);
            var doc = editor.document;
            doc.insertString(0, str, null);
        }

        // "hello world"
        function actionHello() {
            showScript(arguments.callee.title,
                "alert('Hello, world');");
        }
        actionHello.title = "Hello, World";

        // eval the "hello world"!
        function actionEval() {
            showScript(actionEval.title,
                "eval(\"alert('Hello, world')\");");
        }
        actionEval.title = "Eval";

        // show how to access Java static methods
        function actionJavaStatic() {
            showScript(arguments.callee.title,
                "// Just use Java syntax\n" +
                "var props = java.lang.System.getProperties();\n" +
                "alert(props.get('os.name'));");
        }
        actionJavaStatic.title = "Java Static Calls";

        // show how to access Java classes, methods
        function actionJavaAccess() {
            showScript(arguments.callee.title,
                "// just use new JavaClass();\n" +
                "var fr = new javax.swing.JFrame();\n" +
                "// call all public methods as in Java\n" +
                "fr.setTitle('hello');\n" +
                "fr.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);\n" +
                "fr.setSize(200, 200);\n" +
                "fr.setVisible(true);");
        }
        actionJavaAccess.title = "Java Object Access";

        // show how to use Java bean conventions
        function actionJavaBean() {
            showScript(arguments.callee.title,
                "var fr = new javax.swing.JFrame();\n" +
                "fr.setSize(200, 200);\n" +
                "// access public get/set methods as fields\n" +
                "fr.defaultCloseOperation = javax.swing.WindowConstants.DISPOSE_ON_CLOSE;\n" +
                "fr.title = 'hello';\n" +
                "fr.visible = true;"); 
        }
        actionJavaBean.title = "Java Beans";

        // show how to implement Java interface
        function actionJavaInterface() {
            showScript(arguments.callee.title,
                "// use Java anonymizer class-like syntax!\n" +
                "var r = new java.lang.Runnable() {\n" +
                "            run: function() {\n" +
                "                    alert('hello');\n" +
                "            }\n" +
                "    };\n" +
                "// use the above Runnable to create a Thread\n" +
                "java.lang.Thread(r).start();\n" +
                "// For simple one method interfaces, just pass script function\n" +
                "java.lang.Thread(function() { alert('world'); }).start();");
        }
        actionJavaInterface.title = "Java Interfaces";

        // show how to import Java classes, packages
        function actionJavaImport() {
            showScript(arguments.callee.title,
                "// use Java-like import *...\n" +
                "//    importPackage(java.io);\n" +
                "// or import a specific class\n" +
                "//    importClass(java.io.File);\n" +
                "// or better - import just within a scope!\n" +
                "var ioPkgs = JavaImporter(java.io);\n" +
                "with (ioPkgs) { alert(new File('.').absolutePath); }");
        }
        actionJavaImport.title = "Java Import";

        // "Help" menu 

	/*
         * Shows a one liner help message for each 
         * global function. Note that this function 
         * depends on docString meta-data for each 
         * function.
         */
        function actionHelpGlobals() {
            var names = new java.util.ArrayList();
            for (var i in this) {
                var func = this[i];
                if (typeof(func) == "function" &&
                   ("docString" in func)) {
                    names.add(i);
                }
            }
            java.util.Collections.sort(names);
            var helpDoc = new java.lang.StringBuffer();
            helpDoc.append("<table border='1'>");
            var itr = names.iterator();
            while (itr.hasNext()) {
                var name = itr.next();
                helpDoc.append("<tr><td>");
                helpDoc.append(name);
                helpDoc.append("</td><td>");
                helpDoc.append(this[name].docString);
                helpDoc.append("</td></tr>");                
            }
            helpDoc.append("</table>");

            var helpEditor = new JEditorPane();
            helpEditor.setContentType("text/html");            
            helpEditor.setEditable(false);
            helpEditor.setText(helpDoc.toString());

            var scroller = new JScrollPane();
            var port = scroller.getViewport();
            port.add(helpEditor);

            var helpFrame = new JFrame("Help - Global Functions");
            helpFrame.getContentPane().add("Center", scroller);
            helpFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            helpFrame.pack();
            helpFrame.setSize(500, 600); 
            helpFrame.setVisible(true);
        }

        // show a simple about message for scriptpad
        function actionAbout() {
            alert("Scriptpad\nVersion 1.0", "Scriptpad");
        }

        /*
         * This data is used to construct menu bar.
         * This way adding a menu is easier. Just add
         * top level menu or add an item to an existing 
         * menu. "action" should be a function that is
         * called back on clicking the correponding menu.
         */
        var menuData = [
            {
              menu: "File",
              items: [ 
                  { name: "New",  action: actionNew , accel: KeyEvent.VK_N },
                  { name: "Open...", action: actionOpen, accel: KeyEvent.VK_O },                         
                  { name: "Open URL...", action: actionOpenURL, accel: KeyEvent.VK_U },
                  { name: "Save", action: actionSave, accel: KeyEvent.VK_S },
                  { name: "Save As...", action: actionSaveAs },
                  { name: "-" },
                  { name: "Exit", action: actionExit }
                ]
            },

            {
              menu: "Edit", 
              items: [ 
                  { name: "Cut", action: actionCut, accel: KeyEvent.VK_X },
                  { name: "Copy", action: actionCopy, accel: KeyEvent.VK_C },                        
                  { name: "Paste", action: actionPaste, accel: KeyEvent.VK_V },
                  { name: "-" },
                  { name: "Select All", action: actionSelectAll, accel: KeyEvent.VK_A }
                ]
            },
           
            { 
              menu: "Tools", 
              items: [                 
                  { name: "Run", action: actionRun, accel: KeyEvent.VK_R },
                ]
            },

            { 
              menu: "Examples", 
              items: [                 
                  { name: actionHello.title, action: actionHello },
                  { name: actionEval.title, action: actionEval },
                  { name: actionJavaStatic.title, action: actionJavaStatic },
                  { name: actionJavaAccess.title, action: actionJavaAccess },
                  { name: actionJavaBean.title, action: actionJavaBean },
                  { name: actionJavaInterface.title, action: actionJavaInterface },
                  { name: actionJavaImport.title, action: actionJavaImport },
               ]
            },

            { 
              menu: "Help",  
              items: [ 
                  { name: "Global Functions", action: actionHelpGlobals },
                  { name: "-" },
                  { name: "About Scriptpad", action: actionAbout },
                ]
            }
        ];


        function setMenuAccelerator(mi, accel) {
            var keyStroke = KeyStroke.getKeyStroke(accel,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask(), false);
            mi.setAccelerator(keyStroke);
        }
        
        // create a menubar using the above menu data
        function createMenubar() {
            var mb = new JMenuBar();
            for (var m in menuData) {
                var items = menuData[m].items;
                var menu = new JMenu(menuData[m].menu);                
                for (var i in items) {
                    if (items[i].name.equals("-")) {
                        menu.addSeparator();
                    } else {
                        var mi = new JMenuItem(items[i].name);
                        var action = items[i].action;
                        mi.addActionListener(action);
                        var accel = items[i].accel;
                        if (accel) {
                            setMenuAccelerator(mi, accel);
                        }
                        menu.add(mi);
                    }
	        }                    
	        mb.add(menu);
            }
            return mb;
        }

        // function to add a new menu item under "Tools" menu
        function addTool(menuItem, action, accel) {
            if (typeof(action) != "function") {
                return;
            }

            var toolsIndex = -1;
            // find the index of the "Tools" menu
            for (var i in menuData) {
                if (menuData[i].menu.equals("Tools")) {
                    toolsIndex = i;
                    break;
                }
            }
            if (toolsIndex == -1) {
                return;
            }
            var toolsMenu = frame.getJMenuBar().getMenu(toolsIndex);
            var mi = new JMenuItem(menuItem);
            mi.addActionListener(action);
            if (accel) {
                setMenuAccelerator(mi, accel);
            }
            toolsMenu.add(mi);
        }


        // create Scriptpad frame
        function createFrame() {
            frame = new JFrame();
            frame.setTitle(defaultTitle);
            frame.setBackground(Color.lightGray);
            frame.getContentPane().setLayout(new BorderLayout());

            // create notepad panel
            var notepad = new JPanel();
            notepad.setBorder(BorderFactory.createEtchedBorder());
            notepad.setLayout(new BorderLayout());

            // create editor
            editor = createEditor();
            var scroller = new JScrollPane();
            var port = scroller.getViewport();
            port.add(editor);

            // add editor to notepad panel
            var panel = new JPanel(); 
            panel.setLayout(new BorderLayout());        
            panel.add("Center", scroller);
            notepad.add("Center", panel);

            // add notepad panel to frame
            frame.getContentPane().add("Center", notepad);

            // set menu bar to frame and show the frame   
            frame.setJMenuBar(createMenubar());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setSize(500, 600);
        }

        // show Scriptpad frame
        function showFrame() {
            // set global variable by the name "window"
            this.window = frame;     

            // open new document
            actionNew();

            frame.setVisible(true);
        }

        // create and show Scriptpad frame
        createFrame();
        showFrame();

        /*
         * Application object has two fields "frame", "editor"
         * which are current JFrame and editor and a method
         * called "addTool" to add new menu item to "Tools" menu.
         */
        return {
            frame: frame,
            editor: editor,
            addTool: addTool
        };
    }
}

/*
 * Call the main and store Application object 
 * in a global variable named "application".
 */
var application = main();

if (this.load == undefined) {
    function load(file) {
        var ioPkgs = new JavaImporter(java.io);
        with (ioPkgs) {
            var stream = new FileInputStream(file);
            var bstream = new BufferedInputStream(stream);
            var reader = new BufferedReader(new InputStreamReader(bstream));
            var oldFilename = engine.get(engine.FILENAME);
            engine.put(engine.FILENAME, file);
            try {
                engine.eval(reader, context);
            } finally {
                engine.put(engine.FILENAME, oldFilename);
            }
            stream.close();
        }
    }
    load.docString = "loads the given script file";
}

/*
 * Load user specific init file under home dir, if found.
 */
function loadUserInit() {
    var home = java.lang.System.getProperty("user.home");
    var f = new java.io.File(home, "scriptpad.js");
    if (f.exists()) {
        engine.eval(new java.io.FileReader(f));
    }
}

loadUserInit();

