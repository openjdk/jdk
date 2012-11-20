/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8001533
 * @summary Test launching FX application with java -jar
 * Test uses main method and blank main method, a jfx app class and an incorrest
 * jfx app class, a main-class for the manifest, a bogus one and none.
 * All should execute except the incorrect fx app class entries.
 * @run main FXLauncherTest
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FXLauncherTest extends TestHelper {
    private static final String FX_MARKER_CLASS = "javafx.application.Application";
    private static void line() {
        System.out.println("_____________________________________________");
    }
    private static File MainJavaFile = null;
    private static final File FXtestJar =  new File("fxtest.jar");
    private static final File ManifestFile = new File("manifest.txt");
    private static final File ScratchDir = new File(".");

    /* standard main class can be used as java main for fx app class */
    static final String StdMainClass = "helloworld.HelloWorld";
    static int testcount = 0;

    /* a main method and a blank. */
    static final String[] MAIN_METHODS = {
        "public static void main(String[] args) { launch(args); }",
        " "
    };

    // Array of parameters to pass to fx application.
    static final String[] APP_PARMS = { "one", "two" };

    // Create fx java file for test application
    static void createJavaFile(String mainmethod) {
        try {
            String mainClass = "HelloWorld";
            List<String> contents = new ArrayList<>();
            contents.add("package helloworld;");
            contents.add("import javafx.application.Application;");
            contents.add("import javafx.event.ActionEvent;");
            contents.add("import javafx.event.EventHandler;");
            contents.add("import javafx.scene.Scene;");
            contents.add("import javafx.scene.control.Button;");
            contents.add("import javafx.scene.layout.StackPane;");
            contents.add("import javafx.stage.Stage;");
            contents.add("public class HelloWorld extends Application {");
            contents.add(mainmethod);
            contents.add("@Override");
            contents.add("public void start(Stage primaryStage) {");
            contents.add("    primaryStage.setTitle(\"Hello World!\");");
            contents.add("    Button btn = new Button();");
            contents.add("    btn.setText(\"Say 'Hello World'\");");
            contents.add("    btn.setOnAction(new EventHandler<ActionEvent>() {");
            contents.add("        @Override");
            contents.add("        public void handle(ActionEvent event) {");
            contents.add("            System.out.println(\"Hello World!\");");
            contents.add("        }");
            contents.add("    });");
            contents.add("    StackPane root = new StackPane();");
            contents.add("    root.getChildren().add(btn);");
            contents.add("    primaryStage.setScene(new Scene(root, 300, 250));");
            contents.add("//    primaryStage.show(); no GUI for auto tests. ");
            contents.add("    System.out.println(\"HelloWorld.primaryStage.show();\");");
            contents.add("    System.out.println(\"Parameters:\");" );
            contents.add("    for(String p : getParameters().getUnnamed())");
            contents.add("        System.out.println(\"parameter: \" + p );" );
            contents.add("    System.exit(0);");
            contents.add("}");
            contents.add("}");

            // Create and compile java source.
            MainJavaFile = new File(mainClass + JAVA_FILE_EXT);
            createFile(MainJavaFile, contents);
            compile("-d", ".", mainClass + JAVA_FILE_EXT);
        } catch (java.io.IOException ioe) {
            ioe.printStackTrace();
            throw new RuntimeException("Failed creating HelloWorld.");
        }
    }

    /*
     * Create class to extend fx java file for test application
     * TODO: make test to create java file and this extension of the java file
     *      and jar them together an run app via this java class.
     */
    static void createExtJavaFile(String mainmethod) {
        try {
            String mainClass = "ExtHello";
            List<String> contents = new ArrayList<>();
            contents.add("package helloworld;");
            contents.add("public class ExtHello extends HelloWorld {");
            contents.add(mainmethod);
            contents.add("}");
            // Create and compile java source.
            MainJavaFile = new File(mainClass + JAVA_FILE_EXT);
            createFile(MainJavaFile, contents);
            compile("-cp", ".", "-d", ".", mainClass + JAVA_FILE_EXT);
        } catch (java.io.IOException ioe) {
            ioe.printStackTrace();
            throw new RuntimeException("Failed creating HelloWorld.");
        }
    }

    // Create manifest for test fx application
    static List<String> createManifestContents(String mainclassentry) {
        List<String> mcontents = new ArrayList<>();
        mcontents.add("Manifest-Version: 1.0");
        mcontents.add("Created-By: FXLauncherTest");
        mcontents.add("Main-Class: " + mainclassentry);
        return mcontents;
    }

    // Method to marshal createJar to TestHelper.createJar()
    static void createJar(File theJar, File manifestFile) {
        createJar("cvmf", manifestFile.getName(),
                  theJar.getAbsolutePath(), "helloworld");
    }

    static void saveFile(String tname, int testcount, File srcFile) {
        File newFile = new File(tname + "-" + testcount + "-" + srcFile.getName());
        System.out.println("renaming " + srcFile.getName() +
                           " to " + newFile.getName());
        srcFile.renameTo(newFile);
    }

    static void cleanupFiles() throws IOException {
        for(File f : ScratchDir.listFiles()) {
            recursiveDelete(f);
        }
    }

    static void checkStatus(TestResult tr, String testName, int testCount,
                            String mainclass) throws Exception {
        if (tr.testStatus) {
            System.out.println("PASS: " + testName + ":" + testCount +
                               " : test with " + mainclass);
            cleanupFiles();
        } else {
            saveFile(testName, testcount, FXtestJar);
            System.out.println("FAIL: " + testName + ":" + testCount +
                               " : test with " + mainclass);
            cleanupFiles();
            System.err.println(tr);
            throw new Exception("Failed: " + testName + ":" + testCount);
        }
    }

    /*
     * Set Main-Class and iterate main_methods.
     * Try launching with both -jar and -cp methods.
     * All cases should run.
     */
    @Test
    static void testBasicFXApp() throws Exception {
        testBasicFXApp(true);
        testBasicFXApp(false);
    }

    static void testBasicFXApp(boolean useCP) throws Exception {
        String testname = "testBasicFXApp";
        for (String mm : MAIN_METHODS) {
            testcount++;
            line();
            System.out.println("test# " + testcount +
                "-  Main method: " + mm +
                 ";  MF main class: " + StdMainClass);
            createJavaFile(mm);
            createFile(ManifestFile, createManifestContents(StdMainClass));
            createJar(FXtestJar, ManifestFile);
            String sTestJar = FXtestJar.getAbsolutePath();
            TestResult tr;
            if (useCP) {
                tr = doExec(javaCmd, "-cp", sTestJar, StdMainClass, APP_PARMS[0], APP_PARMS[1]);
                testname = testname.concat("_useCP");
            } else {
                tr = doExec(javaCmd, "-jar", sTestJar, APP_PARMS[0], APP_PARMS[1]);
            }
            tr.checkPositive();
            if (tr.testStatus && tr.contains("HelloWorld.primaryStage.show()")) {
                for (String p : APP_PARMS) {
                    if (!tr.contains(p)) {
                        System.err.println("ERROR: Did not find "
                                + p + " in output!");
                    }
                }
            }
            checkStatus(tr, testname, testcount, StdMainClass);
        }
    }

    /*
     * Set Main-Class and iterate main methods.
     * Main class extends another class that extends Application.
     * Try launching with both -jar and -cp methods.
     * All cases should run.
     */
    @Test
    static void testExtendFXApp() throws Exception {
        testExtendFXApp(true);
        testExtendFXApp(false);
    }

    static void testExtendFXApp(boolean useCP) throws Exception {
        String testname = "testExtendFXApp";
        for (String mm : MAIN_METHODS) {
            testcount++;
            line();
            System.out.println("test# " + testcount +
                "-  Main method: " + mm + ";  MF main class: " + StdMainClass);
            createJavaFile(mm);
            createExtJavaFile(mm);
            createFile(ManifestFile, createManifestContents(StdMainClass));
            createJar(FXtestJar, ManifestFile);
            String sTestJar = FXtestJar.getAbsolutePath();
            TestResult tr;
            if (useCP) {
                tr = doExec(javaCmd, "-cp", sTestJar, StdMainClass, APP_PARMS[0], APP_PARMS[1]);
                testname = testname.concat("_useCP");
            } else {
                tr = doExec(javaCmd, "-jar", sTestJar, APP_PARMS[0], APP_PARMS[1]);
            }
            tr.checkPositive();
            if (tr.testStatus && tr.contains("HelloWorld.primaryStage.show()")) {
                for (String p : APP_PARMS) {
                    if (!tr.contains(p)) {
                        System.err.println("ERROR: Did not find "
                                + p + " in output!");
                    }
                }
            }
            checkStatus(tr, testname, testcount, StdMainClass);
        }
    }

    /*
     * test to ensure that we don't load any extraneous fx jars when
     * launching a standard java application
     */
    @Test
    static void testExtraneousJars()throws Exception {
        String testname = "testExtraneousJars";
        testcount++;
        line();
        System.out.println("test# " + testcount);
        TestResult tr = doExec(javacCmd, "-J-verbose:class", "-version");
        if (!tr.notContains("jfxrt.jar")) {
            System.out.println("testing for extraneous jfxrt jar");
            System.out.println(tr);
            throw new Exception("jfxrt.jar is being loaded by javac!!!");
        }
        checkStatus(tr, testname, testcount, StdMainClass);
    }

    public static void main(String... args) throws Exception {
        //check if fx is part of jdk
        Class<?> fxClass = null;
        try {
            fxClass = Class.forName(FX_MARKER_CLASS);
        } catch (ClassNotFoundException ex) {
            // do nothing
        }
        if (fxClass != null) {
            FXLauncherTest fxt = new FXLauncherTest();
            fxt.run(args);
            if (testExitValue > 0) {
                System.out.println("Total of " + testExitValue
                        + " failed. Test cases covered: "
                        + FXLauncherTest.testcount);
                System.exit(1);
            } else {
                System.out.println("All tests pass. Test cases covered: "
                        + FXLauncherTest.testcount);
            }
        } else {
            System.err.println("Warning: JavaFX components missing or not supported");
            System.err.println("         test passes vacuosly.");
         }
    }
}
