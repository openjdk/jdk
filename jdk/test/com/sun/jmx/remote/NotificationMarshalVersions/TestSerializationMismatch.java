
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * @test
 * @summary Tests for the RMI unmarshalling errors not to cause silent failure.
 * @author Jaroslav Bachorik
 * @bug 6937053 8005472
 *
 * @run clean TestSerializationMismatch
 * @run main/othervm TestSerializationMismatch
 *
 */
public class TestSerializationMismatch {
    static final String clientDir = "Client";
    static final String serverDir = "Server";
    static final String testSrc = System.getProperty("test.src");
    static final String testSrcDir = testSrc != null ? testSrc : ".";
    static final String testSrcClientDir = testSrcDir + File.separator + clientDir + File.separator;
    static final String testSrcServerDir = testSrcDir + File.separator + serverDir + File.separator;
    static final String testClasses = System.getProperty("test.classes");
    static final String testClassesDir = testClasses != null ? testClasses : ".";
    static final String testClassesClientDir = testClassesDir + File.separator + clientDir + File.separator;
    static final String testClassesServerDir = testClassesDir + File.separator + serverDir + File.separator;

    static final boolean debug = true;

    public static void main(String[] args) throws Exception {
        setup();

        compileClient();
        compileServer();

        debug("starting server");
        String url = startServer();
        debug("server started and listening on " + url);
        debug("starting client");
        startClient(url);
    }

    static void setup() {
        debug("setting up the output dirs");
        cleanupDir(testClassesClientDir);
        cleanupDir(testClassesServerDir);
    }

    static void cleanupDir(String path) {
        debug("cleaning " + path);
        File dir = new File(path);
        if (dir.exists()) {
            for(File src : dir.listFiles()) {
                boolean rslt = src.delete();
                debug((rslt == false ? "not " : "") + "deleted " + src);
            }
        } else {
            dir.mkdirs();
        }
    }

    static void compileClient() {
        debug("compiling client");
        compile("-d" , testClassesClientDir,
            "-sourcepath", testSrcClientDir,
            testSrcClientDir + "Client.java",
            testSrcClientDir + "ConfigKey.java",
            testSrcClientDir + "TestNotification.java");
    }

    static void compileServer() {
        debug("compiling server");
        compile("-d" , testClassesServerDir,
            "-sourcepath", testSrcServerDir,
            testSrcServerDir + "Server.java",
            testSrcServerDir + "ConfigKey.java",
            testSrcServerDir + "TestNotification.java",
            testSrcServerDir + "Ste.java",
            testSrcServerDir + "SteMBean.java");
    }

    static String startServer() throws Exception {
        ClassLoader serverCL = customCL(testClassesServerDir);

        Class serverClz = serverCL.loadClass("Server");
        Method startMethod = serverClz.getMethod("start");
        return (String)startMethod.invoke(null);
    }

    static void startClient(String url) throws Exception {
        ClassLoader clientCL = customCL(testClassesClientDir);

        Thread.currentThread().setContextClassLoader(clientCL);
        Class clientClz = clientCL.loadClass("Client");
        Method runMethod = clientClz.getMethod("run", String.class);
        runMethod.invoke(null, url);
    }

    static ClassLoader customCL(String classDir) throws Exception {
        return new URLClassLoader(
            new URL[]{
                new File(classDir).toURI().toURL()
            },
            TestSerializationMismatch.class.getClassLoader()
        );
    }

    static void debug(Object message) {
        if (debug) {
            System.out.println(message);
        }
    }

    /* run javac <args> */
    static void compile(String... args) {
        debug("Running: javac " + Arrays.toString(args));
        if (com.sun.tools.javac.Main.compile(args) != 0) {
            throw new RuntimeException("javac failed: args=" + Arrays.toString(args));
        }
    }
}
