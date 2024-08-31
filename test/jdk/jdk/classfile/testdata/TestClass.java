package testdata;

import java.io.Serializable;
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface FunctionalInterface {
}

class BaseClass {
    protected void baseMethod() {
        System.out.println(".");
    }
}
