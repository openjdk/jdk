package javatest;

import java.util.*;
import java.io.*;

/**
 * Standard Java Syntax Test for Javadoc Generation
 *
 * This file tests that standard Java syntax still works perfectly
 * after adding MaxJ support to the javadoc tool.
 *
 * Tests include:
 * - Standard Java control flow (switch/case/default, if/else)
 * - All Java operators and expressions
 * - Modern Java features (records, sealed classes, pattern matching)
 * - Generics, lambdas, streams
 * - Annotations and reflection
 *
 * @author Standard Java Test
 * @version 1.0
 * @since JDK21
 */
public class StandardJavaSyntaxTest {

    /**
     * Tests standard Java switch statements and expressions.
     * Verifies no conflicts with MaxJ SWITCH/CASE syntax.
     *
     * @param value the switch control value
     * @param stringValue string for switch expression test
     * @return result from switch processing
     */
    public int testJavaSwitchStatements(int value, String stringValue) {
        int result = 0;

        // Traditional switch statement
        switch (value) {
            case 1:
                result = 10;
                break;
            case 2:
                result = 20;
                break;
            case 3:
            case 4:
                result = 30;
                break;
            default:
                result = -1;
                break;
        }

        // Modern switch expression (JDK 12+)
        result += switch (value) {
            case 1 -> 100;
            case 2 -> 200;
            case 3, 4 -> 300;
            default -> 0;
        };

        // String switch
        int stringResult = switch (stringValue) {
            case "hello" -> 1;
            case "world" -> 2;
            case "java" -> 3;
            default -> 0;
        };

        return result + stringResult;
    }

    /**
     * Tests standard Java conditional statements.
     * Verifies no conflicts with MaxJ IF/ELSE syntax.
     *
     * @param condition boolean condition
     * @param value integer value
     * @return conditional result
     */
    public boolean testJavaConditionals(boolean condition, int value) {
        boolean result = false;

        // Standard if/else
        if (condition) {
            result = true;
        } else {
            result = false;
        }

        // Nested if/else
        if (value > 0) {
            if (value < 10) {
                result = true;
            } else if (value < 100) {
                result = false;
            } else {
                result = true;
            }
        } else {
            result = false;
        }

        // Ternary operator
        result = condition ? true : false;

        return result;
    }

    /**
     * Tests all Java operators to ensure no conflicts.
     * Particularly important to test == vs MaxJ ===.
     *
     * @param a first operand
     * @param b second operand
     * @return operator test results
     */
    public boolean testJavaOperators(int a, int b) {
        // Arithmetic operators
        int sum = a + b;
        int diff = a - b;
        int product = a * b;
        int quotient = b != 0 ? a / b : 0;
        int remainder = b != 0 ? a % b : 0;

        // Comparison operators (ensure == works, not confused with ===)
        boolean equal = (a == b);
        boolean notEqual = (a != b);
        boolean greater = (a > b);
        boolean less = (a < b);
        boolean greaterEqual = (a >= b);
        boolean lessEqual = (a <= b);

        // Logical operators
        boolean andResult = (a > 0) && (b > 0);
        boolean orResult = (a > 0) || (b > 0);
        boolean notResult = !(a > 0);

        // Bitwise operators
        int bitwiseAnd = a & b;
        int bitwiseOr = a | b;
        int bitwiseXor = a ^ b;
        int bitwiseNot = ~a;
        int leftShift = a << 1;
        int rightShift = a >> 1;
        int unsignedRightShift = a >>> 1;

        // Assignment operators
        int temp = a;
        temp += b;
        temp -= b;
        temp *= 2;
        temp /= 2;
        temp %= 3;
        temp &= 0xFF;
        temp |= 0x10;
        temp ^= 0x01;
        temp <<= 1;
        temp >>= 1;
        temp >>>= 1;

        return equal && !notEqual;
    }

    /**
     * Tests modern Java features to ensure compatibility.
     *
     * @param <T> the generic type parameter
     * @param obj generic object
     * @param list generic list
     * @return feature test result
     */
    public <T> boolean testModernJavaFeatures(T obj, List<T> list) {
        // Generics
        List<String> strings = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();

        // Lambda expressions
        list.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .forEach(System.out::println);

        // Pattern matching instanceof (JDK 16+)
        if (obj instanceof String s) {
            System.out.println("String length: " + s.length());
            return true;
        }

        // Text blocks (JDK 15+)
        String textBlock = """
            This is a text block
            with multiple lines
            """;

        return !textBlock.isEmpty();
    }

    /**
     * Tests record classes (JDK 14+).
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public record Point(int x, int y) {
        /**
         * Static method in record.
         * @return new Point at origin
         */
        public static Point origin() {
            return new Point(0, 0);
        }
    }

    /**
     * Tests sealed classes (JDK 17+).
     */
    public sealed interface Shape permits Circle, Rectangle {
        double area();
    }

    /**
     * Circle implementation of sealed interface.
     */
    public static final class Circle implements Shape {
        private final double radius;

        public Circle(double radius) {
            this.radius = radius;
        }

        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    /**
     * Rectangle implementation of sealed interface.
     */
    public static final class Rectangle implements Shape {
        private final double width, height;

        public Rectangle(double width, double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public double area() {
            return width * height;
        }
    }

    /**
     * Tests pattern matching with switch (JDK 17+).
     *
     * @param shape the shape to analyze
     * @return area description
     */
    public String testPatternMatching(Shape shape) {
        return switch (shape) {
            case Circle c -> "Circle with area: " + c.area();
            case Rectangle r -> "Rectangle with area: " + r.area();
        };
    }

    /**
     * Tests complex Java expressions and statements.
     * Ensures no syntax conflicts with MaxJ additions.
     *
     * @param data input data
     * @return processed result
     */
    public Map<String, Object> testComplexJavaExpressions(int[] data) {
        Map<String, Object> results = new HashMap<>();

        // Array operations
        int[] copy = Arrays.copyOf(data, data.length);
        Arrays.sort(copy);

        // Stream operations
        OptionalInt max = Arrays.stream(data)
            .filter(x -> x > 0)
            .max();

        // Exception handling
        try {
            int riskyOperation = data[0] / data[1];
            results.put("division", riskyOperation);
        } catch (ArithmeticException | ArrayIndexOutOfBoundsException e) {
            results.put("error", e.getMessage());
        } finally {
            results.put("completed", true);
        }

        // Annotations
        @SuppressWarnings("unchecked")
        List<String> uncheckedList = (List<String>) (List<?>) new ArrayList<Object>();

        return results;
    }

    /**
     * Tests that Java keywords are not affected by MaxJ additions.
     * Important: tests case-sensitivity (case vs CASE).
     */
    public void testJavaKeywordPreservation() {
        // Ensure lowercase 'case' still works in Java switch
        int value = 5;
        switch (value) {
            case 1:
                System.out.println("case 1");
                break;
            case 5:
                System.out.println("case 5");  // This should work normally
                break;
            default:
                System.out.println("default case");
                break;
        }

        // Ensure lowercase 'if' and 'else' still work
        if (value == 5) {
            System.out.println("Standard Java if works");
        } else {
            System.out.println("Standard Java else works");
        }

        // Test that Java 'switch' keyword is not affected by MaxJ 'SWITCH'
        String result = switch (value) {
            case 1, 2, 3 -> "low";
            case 4, 5, 6 -> "medium";
            default -> "high";
        };

        System.out.println("Switch expression result: " + result);
    }
}
