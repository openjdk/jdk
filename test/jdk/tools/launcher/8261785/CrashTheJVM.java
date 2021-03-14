import java.io.IOException;

public class CrashTheJVM {
    public static void main(String... args) throws IOException {
        System.out.println("Fine 1: from the outer class");

        new Object() {
            public static void main(String... args) throws IOException {
                System.out.println("Crash Before Fix 1: from anonymous nested class");
            }
        };
        class LocalNestedClass {
            public static void main(String... args) throws IOException {
                System.out.println("Crash Before Fix 2: from local nested class");
            }
        }
    }

    public void fromMethod() {
        new Object() {
            public static void main(String... args) throws IOException {
                System.out.println("Crash Before Fix 3: from local anonymous class");
            }
        };
        class LocalInnerClass {
            public static void main(String... args) throws IOException {
                System.out.println("Crash Before Fix 4: from local inner class");
            }
        }
    }

    public class InnerClass {
        public static void main(String... args) throws IOException {
            System.out.println("Fine 2: from inner class");
        }
    }

    public static class NestedClass {
        public static void main(String... args) throws IOException {
            System.out.println("Fine 3: from nested class");
        }
    }
}
