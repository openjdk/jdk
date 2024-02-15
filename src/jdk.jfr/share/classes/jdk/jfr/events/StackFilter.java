package jdk.jfr.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jdk.jfr.MetadataDefinition;

/**
* Event annotation, specifies method names or classes to exclude in a stack
* trace.
* <p>
* The following example illustrates how the {@code StackFilter} annotation can
* be used to remove the {@code Logger::log} method in a stack trace:
*
* {@snippet :
* package com.example;
*
* @Name("example.LogMessage")
* @Label("Log Message")
* @StackFilter("com.example.Logger::log")
* class LogMessage extends Event {
*     @Label("Message")
*     String message;
* }
*
* public class Logger {
*
*     public static void log(String message) {
*         System.out.print(Instant.now() + " : " + message);
*         LogMessage event = new LogMessage();
*         event.message = message;
*         event.commit();
*     }
* }
* }
*
* @since 22
*/
@Target({ ElementType.TYPE })
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@MetadataDefinition
public @interface StackFilter {
    /**
     * The methods or classes that should not be part of an event stack trace.
     * <p>
     * A filter is formed by using the fully qualified class name concatenated with
     * the method name using {@code "::"} as separator, for example
     * {@code "java.lang.String::toString"}
     * <p>
     * If only the name of a class is specified, for example {@code
     * "java.lang.String"}, all methods in that class are filtered out.
     * <p>
     * Methods can't be qualified using method parameters or return types.
     * <p>
     * Instance methods belonging to an interface can't be filtered out.
     * <p>
     * Wilcards are not permitted.
     *
     * @return the method names, not {@code null}
     */
     String[] value();
}
