package java.util.function;

import java.util.logging.Logger;

public class Capturing {

    static boolean not(boolean original) {
        return !original;
    }

    static int not(int original) {
        return ~original;
    }

    static boolean not(Object o) {
        return false;
    }

    public static void main(String[] args) {

        var f = Function.of(Capturing::not);
        var p = Predicate.of(Capturing::not);

        // Function
        {
            var function = Function.of(String::isEmpty); // Function<String, Boolean>
            var predicate = Predicate.of(String::isEmpty); // Predicate<String>

            // Fluent composition
            var chained = Function.of(String::length)  // Function<String, Integer>
                    .andThen(Integer::byteValue);      // Function<String, Byte>
        }

        // Predicate
        {
            // Resolve ambiguity
            var function = Function.of(String::isEmpty); // Function<String, Boolean>
            var predicate = Predicate.of(String::isEmpty); // Predicate<String>

            // Fluent composition
            var composed = Predicate.of(String::isEmpty)
                    .or(s -> s.startsWith("*")); // Predicate<String>
        }

        // UnaryOperator
        {
            var function = Function.of(String::stripTrailing); // Function<String, String>
            var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>

            // Fluent composition
            var composed = UnaryOperator.of(String::stripTrailing)
                    .andThenUnary(String::stripIndent);  // UnaryOperator<String>
        }

        // BiConsumer
        {


        }

            Capturing c = new Capturing();

            var biConsumer = BiConsumer.of(c::toConsole)
                    .andThen(c::toLogger); // BiConsumer<Long, String>

            var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>

            // Fluent composition
            var composed = UnaryOperator.of(String::stripTrailing)
                    .andThenUnary(String::stripIndent);  // UnaryOperator<String>
        }

        void toConsole(long id, String message) {
            System.out.format("%d = %s%n", id, message);
        }

        static Logger LOGGER = null;

        void toLogger(long id, String message) {
            LOGGER.info(String.format("%d = %s", id, message));
        }

    }

}
