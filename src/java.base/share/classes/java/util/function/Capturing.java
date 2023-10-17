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

            Capturing c = new Capturing();

            var biConsumer = BiConsumer.of(c::toConsole)
                    .andThen(c::toLogger); // BiConsumer<Long, String>

            var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>

            // Fluent composition
            var composed = UnaryOperator.of(String::stripTrailing)
                    .andThenUnary(String::stripIndent);  // UnaryOperator<String>
        }

        // BiConsumer
        {

            Capturing c = new Capturing();

            var biConsumer = BiConsumer.of(c::toConsole)
                    .andThen(c::toLogger); // BiConsumer<Long, String>

            var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>

            // Fluent composition
            var composed = UnaryOperator.of(String::stripTrailing)
                    .andThenUnary(String::stripIndent);  // UnaryOperator<String>
        }

        // BiFunction
        {
            // Resolve ambiguity
            var function = BiFunction.of(String::endsWith);   // BiFunction<String, String, Boolean>
            var predicate = BiPredicate.of(String::endsWith); // BiPredicate<String, String>

            // Fluent composition
            var chained = BiFunction.of(String::repeat)     // BiFunction<String, Integer, String>
                    .andThen(String::length);               // Function<String, Integer>
        }

        // BinaryOperator
        {
            // Resolve ambiguity
            var biFunction = BiFunction.of(Integer::sum);        // BiFunction<Integer, Integer, Integer>
            var unaryOperator = BinaryOperator.of(Integer::sum); // BinaryOperator<Integer>

            // Fluent composition
            var composed = BinaryOperator.of(Integer::sum)     // BinaryOperator<Integer>
                               .andThen(Integer::toHexString); // BiFunction<Integer, Integer, String>
        }

        // BiPredicate
        {
            var biFunction = BiFunction.of(String::equals);      // BiFunction<String, Object, Boolean>
            var biPredicate = BiPredicate.of(String::equals);    // BiPredicate<Integer, Object>

            // Fluent composition
            var composed = BiPredicate.of(String::endsWith)     // BiPredicate<String, String>
                    .or(String::startsWith);                    // BiPredicate<String, String>
        }

        // ObjDoubleConsumer
        {
            Capturing c = new Capturing();

            var odc = ObjDoubleConsumer.of(c::action);  // ObjDoubleConsumer<String>
            var bc = BiConsumer.of(c::action);          // BiConsumer<String, Double>
        }




    }

    void action(String s, double d) {

    }

    void action(String s, int d) {

    }


    void toConsole(long id, String message) {
        System.out.format("%d = %s%n", id, message);
    }

    static Logger LOGGER = null;

    void toLogger(long id, String message) {
        LOGGER.info(String.format("%d = %s", id, message));
    }

}
