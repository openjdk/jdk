package testdata;

import java.io.Serializable;
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface FunctionalInterface {
}

@Deprecated
public class TestClass extends BaseClass implements Serializable {

    public static final int STATIC_FIELD = 42;

    private String instanceField;

    public TestClass(String instanceField) {
        this.instanceField = instanceField;
    }

    public static void staticMethod() {
        System.out.println(".");
    }

    @Override
    public String toString() {
        return "field:" + instanceField;
    }

    public void riskyMethod() throws Exception {
        throw new Exception(".");
    }

    public enum Status {
        UNSTARTED,
        PASS,
        FAIL;
    }

    public class InnerClass {
        public void innerMethod() {
            System.out.println(".");
        }
    }
}

class BaseClass {
    protected void baseMethod() {
        System.out.println(".");
    }
}
