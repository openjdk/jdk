/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
import java.security.Policy;

/**
 *
 *
 * @author huizhe.wang@oracle.com
 */
public class TestBase {
    public static boolean isWindows = false;
    static {
        if (System.getProperty("os.name").indexOf("Windows")>-1) {
            isWindows = true;
        }
    };

    String filepath;
    boolean hasSM;
    String curDir;
    Policy origPolicy;
    String testName;
    static String errMessage;

    int passed = 0, failed = 0;

    /**
     * Creates a new instance of StreamReader
     */
    public TestBase(String name) {
        testName = name;
    }

    //junit @Override
    protected void setUp() {
        if (System.getSecurityManager() != null) {
            hasSM = true;
            System.setSecurityManager(null);
        }

        filepath = System.getProperty("test.src");
        if (filepath == null) {
            //current directory
            filepath = System.getProperty("user.dir");
        }
        origPolicy = Policy.getPolicy();

    }

    //junit @Override
    public void tearDown() {
        // turn off security manager and restore policy
        System.setSecurityManager(null);
        Policy.setPolicy(origPolicy);
        if (hasSM) {
            System.setSecurityManager(new SecurityManager());
        }
        System.out.println("\nNumber of tests passed: " + passed);
        System.out.println("Number of tests failed: " + failed + "\n");

        if (errMessage != null ) {
            throw new RuntimeException(errMessage);
        }
    }

    void fail(String errMsg) {
        if (errMessage == null) {
            errMessage = errMsg;
        } else {
            errMessage = errMessage + "\n" + errMsg;
        }
        failed++;
    }

    void success(String msg) {
        passed++;
        System.out.println(msg);
    }

}
