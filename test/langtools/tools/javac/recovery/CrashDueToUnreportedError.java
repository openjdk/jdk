/**
 * @test /nodynamiccopyright/
 * @bug 8320948
 * @summary NPE due to unreported compiler error
 * @compile/fail/ref=CrashDueToUnreportedError.out -XDrawDiagnostics CrashDueToUnreportedError.java
 */

import java.util.List;

public class CrashDueToUnreportedError {
    class Builder {
        private Builder(Person person, String unused) {}
        public Builder withTypes(Entity<String> entities) {
            return new Builder(Person.make(Entity.combineAll(entities)));
        }
    }

    interface Person {
        static <E> Person make(List<? extends Entity<E>> eventSubtypes) {
            return null;
        }
    }

    class Entity<E> {
        public static <Root> List<? extends Entity<Root>> combineAll(Entity<Root> subtypes) {
            return null;
        }
    }
}
