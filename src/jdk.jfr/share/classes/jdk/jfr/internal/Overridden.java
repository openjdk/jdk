package jdk.jfr.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Inherited
public @interface Overridden {
    enum Target {
        EVENT_THREAD(Thread.class), STACKTRACE(StackTraceElement[].class);

        private final Class<?> targetType;
        Target(Class<?> targetType) {
            this.targetType = targetType;
        }

        public Class<?> getTargetType() {
            return targetType;
        }
    }

    Target value();
}