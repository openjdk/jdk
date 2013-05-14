
import java.util.*;
import java.lang.annotation.*;

class Test<K> { GOuter<@TC Object, String> entrySet() { return null; } }

@interface A {}
@interface B {}
@interface C {}
@interface D {}
@interface E {}
@interface F {}

@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TA {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TB {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TC {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TD {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TE {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TF {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TG {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TH {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TI {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TJ {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TK {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TL {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TM {}

@Repeatable(RTAs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTA {}
@Repeatable(RTBs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTB {}
@ContainerFor(RTA.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTAs { RTA[] value(); }
@ContainerFor(RTB.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTBs { RTB[] value(); }
@Target(value={ElementType.TYPE,ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.CONSTRUCTOR,ElementType.LOCAL_VARIABLE})
@interface Decl {}
