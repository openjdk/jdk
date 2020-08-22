/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// External Java Accessibility links:
//
// <https://docs.oracle.com/javase/8/docs/technotes/guides/access/index.html>
// <http://www-106.ibm.com/developerworks/library/j-access/?n-j-10172>
// <http://archives.java.sun.com/archives/java-access.html> (Sun's mailing list for Java accessibility)

#import "JavaComponentAccessibility.h"

#import "sun_lwawt_macosx_CAccessibility.h"

#import <AppKit/AppKit.h>

#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>

#import <dlfcn.h>

#import "JavaAccessibilityAction.h"
#import "JavaAccessibilityUtilities.h"
#import "JavaTextAccessibility.h"
#import "ThreadUtilities.h"
#import "AWTView.h"


// these constants are duplicated in CAccessibility.java
#define JAVA_AX_ALL_CHILDREN (-1)
#define JAVA_AX_SELECTED_CHILDREN (-2)
#define JAVA_AX_VISIBLE_CHILDREN (-3)
// If the value is >=0, it's an index

static JNF_STATIC_MEMBER_CACHE(jm_getChildrenAndRoles, sjc_CAccessibility, "getChildrenAndRoles", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;IZ)[Ljava/lang/Object;");
static JNF_STATIC_MEMBER_CACHE(jm_getTableInfo, sjc_CAccessibility, "getTableInfo", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)I");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleComponent, sjc_CAccessibility, "getAccessibleComponent", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleComponent;");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleValue, sjc_CAccessibility, "getAccessibleValue", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleValue;");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleName, sjc_CAccessibility, "getAccessibleName", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleDescription, sjc_CAccessibility, "getAccessibleDescription", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");
static JNF_STATIC_MEMBER_CACHE(sjm_isFocusTraversable, sjc_CAccessibility, "isFocusTraversable", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Z");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleIndexInParent, sjc_CAccessibility, "getAccessibleIndexInParent", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)I");

static JNF_CLASS_CACHE(sjc_CAccessible, "sun/lwawt/macosx/CAccessible");

static JNF_MEMBER_CACHE(jf_ptr, sjc_CAccessible, "ptr", "J");
static JNF_STATIC_MEMBER_CACHE(sjm_getCAccessible, sjc_CAccessible, "getCAccessible", "(Ljavax/accessibility/Accessible;)Lsun/lwawt/macosx/CAccessible;");

static jobject sAccessibilityClass = NULL;

// sAttributeNamesForRoleCache holds the names of the attributes to which each java
// AccessibleRole responds (see AccessibleRole.java).
// This cache is queried before attempting to access a given attribute for a particular role.
static NSMutableDictionary *sAttributeNamesForRoleCache = nil;
static NSObject *sAttributeNamesLOCK = nil;

@interface TabGroupAccessibility : JavaComponentAccessibility {
    NSInteger _numTabs;
}

- (id)currentTabWithEnv:(JNIEnv *)env withAxContext:(jobject)axContext;
- (NSArray *)tabControlsWithEnv:(JNIEnv *)env withTabGroupAxContext:(jobject)axContext withTabCode:(NSInteger)whichTabs allowIgnored:(BOOL)allowIgnored;
- (NSArray *)contentsWithEnv:(JNIEnv *)env withTabGroupAxContext:(jobject)axContext withTabCode:(NSInteger)whichTabs allowIgnored:(BOOL)allowIgnored;
- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env;

- (NSArray *)accessibilityArrayAttributeValues:(NSString *)attribute index:(NSUInteger)index maxCount:(NSUInteger)maxCount;
- (NSArray *)accessibilityChildrenAttribute;
- (id) accessibilityTabsAttribute;
- (BOOL)accessibilityIsTabsAttributeSettable;
- (NSArray *)accessibilityContentsAttribute;
- (BOOL)accessibilityIsContentsAttributeSettable;
- (id) accessibilityValueAttribute;

@end


@interface TabGroupControlAccessibility : JavaComponentAccessibility {
    jobject fTabGroupAxContext;
}
- (id)initWithParent:(NSObject *)parent withEnv:(JNIEnv *)env withAccessible:(jobject)accessible withIndex:(jint)index withTabGroup:(jobject)tabGroup withView:(NSView *)view withJavaRole:(NSString *)javaRole;
- (jobject)tabGroup;
- (void)getActionsWithEnv:(JNIEnv *)env;

- (id)accessibilityValueAttribute;
@end


@interface ScrollAreaAccessibility : JavaComponentAccessibility {

}
- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env;
- (NSArray *)accessibilityContentsAttribute;
- (BOOL)accessibilityIsContentsAttributeSettable;
- (id)accessibilityVerticalScrollBarAttribute;
- (BOOL)accessibilityIsVerticalScrollBarAttributeSettable;
- (id)accessibilityHorizontalScrollBarAttribute;
- (BOOL)accessibilityIsHorizontalScrollBarAttributeSettable;
@end

@interface TableAccessibility : JavaComponentAccessibility {

}
- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env;
- (NSArray *)accessibilityRowsAttribute;
- (NSArray *)accessibilityColumnsAttribute;
@end


@implementation JavaComponentAccessibility

- (NSString *)description
{
    return [NSString stringWithFormat:@"%@(title:'%@', desc:'%@', value:'%@')", [self accessibilityRoleAttribute],
        [self accessibilityTitleAttribute], [self accessibilityRoleDescriptionAttribute], [self accessibilityValueAttribute]];
}

- (id)initWithParent:(NSObject *)parent withEnv:(JNIEnv *)env withAccessible:(jobject)accessible withIndex:(jint)index withView:(NSView *)view withJavaRole:(NSString *)javaRole
{
    self = [super init];
    if (self)
    {
        fParent = [parent retain];
        fView = [view retain];
        fJavaRole = [javaRole retain];

        fAccessible = (*env)->NewWeakGlobalRef(env, accessible);
        (*env)->ExceptionClear(env); // in case of OOME
        jobject jcomponent = [(AWTView *)fView awtComponent:env];
        fComponent = (*env)->NewWeakGlobalRef(env, jcomponent);
        (*env)->DeleteLocalRef(env, jcomponent);

        fIndex = index;

        fActions = nil;
        fActionsLOCK = [[NSObject alloc] init];
    }
    return self;
}

- (void)unregisterFromCocoaAXSystem
{
    AWT_ASSERT_APPKIT_THREAD;
    static dispatch_once_t initialize_unregisterUniqueId_once;
    static void (*unregisterUniqueId)(id);
    dispatch_once(&initialize_unregisterUniqueId_once, ^{
        void *jrsFwk = dlopen("/System/Library/Frameworks/JavaVM.framework/Frameworks/JavaRuntimeSupport.framework/JavaRuntimeSupport", RTLD_LAZY | RTLD_LOCAL);
        unregisterUniqueId = dlsym(jrsFwk, "JRSAccessibilityUnregisterUniqueIdForUIElement");
    });
    if (unregisterUniqueId) unregisterUniqueId(self);
}

- (void)dealloc
{
    [self unregisterFromCocoaAXSystem];

    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];

    (*env)->DeleteWeakGlobalRef(env, fAccessible);
    fAccessible = NULL;

    (*env)->DeleteWeakGlobalRef(env, fComponent);
    fComponent = NULL;

    [fParent release];
    fParent = nil;

    [fNSRole release];
    fNSRole = nil;

    [fJavaRole release];
    fJavaRole = nil;

    [fView release];
    fView = nil;

    [fActions release];
    fActions = nil;

    [fActionsLOCK release];
    fActionsLOCK = nil;

    [super dealloc];
}

- (void)postValueChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, NSAccessibilityValueChangedNotification);
}

- (void)postSelectedTextChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, NSAccessibilitySelectedTextChangedNotification);
}

- (void)postSelectionChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, NSAccessibilitySelectedChildrenChangedNotification);
}

-(void)postTitleChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, NSAccessibilityTitleChangedNotification);
}

- (void)postMenuOpened
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, (NSString *)kAXMenuOpenedNotification);
}

- (void)postMenuClosed
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, (NSString *)kAXMenuClosedNotification);
}

- (void)postMenuItemSelected
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification(self, (NSString *)kAXMenuItemSelectedNotification);
}

- (BOOL)isEqual:(id)anObject
{
    if (![anObject isKindOfClass:[self class]]) return NO;
    JavaComponentAccessibility *accessibility = (JavaComponentAccessibility *)anObject;

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    return (*env)->IsSameObject(env, accessibility->fAccessible, fAccessible);
}

- (BOOL)isAccessibleWithEnv:(JNIEnv *)env forAccessible:(jobject)accessible
{
    return (*env)->IsSameObject(env, fAccessible, accessible);
}

+ (void)initialize
{
    if (sAttributeNamesForRoleCache == nil) {
        sAttributeNamesLOCK = [[NSObject alloc] init];
        sAttributeNamesForRoleCache = [[NSMutableDictionary alloc] initWithCapacity:60];
    }

    if (sRoles == nil) {
        initializeRoles();
    }

    if (sAccessibilityClass == NULL) {
        JNF_STATIC_MEMBER_CACHE(jm_getAccessibility, sjc_CAccessibility, "getAccessibility", "([Ljava/lang/String;)Lsun/lwawt/macosx/CAccessibility;");

#ifdef JAVA_AX_NO_IGNORES
        NSArray *ignoredKeys = [NSArray array];
#else
        NSArray *ignoredKeys = [sRoles allKeysForObject:JavaAccessibilityIgnore];
#endif
        jobjectArray result = NULL;
        jsize count = [ignoredKeys count];

        JNIEnv *env = [ThreadUtilities getJNIEnv];

        static JNF_CLASS_CACHE(jc_String, "java/lang/String");
        result = JNFNewObjectArray(env, &jc_String, count);
        if (!result) {
            NSLog(@"In %s, can't create Java array of String objects", __FUNCTION__);
            return;
        }

        NSInteger i;
        for (i = 0; i < count; i++) {
            jstring jString = JNFNSToJavaString(env, [ignoredKeys objectAtIndex:i]);
            (*env)->SetObjectArrayElement(env, result, i, jString);
            (*env)->DeleteLocalRef(env, jString);
        }

        sAccessibilityClass = JNFCallStaticObjectMethod(env, jm_getAccessibility, result); // AWT_THREADING Safe (known object)
    }
}

+ (void)postFocusChanged:(id)message
{
    AWT_ASSERT_APPKIT_THREAD;
    NSAccessibilityPostNotification([NSApp accessibilityFocusedUIElement], NSAccessibilityFocusedUIElementChangedNotification);
}

+ (jobject) getCAccessible:(jobject)jaccessible withEnv:(JNIEnv *)env {
    if (JNFIsInstanceOf(env, jaccessible, &sjc_CAccessible)) {
        return jaccessible;
    } else if (JNFIsInstanceOf(env, jaccessible, &sjc_Accessible)) {
        return JNFCallStaticObjectMethod(env, sjm_getCAccessible, jaccessible);
    }
    return NULL;
}

+ (NSArray *)childrenOfParent:(JavaComponentAccessibility *)parent withEnv:(JNIEnv *)env withChildrenCode:(NSInteger)whichChildren allowIgnored:(BOOL)allowIgnored
{
    if (parent->fAccessible == NULL) return nil;
    jobjectArray jchildrenAndRoles = (jobjectArray)JNFCallStaticObjectMethod(env, jm_getChildrenAndRoles, parent->fAccessible, parent->fComponent, whichChildren, allowIgnored); // AWT_THREADING Safe (AWTRunLoop)
    if (jchildrenAndRoles == NULL) return nil;

    jsize arrayLen = (*env)->GetArrayLength(env, jchildrenAndRoles);
    NSMutableArray *children = [NSMutableArray arrayWithCapacity:arrayLen/2]; //childrenAndRoles array contains two elements (child, role) for each child

    NSInteger i;
    NSUInteger childIndex = (whichChildren >= 0) ? whichChildren : 0; // if we're getting one particular child, make sure to set its index correctly
    for(i = 0; i < arrayLen; i+=2)
    {
        jobject /* Accessible */ jchild = (*env)->GetObjectArrayElement(env, jchildrenAndRoles, i);
        jobject /* String */ jchildJavaRole = (*env)->GetObjectArrayElement(env, jchildrenAndRoles, i+1);

        NSString *childJavaRole = nil;
        if (jchildJavaRole != NULL) {
            jobject jkey = JNFGetObjectField(env, jchildJavaRole, sjf_key);
            childJavaRole = JNFJavaToNSString(env, jkey);
            (*env)->DeleteLocalRef(env, jkey);
        }

        JavaComponentAccessibility *child = [self createWithParent:parent accessible:jchild role:childJavaRole index:childIndex withEnv:env withView:parent->fView];

        (*env)->DeleteLocalRef(env, jchild);
        (*env)->DeleteLocalRef(env, jchildJavaRole);

        [children addObject:child];
        childIndex++;
    }
    (*env)->DeleteLocalRef(env, jchildrenAndRoles);

    return children;
}

+ (JavaComponentAccessibility *)createWithAccessible:(jobject)jaccessible withEnv:(JNIEnv *)env withView:(NSView *)view
{
    JavaComponentAccessibility *ret = nil;
    jobject jcomponent = [(AWTView *)view awtComponent:env];
    jint index = JNFCallStaticIntMethod(env, sjm_getAccessibleIndexInParent, jaccessible, jcomponent);
    if (index >= 0) {
      NSString *javaRole = getJavaRole(env, jaccessible, jcomponent);
      ret = [self createWithAccessible:jaccessible role:javaRole index:index withEnv:env withView:view];
    }
    (*env)->DeleteLocalRef(env, jcomponent);
    return ret;
}

+ (JavaComponentAccessibility *) createWithAccessible:(jobject)jaccessible role:(NSString *)javaRole index:(jint)index withEnv:(JNIEnv *)env withView:(NSView *)view
{
    return [self createWithParent:nil accessible:jaccessible role:javaRole index:index withEnv:env withView:view];
}

+ (JavaComponentAccessibility *) createWithParent:(JavaComponentAccessibility *)parent accessible:(jobject)jaccessible role:(NSString *)javaRole index:(jint)index withEnv:(JNIEnv *)env withView:(NSView *)view
{
    // try to fetch the jCAX from Java, and return autoreleased
    jobject jCAX = [JavaComponentAccessibility getCAccessible:jaccessible withEnv:env];
    if (jCAX == NULL) return nil;
    JavaComponentAccessibility *value = (JavaComponentAccessibility *) jlong_to_ptr(JNFGetLongField(env, jCAX, jf_ptr));
    if (value != nil) {
        (*env)->DeleteLocalRef(env, jCAX);
        return [[value retain] autorelease];
    }

    // otherwise, create a new instance
    JavaComponentAccessibility *newChild = nil;
    if ([javaRole isEqualToString:@"pagetablist"]) {
        newChild = [TabGroupAccessibility alloc];
    } else if ([javaRole isEqualToString:@"table"]) {
        newChild = [TableAccessibility alloc];
    } else if ([javaRole isEqualToString:@"scrollpane"]) {
        newChild = [ScrollAreaAccessibility alloc];
    } else {
        NSString *nsRole = [sRoles objectForKey:javaRole];
        if ([nsRole isEqualToString:NSAccessibilityStaticTextRole] || [nsRole isEqualToString:NSAccessibilityTextAreaRole] || [nsRole isEqualToString:NSAccessibilityTextFieldRole]) {
            newChild = [JavaTextAccessibility alloc];
        } else {
            newChild = [JavaComponentAccessibility alloc];
        }
    }

    // must init freshly -alloc'd object
    [newChild initWithParent:parent withEnv:env withAccessible:jCAX withIndex:index withView:view withJavaRole:javaRole]; // must init new instance

    // If creating a JPopupMenu (not a combobox popup list) need to fire menuOpened.
    // This is the only way to know if the menu is opening; visible state change
    // can't be caught because the listeners are not set up in time.
    if ( [javaRole isEqualToString:@"popupmenu"] &&
         ![[parent javaRole] isEqualToString:@"combobox"] ) {
        [newChild postMenuOpened];
    }

    // must hard retain pointer poked into Java object
    [newChild retain];
    JNFSetLongField(env, jCAX, jf_ptr, ptr_to_jlong(newChild));
    (*env)->DeleteLocalRef(env, jCAX);

    // return autoreleased instance
    return [newChild autorelease];
}

- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env
{
    static JNF_STATIC_MEMBER_CACHE(jm_getInitialAttributeStates, sjc_CAccessibility, "getInitialAttributeStates", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)[Z");

    NSMutableArray *attributeNames = [NSMutableArray arrayWithCapacity:20];
    [attributeNames retain];

    // all elements respond to parent, role, role description, window, topLevelUIElement, help
    [attributeNames addObject:NSAccessibilityParentAttribute];
    [attributeNames addObject:NSAccessibilityRoleAttribute];
    [attributeNames addObject:NSAccessibilityRoleDescriptionAttribute];
    [attributeNames addObject:NSAccessibilityHelpAttribute];

    // cmcnote: AXMenu usually doesn't respond to window / topLevelUIElement. But menus within a Java app's window
    // probably should. Should we use some role other than AXMenu / AXMenuBar for Java menus?
    [attributeNames addObject:NSAccessibilityWindowAttribute];
    [attributeNames addObject:NSAccessibilityTopLevelUIElementAttribute];

    // set accessible subrole
    NSString *javaRole = [self javaRole];
    if (javaRole != nil && [javaRole isEqualToString:@"passwordtext"]) {
        //cmcnote: should turn this into a constant
        [attributeNames addObject:NSAccessibilitySubroleAttribute];
    }

    // Get all the other accessibility attributes states we need in one swell foop.
    // javaRole isn't pulled in because we need protected access to AccessibleRole.key
    jbooleanArray attributeStates = (jbooleanArray)JNFCallStaticObjectMethod(env, jm_getInitialAttributeStates, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (attributeStates == NULL) return nil;
    jboolean *attributeStatesArray = (*env)->GetBooleanArrayElements(env, attributeStates, 0);
    if (attributeStatesArray == NULL) {
        // Note: Java will not be on the stack here so a java exception can't happen and no need to call ExceptionCheck.
        NSLog(@"%s failed calling GetBooleanArrayElements", __FUNCTION__);
        return nil;
    }

    // if there's a component, it can be enabled and it has a size/position
    if (attributeStatesArray[0]) {
        [attributeNames addObject:NSAccessibilityEnabledAttribute];
        [attributeNames addObject:NSAccessibilitySizeAttribute];
        [attributeNames addObject:NSAccessibilityPositionAttribute];
    }

    // According to javadoc, a component that is focusable will return true from isFocusTraversable,
    // as well as having AccessibleState.FOCUSABLE in it's AccessibleStateSet.
    // We use the former heuristic; if the component focus-traversable, add a focused attribute
    // See also: accessibilityIsFocusedAttributeSettable
    if (attributeStatesArray[1])
    {
        [attributeNames addObject:NSAccessibilityFocusedAttribute];
    }

    // if it's a pagetab / radiobutton, it has a value but no min/max value.
    // if it is a slider, supplying only the value makes it to voice out the value instead of percentages
    BOOL hasAxValue = attributeStatesArray[2];
    if ([javaRole isEqualToString:@"pagetab"] || [javaRole isEqualToString:@"radiobutton"] || [javaRole isEqualToString:@"slider"]) {
        [attributeNames addObject:NSAccessibilityValueAttribute];
    } else {
        // if not a pagetab/radio button, and it has a value, it has a min/max/current value.
        if (hasAxValue) {
            // er, it has a min/max/current value if it's not a button.
            // See AppKit/NSButtonCellAccessibility.m
            if (![javaRole isEqualToString:@"pushbutton"]) {
                //cmcnote: make this (and "passwordtext") constants instead of magic strings
                [attributeNames addObject:NSAccessibilityMinValueAttribute];
                [attributeNames addObject:NSAccessibilityMaxValueAttribute];
                [attributeNames addObject:NSAccessibilityValueAttribute];
            }
        }
    }

    // does it have an orientation?
    if (attributeStatesArray[4]) {
        [attributeNames addObject:NSAccessibilityOrientationAttribute];
    }

    // name
    if (attributeStatesArray[5]) {
        [attributeNames addObject:NSAccessibilityTitleAttribute];
    }

    // children
    if (attributeStatesArray[6]) {
        [attributeNames addObject:NSAccessibilityChildrenAttribute];
        if ([javaRole isEqualToString:@"list"]
                || [javaRole isEqualToString:@"table"]
                || [javaRole isEqualToString:@"pagetablist"]) {
            [attributeNames addObject:NSAccessibilitySelectedChildrenAttribute];
            [attributeNames addObject:NSAccessibilityVisibleChildrenAttribute];
        }
        // Just above, the below mentioned support has been added back in for lists.
        // However, the following comments may still be useful for future fixes.
//        [attributeNames addObject:NSAccessibilitySelectedChildrenAttribute];
//        [attributeNames addObject:NSAccessibilityVisibleChildrenAttribute];
                //According to AXRoles.txt:
                //VisibleChildren: radio group, list, row, table row subrole
                //SelectedChildren: list
    }

    // Cleanup
    (*env)->ReleaseBooleanArrayElements(env, attributeStates, attributeStatesArray, JNI_ABORT);

    return attributeNames;
}

- (NSDictionary *)getActions:(JNIEnv *)env
{
    @synchronized(fActionsLOCK) {
        if (fActions == nil) {
            fActions = [[NSMutableDictionary alloc] initWithCapacity:3];
            [self getActionsWithEnv:env];
        }
    }

    return fActions;
}

- (void)getActionsWithEnv:(JNIEnv *)env
{
    static JNF_STATIC_MEMBER_CACHE(jm_getAccessibleAction, sjc_CAccessibility, "getAccessibleAction", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleAction;");

    // On MacOSX, text doesn't have actions, in java it does.
    // cmcnote: NOT TRUE - Editable text has AXShowMenu. Textfields have AXConfirm. Static text has no actions.
    jobject axAction = JNFCallStaticObjectMethod(env, jm_getAccessibleAction, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axAction != NULL) {
        //+++gdb NOTE: In MacOSX, there is just a single Action, not multiple. In java,
        //  the first one seems to be the most basic, so this will be used.
        // cmcnote: NOT TRUE - Sometimes there are multiple actions, eg sliders have AXDecrement AND AXIncrement (radr://3893192)
        JavaAxAction *action = [[JavaAxAction alloc] initWithEnv:env withAccessibleAction:axAction withIndex:0 withComponent:fComponent];
        [fActions setObject:action forKey:[self isMenu] ? NSAccessibilityPickAction : NSAccessibilityPressAction];
        [action release];
        (*env)->DeleteLocalRef(env, axAction);
    }
}

- (jobject)axContextWithEnv:(JNIEnv *)env
{
    return getAxContext(env, fAccessible, fComponent);
}

- (id)parent
{
    static JNF_CLASS_CACHE(sjc_Window, "java/awt/Window");
    static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleParent, sjc_CAccessibility, "getAccessibleParent", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/Accessible;");
    static JNF_STATIC_MEMBER_CACHE(sjm_getSwingAccessible, sjc_CAccessible, "getSwingAccessible", "(Ljavax/accessibility/Accessible;)Ljavax/accessibility/Accessible;");

    if(fParent == nil) {
        JNIEnv* env = [ThreadUtilities getJNIEnv];

        jobject jparent = JNFCallStaticObjectMethod(env, sjm_getAccessibleParent, fAccessible, fComponent);

        if (jparent == NULL) {
            fParent = fView;
        } else {
            AWTView *view = fView;
            jobject jax = JNFCallStaticObjectMethod(env, sjm_getSwingAccessible, fAccessible);

            if (JNFIsInstanceOf(env, jax, &sjc_Window)) {
                // In this case jparent is an owner toplevel and we should retrieve its own view
                view = [AWTView awtView:env ofAccessible:jparent];
            }
            if (view != nil) {
                fParent = [JavaComponentAccessibility createWithAccessible:jparent withEnv:env withView:view];
            }
            if (fParent == nil) {
                fParent = fView;
            }
            (*env)->DeleteLocalRef(env, jparent);
            (*env)->DeleteLocalRef(env, jax );
        }
        [fParent retain];
    }
    return fParent;
}

- (NSView *)view
{
    return fView;
}

- (NSWindow *)window
{
    return [[self view] window];
}

- (NSString *)javaRole
{
    if(fJavaRole == nil) {
        JNIEnv* env = [ThreadUtilities getJNIEnv];
        fJavaRole = getJavaRole(env, fAccessible, fComponent);
        [fJavaRole retain];
    }
    return fJavaRole;
}

- (BOOL)isMenu
{
    id role = [self accessibilityRoleAttribute];
    return [role isEqualToString:NSAccessibilityMenuBarRole] || [role isEqualToString:NSAccessibilityMenuRole] || [role isEqualToString:NSAccessibilityMenuItemRole];
}

- (BOOL)isSelected:(JNIEnv *)env
{
    if (fIndex == -1) {
        return NO;
    }

    return isChildSelected(env, ((JavaComponentAccessibility *)[self parent])->fAccessible, fIndex, fComponent);
}

- (BOOL)isSelectable:(JNIEnv *)env
{
    jobject axContext = [self axContextWithEnv:env];
    BOOL selectable = isSelectable(env, axContext, fComponent);
    (*env)->DeleteLocalRef(env, axContext);
    return selectable;
}

- (BOOL)isVisible:(JNIEnv *)env
{
    if (fIndex == -1) {
        return NO;
    }

    jobject axContext = [self axContextWithEnv:env];
    BOOL showing = isShowing(env, axContext, fComponent);
    (*env)->DeleteLocalRef(env, axContext);
    return showing;
}

// the array of names for each role is cached in the sAttributeNamesForRoleCache
- (NSArray *)accessibilityAttributeNames
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];

    @synchronized(sAttributeNamesLOCK) {
        NSString *javaRole = [self javaRole];
        NSArray *names =
            (NSArray *)[sAttributeNamesForRoleCache objectForKey:javaRole];
        if (names == nil) {
            names = [self initializeAttributeNamesWithEnv:env];
#ifdef JAVA_AX_DEBUG
            NSLog(@"Initializing: %s for %@: %@", __FUNCTION__, javaRole, names);
#endif
            [sAttributeNamesForRoleCache setObject:names forKey:javaRole];
        }
        // The above set of attributes is immutable per role, but some objects, if
        // they are the child of a list, need to add the selected and index attributes.
        if ([self accessibilityIsIgnored]) {
            return names;
        }
        id myParent = [self accessibilityParentAttribute];
        if ([myParent isKindOfClass:[JavaComponentAccessibility class]]) {
            NSString *parentRole = [(JavaComponentAccessibility *)myParent javaRole];

            if ([parentRole isEqualToString:@"list"]
                    || [parentRole isEqualToString:@"table"]) {
                NSMutableArray *moreNames =
                    [[NSMutableArray alloc] initWithCapacity: [names count] + 2];
                [moreNames addObjectsFromArray: names];
                [moreNames addObject:NSAccessibilitySelectedAttribute];
                [moreNames addObject:NSAccessibilityIndexAttribute];
                return moreNames;
            }
        }
        // popupmenu's return values not selected children
        if ( [javaRole isEqualToString:@"popupmenu"] &&
             ![[[self parent] javaRole] isEqualToString:@"combobox"] ) {
            NSMutableArray *moreNames =
                [[NSMutableArray alloc] initWithCapacity: [names count] + 1];
            [moreNames addObjectsFromArray: names];
            [moreNames addObject:NSAccessibilityValueAttribute];
            return moreNames;
        }
        return names;

    }  // end @synchronized

#ifdef JAVA_AX_DEBUG
    NSLog(@"Warning in %s: could not find attribute names for role: %@", __FUNCTION__, [self javaRole]);
#endif

    return nil;
}

// -- accessibility attributes --

- (BOOL)accessibilityShouldUseUniqueId {
    return YES;
}

- (BOOL)accessibilitySupportsOverriddenAttributes {
    return YES;
}


// generic getters & setters
// cmcnote: it would make more sense if these generic getters/setters were in JavaAccessibilityUtilities
- (id)accessibilityAttributeValue:(NSString *)attribute
{
    AWT_ASSERT_APPKIT_THREAD;

    // turns attribute "NSAccessibilityEnabledAttribute" into getter "accessibilityEnabledAttribute",
    // calls getter on self
    return JavaAccessibilityAttributeValue(self, attribute);
}

- (BOOL)accessibilityIsAttributeSettable:(NSString *)attribute
{
    AWT_ASSERT_APPKIT_THREAD;

    // turns attribute "NSAccessibilityParentAttribute" into selector "accessibilityIsParentAttributeSettable",
    // calls selector on self
    return JavaAccessibilityIsAttributeSettable(self, attribute);
}

- (void)accessibilitySetValue:(id)value forAttribute:(NSString *)attribute
{
    AWT_ASSERT_APPKIT_THREAD;

    if ([self accessibilityIsAttributeSettable:attribute]) {
        // turns attribute "NSAccessibilityFocusAttribute" into setter "accessibilitySetFocusAttribute",
        // calls setter on self
        JavaAccessibilitySetAttributeValue(self, attribute, value);
    }
}


// specific attributes, in alphabetical order a la
// http://developer.apple.com/documentation/Cocoa/Reference/ApplicationKit/ObjC_classic/Protocols/NSAccessibility.html

// Elements that current element contains (NSArray)
- (NSArray *)accessibilityChildrenAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    NSArray *children = [JavaComponentAccessibility childrenOfParent:self
                                                    withEnv:env
                                                    withChildrenCode:JAVA_AX_ALL_CHILDREN
                                                    allowIgnored:NO];

    NSArray *value = nil;
    if ([children count] > 0) {
        value = children;
    }

    return value;
}

- (BOOL)accessibilityIsChildrenAttributeSettable
{
    return NO;
}

- (NSUInteger)accessibilityIndexOfChild:(id)child
{
    // Only special-casing for Lists, for now. This allows lists to be accessible, fixing radr://3856139 "JLists are broken".
    // Will probably want to special-case for Tables when we implement them (radr://3096643 "Accessibility: Table").
    // In AppKit, NSMatrixAccessibility (which uses NSAccessibilityListRole), NSTableRowAccessibility, and NSTableViewAccessibility are the
    // only ones that override the default implementation in NSAccessibility
    if (![[self accessibilityRoleAttribute] isEqualToString:NSAccessibilityListRole]) {
        return [super accessibilityIndexOfChild:child];
    }

    jint returnValue =
        JNFCallStaticIntMethod( [ThreadUtilities getJNIEnv],
                                sjm_getAccessibleIndexInParent,
                                ((JavaComponentAccessibility *)child)->fAccessible,
                                ((JavaComponentAccessibility *)child)->fComponent );
    return (returnValue == -1) ? NSNotFound : returnValue;
}

// Without this optimization accessibilityChildrenAttribute is called in order to get the entire array of children.
- (NSArray *)accessibilityArrayAttributeValues:(NSString *)attribute index:(NSUInteger)index maxCount:(NSUInteger)maxCount {
    if ( (maxCount == 1) && [attribute isEqualToString:NSAccessibilityChildrenAttribute]) {
        // Children codes for ALL, SELECTED, VISIBLE are <0. If the code is >=0, we treat it as an index to a single child
        NSArray *child = [JavaComponentAccessibility childrenOfParent:self withEnv:[ThreadUtilities getJNIEnv] withChildrenCode:(NSInteger)index allowIgnored:NO];
        if ([child count] > 0) {
            return child;
        }
    }
    return [super accessibilityArrayAttributeValues:attribute index:index maxCount:maxCount];
}

// Flag indicating enabled state of element (NSNumber)
- (NSNumber *)accessibilityEnabledAttribute
{
    static JNF_STATIC_MEMBER_CACHE(jm_isEnabled, sjc_CAccessibility, "isEnabled", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Z");

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    NSNumber *value = [NSNumber numberWithBool:JNFCallStaticBooleanMethod(env, jm_isEnabled, fAccessible, fComponent)]; // AWT_THREADING Safe (AWTRunLoop)
    if (value == nil) {
        NSLog(@"WARNING: %s called on component that has no accessible component: %@", __FUNCTION__, self);
    }
    return value;
}

- (BOOL)accessibilityIsEnabledAttributeSettable
{
    return NO;
}

// Flag indicating presence of keyboard focus (NSNumber)
- (NSNumber *)accessibilityFocusedAttribute
{
    if ([self accessibilityIsFocusedAttributeSettable]) {
        return [NSNumber numberWithBool:[self isEqual:[NSApp accessibilityFocusedUIElement]]];
    }
    return [NSNumber numberWithBool:NO];
}

- (BOOL)accessibilityIsFocusedAttributeSettable
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    // According to javadoc, a component that is focusable will return true from isFocusTraversable,
    // as well as having AccessibleState.FOCUSABLE in its AccessibleStateSet.
    // We use the former heuristic; if the component focus-traversable, add a focused attribute
    // See also initializeAttributeNamesWithEnv:
    if (JNFCallStaticBooleanMethod(env, sjm_isFocusTraversable, fAccessible, fComponent)) { // AWT_THREADING Safe (AWTRunLoop)
        return YES;
    }

    return NO;
}

- (void)accessibilitySetFocusedAttribute:(id)value
{
    static JNF_STATIC_MEMBER_CACHE(jm_requestFocus, sjc_CAccessibility, "requestFocus", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)V");

    if ([(NSNumber*)value boolValue])
    {
        JNIEnv* env = [ThreadUtilities getJNIEnv];
        JNFCallStaticVoidMethod(env, jm_requestFocus, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    }
}

// Instance description, such as a help tag string (NSString)
- (NSString *)accessibilityHelpAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];

    jobject val = JNFCallStaticObjectMethod(env, sjm_getAccessibleDescription, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (val == NULL) {
        return nil;
    }
    NSString* str = JNFJavaToNSString(env, val);
    (*env)->DeleteLocalRef(env, val);
    return str;
}

- (BOOL)accessibilityIsHelpAttributeSettable
{
    return NO;
}

- (NSValue *)accessibilityIndexAttribute
{
    NSInteger index = fIndex;
    NSValue *returnValue = [NSValue value:&index withObjCType:@encode(NSInteger)];
    return returnValue;
}

- (BOOL)accessibilityIsIndexAttributeSettable
{
    return NO;
}

// Element's maximum value (id)
- (id)accessibilityMaxValueAttribute
{
    static JNF_STATIC_MEMBER_CACHE(jm_getMaximumAccessibleValue, sjc_CAccessibility, "getMaximumAccessibleValue", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/Number;");

    JNIEnv* env = [ThreadUtilities getJNIEnv];

    jobject axValue = JNFCallStaticObjectMethod(env, jm_getMaximumAccessibleValue, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axValue == NULL) {
        return [NSNumber numberWithInt:0];
    }
    NSNumber* num = JNFJavaToNSNumber(env, axValue);
    (*env)->DeleteLocalRef(env, axValue);
    return num;
}

- (BOOL)accessibilityIsMaxValueAttributeSettable
{
    return NO;
}

// Element's minimum value (id)
- (id)accessibilityMinValueAttribute
{
    static JNF_STATIC_MEMBER_CACHE(jm_getMinimumAccessibleValue, sjc_CAccessibility, "getMinimumAccessibleValue", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/Number;");

    JNIEnv* env = [ThreadUtilities getJNIEnv];

    jobject axValue = JNFCallStaticObjectMethod(env, jm_getMinimumAccessibleValue, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axValue == NULL) {
        return [NSNumber numberWithInt:0];
    }
    NSNumber* num = JNFJavaToNSNumber(env, axValue);
    (*env)->DeleteLocalRef(env, axValue);
    return num;
}

- (BOOL)accessibilityIsMinValueAttributeSettable
{
    return NO;
}

- (id)accessibilityOrientationAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];

    // cmcnote - should batch these two calls into one that returns an array of two bools, one for vertical and one for horiz
    if (isVertical(env, axContext, fComponent)) {
        (*env)->DeleteLocalRef(env, axContext);
        return NSAccessibilityVerticalOrientationValue;
    }

    if (isHorizontal(env, axContext, fComponent)) {
        (*env)->DeleteLocalRef(env, axContext);
        return NSAccessibilityHorizontalOrientationValue;
    }

    (*env)->DeleteLocalRef(env, axContext);
    return nil;
}

- (BOOL)accessibilityIsOrientationAttributeSettable
{
    return NO;
}

// Element containing current element (id)
- (id)accessibilityParentAttribute
{
    return NSAccessibilityUnignoredAncestor([self parent]);
}

- (BOOL)accessibilityIsParentAttributeSettable
{
    return NO;
}

// Screen position of element's lower-left corner in lower-left relative screen coordinates (NSValue)
- (NSValue *)accessibilityPositionAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    jobject axComponent = JNFCallStaticObjectMethod(env, sjm_getAccessibleComponent, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)

    // NSAccessibility wants the bottom left point of the object in
    // bottom left based screen coords

    // Get the java screen coords, and make a NSPoint of the bottom left of the AxComponent.
    NSSize size = getAxComponentSize(env, axComponent, fComponent);
    NSPoint point = getAxComponentLocationOnScreen(env, axComponent, fComponent);
    (*env)->DeleteLocalRef(env, axComponent);

    point.y += size.height;

    // Now make it into Cocoa screen coords.
    point.y = [[[[self view] window] screen] frame].size.height - point.y;

    return [NSValue valueWithPoint:point];
}

- (BOOL)accessibilityIsPositionAttributeSettable
{
    // In AppKit, position is only settable for a window (NSAccessibilityWindowRole). Our windows are taken care of natively, so we don't need to deal with this here
    // We *could* make use of Java's AccessibleComponent.setLocation() method. Investigate. radr://3953869
    return NO;
}

// Element type, such as NSAccessibilityRadioButtonRole (NSString). See the role table
// at http://developer.apple.com/documentation/Cocoa/Reference/ApplicationKit/ObjC_classic/Protocols/NSAccessibility.html
- (NSString *)accessibilityRoleAttribute
{
    if (fNSRole == nil) {
        NSString *javaRole = [self javaRole];
        fNSRole = [sRoles objectForKey:javaRole];
        // The sRoles NSMutableDictionary maps popupmenu to Mac's popup button.
        // JComboBox behavior currently relies on this.  However this is not the
        // proper mapping for a JPopupMenu so fix that.
        if ( [javaRole isEqualToString:@"popupmenu"] &&
             ![[[self parent] javaRole] isEqualToString:@"combobox"] ) {
             fNSRole = NSAccessibilityMenuRole;
        }
        if (fNSRole == nil) {
            // this component has assigned itself a custom AccessibleRole not in the sRoles array
            fNSRole = javaRole;
        }
        [fNSRole retain];
    }
    return fNSRole;
}

- (BOOL)accessibilityIsRoleAttributeSettable
{
    return NO;
}

// Localized, user-readable description of role, such as radio button (NSString)
- (NSString *)accessibilityRoleDescriptionAttribute
{
    // first ask AppKit for its accessible role description for a given AXRole
    NSString *value = NSAccessibilityRoleDescription([self accessibilityRoleAttribute], nil);

    if (value == nil) {
        // query java if necessary
        static JNF_STATIC_MEMBER_CACHE(jm_getAccessibleRoleDisplayString, sjc_CAccessibility, "getAccessibleRoleDisplayString", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");

        JNIEnv* env = [ThreadUtilities getJNIEnv];

        jobject axRole = JNFCallStaticObjectMethod(env, jm_getAccessibleRoleDisplayString, fAccessible, fComponent);
        if (axRole != NULL) {
            value = JNFJavaToNSString(env, axRole);
            (*env)->DeleteLocalRef(env, axRole);
        } else {
            value = @"unknown";
        }
    }

    return value;
}

- (BOOL)accessibilityIsRoleDescriptionAttributeSettable
{
    return NO;
}

// Currently selected children (NSArray)
- (NSArray *)accessibilitySelectedChildrenAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    NSArray *selectedChildren = [JavaComponentAccessibility childrenOfParent:self withEnv:env withChildrenCode:JAVA_AX_SELECTED_CHILDREN allowIgnored:NO];
    if ([selectedChildren count] > 0) {
        return selectedChildren;
    }

    return nil;
}

- (BOOL)accessibilityIsSelectedChildrenAttributeSettable
{
    return NO; // cmcnote: actually it should be. so need to write accessibilitySetSelectedChildrenAttribute also
}

- (NSNumber *)accessibilitySelectedAttribute
{
    return [NSNumber numberWithBool:[self isSelected:[ThreadUtilities getJNIEnv]]];
}

- (BOOL)accessibilityIsSelectedAttributeSettable
{
    if ([self isSelectable:[ThreadUtilities getJNIEnv]]) {
        return YES;
    } else {
        return NO;
    }
}

- (void)accessibilitySetSelectedAttribute:(id)value
{
    static JNF_STATIC_MEMBER_CACHE( jm_requestSelection,
                                    sjc_CAccessibility,
                                    "requestSelection",
                                    "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)V" );

    if ([(NSNumber*)value boolValue]) {
        JNIEnv* env = [ThreadUtilities getJNIEnv];
        JNFCallStaticVoidMethod(env, jm_requestSelection, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    }
}

// Element size (NSValue)
- (NSValue *)accessibilitySizeAttribute {
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    jobject axComponent = JNFCallStaticObjectMethod(env, sjm_getAccessibleComponent, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    NSValue* size = [NSValue valueWithSize:getAxComponentSize(env, axComponent, fComponent)];
    (*env)->DeleteLocalRef(env, axComponent);
    return size;
}

- (BOOL)accessibilityIsSizeAttributeSettable
{
    // SIZE is settable in windows if [self styleMask] & NSResizableWindowMask - but windows are heavyweight so we're ok here
    // SIZE is settable in columns if [[self tableValue] allowsColumnResizing - haven't dealt with columns yet
    return NO;
}

// Element subrole type, such as NSAccessibilityTableRowSubrole (NSString). See the subrole attribute table at
// http://developer.apple.com/documentation/Cocoa/Reference/ApplicationKit/ObjC_classic/Protocols/NSAccessibility.html
- (NSString *)accessibilitySubroleAttribute
{
    NSString *value = nil;
    if ([[self javaRole] isEqualToString:@"passwordtext"]) {
        value = NSAccessibilitySecureTextFieldSubrole;
    }
    /*
    // other subroles. TableRow and OutlineRow may be relevant to us
     NSAccessibilityCloseButtonSubrole // no, heavyweight window takes care of this
     NSAccessibilityMinimizeButtonSubrole // "
     NSAccessibilityOutlineRowSubrole    // maybe?
     NSAccessibilitySecureTextFieldSubrole // currently used
     NSAccessibilityTableRowSubrole        // maybe?
     NSAccessibilityToolbarButtonSubrole // maybe?
     NSAccessibilityUnknownSubrole
     NSAccessibilityZoomButtonSubrole    // no, heavyweight window takes care of this
     NSAccessibilityStandardWindowSubrole// no, heavyweight window takes care of this
     NSAccessibilityDialogSubrole        // maybe?
     NSAccessibilitySystemDialogSubrole    // no
     NSAccessibilityFloatingWindowSubrole // in 1.5 if we implement these, heavyweight will take care of them anyway
     NSAccessibilitySystemFloatingWindowSubrole
     NSAccessibilityIncrementArrowSubrole  // no
     NSAccessibilityDecrementArrowSubrole  // no
     NSAccessibilityIncrementPageSubrole   // no
     NSAccessibilityDecrementPageSubrole   // no
     NSAccessibilitySearchFieldSubrole    //no
     */
    return value;
}

- (BOOL)accessibilityIsSubroleAttributeSettable
{
    return NO;
}

// Title of element, such as button text (NSString)
- (NSString *)accessibilityTitleAttribute
{
    // Return empty string for labels, since their value and tile end up being the same thing and this leads to repeated text.
    if ([[self accessibilityRoleAttribute] isEqualToString:NSAccessibilityStaticTextRole]) {
        return @"";
    }

    JNIEnv* env = [ThreadUtilities getJNIEnv];

    jobject val = JNFCallStaticObjectMethod(env, sjm_getAccessibleName, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (val == NULL) {
        return nil;
    }
    NSString* str = JNFJavaToNSString(env, val);
    (*env)->DeleteLocalRef(env, val);
    return str;
}

- (BOOL)accessibilityIsTitleAttributeSettable
{
    return NO;
}

- (NSWindow *)accessibilityTopLevelUIElementAttribute
{
    return [self window];
}

- (BOOL)accessibilityIsTopLevelUIElementAttributeSettable
{
    return NO;
}

// Element's value (id)
// note that the appKit meaning of "accessibilityValue" is different from the java
// meaning of "accessibleValue", which is specific to numerical values
// (https://docs.oracle.com/javase/8/docs/api/javax/accessibility/AccessibleValue.html#setCurrentAccessibleValue-java.lang.Number-)
- (id)accessibilityValueAttribute
{
    static JNF_STATIC_MEMBER_CACHE(jm_getCurrentAccessibleValue, sjc_CAccessibility, "getCurrentAccessibleValue", "(Ljavax/accessibility/AccessibleValue;Ljava/awt/Component;)Ljava/lang/Number;");

    JNIEnv* env = [ThreadUtilities getJNIEnv];

    // Need to handle popupmenus differently.
    //
    // At least for now don't handle combo box menus.
    // This may change when later fixing issues which currently
    // exist for combo boxes, but for now the following is only
    // for JPopupMenus, not for combobox menus.
    id parent = [self parent];
    if ( [[self javaRole] isEqualToString:@"popupmenu"] &&
         ![[parent javaRole] isEqualToString:@"combobox"] ) {
        NSArray *children =
            [JavaComponentAccessibility childrenOfParent:self
                                        withEnv:env
                                        withChildrenCode:JAVA_AX_ALL_CHILDREN
                                        allowIgnored:YES];
        if ([children count] > 0) {
            // handle case of AXMenuItem
            // need to ask menu what is selected
            NSArray *selectedChildrenOfMenu =
                [self accessibilitySelectedChildrenAttribute];
            JavaComponentAccessibility *selectedMenuItem =
                [selectedChildrenOfMenu objectAtIndex:0];
            if (selectedMenuItem != nil) {
                jobject itemValue =
                        JNFCallStaticObjectMethod( env,
                                                   sjm_getAccessibleName,
                                                   selectedMenuItem->fAccessible,
                                                   selectedMenuItem->fComponent ); // AWT_THREADING Safe (AWTRunLoop)
                if (itemValue == NULL) {
                    return nil;
                }
                NSString* itemString = JNFJavaToNSString(env, itemValue);
                (*env)->DeleteLocalRef(env, itemValue);
                return itemString;
            } else {
                return nil;
            }
        }
    }

    // ask Java for the component's accessibleValue. In java, the "accessibleValue" just means a numerical value
    // a text value is taken care of in JavaTextAccessibility

    // cmcnote should coalesce these calls into one java call
    NSNumber *num = nil;
    jobject axValue = JNFCallStaticObjectMethod(env, sjm_getAccessibleValue, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axValue != NULL) {
        jobject str = JNFCallStaticObjectMethod(env, jm_getCurrentAccessibleValue, axValue, fComponent);
        if (str != NULL) {
            num = JNFJavaToNSNumber(env, str); // AWT_THREADING Safe (AWTRunLoop)
            (*env)->DeleteLocalRef(env, str);
        }
        (*env)->DeleteLocalRef(env, axValue);
    }
    if (num == nil) {
        num = [NSNumber numberWithInt:0];
    }
    return num;
}

- (BOOL)accessibilityIsValueAttributeSettable
{
    // according ot AppKit sources, in general the value attribute is not settable, except in the cases
    // of an NSScroller, an NSSplitView, and text that's both enabled & editable
    BOOL isSettable = NO;
    NSString *role = [self accessibilityRoleAttribute];

    if ([role isEqualToString:NSAccessibilityScrollBarRole] || // according to NSScrollerAccessibility
        [role isEqualToString:NSAccessibilitySplitGroupRole] ) // according to NSSplitViewAccessibility
    {
        isSettable = YES;
    }
    return isSettable;
}

- (void)accessibilitySetValueAttribute:(id)value
{
#ifdef JAVA_AX_DEBUG
    NSLog(@"Not yet implemented: %s\n", __FUNCTION__); // radr://3954018
#endif
}


// Child elements that are visible (NSArray)
- (NSArray *)accessibilityVisibleChildrenAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    NSArray *visibleChildren = [JavaComponentAccessibility childrenOfParent:self withEnv:env withChildrenCode:JAVA_AX_VISIBLE_CHILDREN allowIgnored:NO];
    if ([visibleChildren count] <= 0) return nil;
    return visibleChildren;
}

- (BOOL)accessibilityIsVisibleChildrenAttributeSettable
{
    return NO;
}

// Window containing current element (id)
- (id)accessibilityWindowAttribute
{
    return [self window];
}

- (BOOL)accessibilityIsWindowAttributeSettable
{
    return NO;
}


// -- accessibility actions --
- (NSArray *)accessibilityActionNames
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    return [[self getActions:env] allKeys];
}

- (NSString *)accessibilityActionDescription:(NSString *)action
{
    AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    return [(id <JavaAccessibilityAction>)[[self getActions:env] objectForKey:action] getDescription];
}

- (void)accessibilityPerformAction:(NSString *)action
{
    AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    [(id <JavaAccessibilityAction>)[[self getActions:env] objectForKey:action] perform];
}


// -- misc accessibility --
- (BOOL)accessibilityIsIgnored
{
#ifdef JAVA_AX_NO_IGNORES
    return NO;
#else
    return [[self accessibilityRoleAttribute] isEqualToString:JavaAccessibilityIgnore];
#endif /* JAVA_AX_NO_IGNORES */
}

- (id)accessibilityHitTest:(NSPoint)point withEnv:(JNIEnv *)env
{
    static JNF_CLASS_CACHE(jc_Container, "java/awt/Container");
    static JNF_STATIC_MEMBER_CACHE(jm_accessibilityHitTest, sjc_CAccessibility, "accessibilityHitTest", "(Ljava/awt/Container;FF)Ljavax/accessibility/Accessible;");

    // Make it into java screen coords
    point.y = [[[[self view] window] screen] frame].size.height - point.y;

    jobject jparent = fComponent;

    id value = nil;
    if (JNFIsInstanceOf(env, jparent, &jc_Container)) {
        jobject jaccessible = JNFCallStaticObjectMethod(env, jm_accessibilityHitTest, jparent, (jfloat)point.x, (jfloat)point.y); // AWT_THREADING Safe (AWTRunLoop)
        if (jaccessible != NULL) {
            value = [JavaComponentAccessibility createWithAccessible:jaccessible withEnv:env withView:fView];
            (*env)->DeleteLocalRef(env, jaccessible);
        }
    }

    if (value == nil) {
        value = self;
    }

    if ([value accessibilityIsIgnored]) {
        value = NSAccessibilityUnignoredAncestor(value);
    }

#ifdef JAVA_AX_DEBUG
    NSLog(@"%s: %@", __FUNCTION__, value);
#endif
    return value;
}

- (id)accessibilityFocusedUIElement
{
    static JNF_STATIC_MEMBER_CACHE(jm_getFocusOwner, sjc_CAccessibility, "getFocusOwner", "(Ljava/awt/Component;)Ljavax/accessibility/Accessible;");

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    id value = nil;

    NSWindow* hostWindow = [[self->fView window] retain];
    jobject focused = JNFCallStaticObjectMethod(env, jm_getFocusOwner, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    [hostWindow release];

    if (focused != NULL) {
        if (JNFIsInstanceOf(env, focused, &sjc_Accessible)) {
            value = [JavaComponentAccessibility createWithAccessible:focused withEnv:env withView:fView];
        }
        (*env)->DeleteLocalRef(env, focused);
    }

    if (value == nil) {
        value = self;
    }
#ifdef JAVA_AX_DEBUG
    NSLog(@"%s: %@", __FUNCTION__, value);
#endif
    return value;
}

@end

/*
 * Class:     sun_lwawt_macosx_CAccessibility
 * Method:    focusChanged
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessibility_focusChanged
(JNIEnv *env, jobject jthis)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postFocusChanged:) on:[JavaComponentAccessibility class] withObject:nil waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    valueChanged
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_valueChanged
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postValueChanged) on:(JavaComponentAccessibility *)jlong_to_ptr(element) withObject:nil waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    selectedTextChanged
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_selectedTextChanged
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postSelectedTextChanged)
                     on:(JavaComponentAccessibility *)jlong_to_ptr(element)
                     withObject:nil
                     waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    selectionChanged
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_selectionChanged
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postSelectionChanged) on:(JavaComponentAccessibility *)jlong_to_ptr(element) withObject:nil waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    titleChanged
 * Signature: (I)V
 */
 JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_titleChanged
 (JNIEnv *env, jclass jklass, jlong element)
 {
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postTitleChanged) on:(JavaComponentAccessibility*)jlong_to_ptr(element) withObject:nil waitUntilDone:NO];
JNF_COCOA_EXIT(env);
 }

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    menuOpened
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_menuOpened
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postMenuOpened)
                     on:(JavaComponentAccessibility *)jlong_to_ptr(element)
                     withObject:nil
                     waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    menuClosed
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_menuClosed
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postMenuClosed)
                     on:(JavaComponentAccessibility *)jlong_to_ptr(element)
                     withObject:nil
                     waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    menuItemSelected
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_menuItemSelected
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(postMenuItemSelected)
                     on:(JavaComponentAccessibility *)jlong_to_ptr(element)
                     withObject:nil
                     waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    unregisterFromCocoaAXSystem
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_unregisterFromCocoaAXSystem
(JNIEnv *env, jclass jklass, jlong element)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(unregisterFromCocoaAXSystem) on:(JavaComponentAccessibility *)jlong_to_ptr(element) withObject:nil waitUntilDone:NO];
JNF_COCOA_EXIT(env);
}

@implementation TabGroupAccessibility

- (id)initWithParent:(NSObject *)parent withEnv:(JNIEnv *)env withAccessible:(jobject)accessible withIndex:(jint)index withView:(NSView *)view withJavaRole:(NSString *)javaRole
{
    self = [super initWithParent:parent withEnv:env withAccessible:accessible withIndex:index withView:view withJavaRole:javaRole];
    if (self) {
        _numTabs = -1; //flag for uninitialized numTabs
    }
    return self;
}

- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env
{
    NSMutableArray *names = (NSMutableArray *)[super initializeAttributeNamesWithEnv:env];

    [names addObject:NSAccessibilityTabsAttribute];
    [names addObject:NSAccessibilityContentsAttribute];
    [names addObject:NSAccessibilityValueAttribute];

    return names;
}

- (id)currentTabWithEnv:(JNIEnv *)env withAxContext:(jobject)axContext
{
    NSArray *tabs = [self tabControlsWithEnv:env withTabGroupAxContext:axContext withTabCode:JAVA_AX_ALL_CHILDREN allowIgnored:NO];

    // Looking at the JTabbedPane sources, there is always one AccessibleSelection.
    jobject selAccessible = getAxContextSelection(env, axContext, 0, fComponent);
    if (selAccessible == NULL) return nil;

    // Go through the tabs and find selAccessible
    _numTabs = [tabs count];
    JavaComponentAccessibility *aTab;
    NSInteger i;
    for (i = 0; i < _numTabs; i++) {
        aTab = (JavaComponentAccessibility *)[tabs objectAtIndex:i];
        if ([aTab isAccessibleWithEnv:env forAccessible:selAccessible]) {
            (*env)->DeleteLocalRef(env, selAccessible);
            return aTab;
        }
    }
    (*env)->DeleteLocalRef(env, selAccessible);
    return nil;
}

- (NSArray *)tabControlsWithEnv:(JNIEnv *)env withTabGroupAxContext:(jobject)axContext withTabCode:(NSInteger)whichTabs allowIgnored:(BOOL)allowIgnored
{
    jobjectArray jtabsAndRoles = (jobjectArray)JNFCallStaticObjectMethod(env, jm_getChildrenAndRoles, fAccessible, fComponent, whichTabs, allowIgnored); // AWT_THREADING Safe (AWTRunLoop)
    if(jtabsAndRoles == NULL) return nil;

    jsize arrayLen = (*env)->GetArrayLength(env, jtabsAndRoles);
    if (arrayLen == 0) {
        (*env)->DeleteLocalRef(env, jtabsAndRoles);
        return nil;
    }
    NSMutableArray *tabs = [NSMutableArray arrayWithCapacity:(arrayLen/2)];

    // all of the tabs have the same role, so we can just find out what that is here and use it for all the tabs
    jobject jtabJavaRole = (*env)->GetObjectArrayElement(env, jtabsAndRoles, 1); // the array entries alternate between tab/role, starting with tab. so the first role is entry 1.
    if (jtabJavaRole == NULL) {
        (*env)->DeleteLocalRef(env, jtabsAndRoles);
        return nil;
    }
    jobject jkey = JNFGetObjectField(env, jtabJavaRole, sjf_key);
    NSString *tabJavaRole = JNFJavaToNSString(env, jkey);
    (*env)->DeleteLocalRef(env, jkey);

    NSInteger i;
    NSUInteger tabIndex = (whichTabs >= 0) ? whichTabs : 0; // if we're getting one particular child, make sure to set its index correctly
    for(i = 0; i < arrayLen; i+=2) {
        jobject jtab = (*env)->GetObjectArrayElement(env, jtabsAndRoles, i);
        JavaComponentAccessibility *tab = [[[TabGroupControlAccessibility alloc] initWithParent:self withEnv:env withAccessible:jtab withIndex:tabIndex withTabGroup:axContext withView:[self view] withJavaRole:tabJavaRole] autorelease];
        (*env)->DeleteLocalRef(env, jtab);
        [tabs addObject:tab];
        tabIndex++;
    }
    (*env)->DeleteLocalRef(env, jtabsAndRoles);
    return tabs;
}

- (NSArray *)contentsWithEnv:(JNIEnv *)env withTabGroupAxContext:(jobject)axContext withTabCode:(NSInteger)whichTabs allowIgnored:(BOOL)allowIgnored
{
    // Contents are the children of the selected tab.
    id currentTab = [self currentTabWithEnv:env withAxContext:axContext];
    if (currentTab == nil) return nil;

    NSArray *contents = [JavaComponentAccessibility childrenOfParent:currentTab withEnv:env withChildrenCode:whichTabs allowIgnored:allowIgnored];
    if ([contents count] <= 0) return nil;
    return contents;
}

- (id) accessibilityTabsAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    id tabs = [self tabControlsWithEnv:env withTabGroupAxContext:axContext withTabCode:JAVA_AX_ALL_CHILDREN allowIgnored:NO];
    (*env)->DeleteLocalRef(env, axContext);
    return tabs;
}

- (BOOL)accessibilityIsTabsAttributeSettable
{
    return NO; //cmcnote: not sure.
}

- (NSInteger)numTabs
{
    if (_numTabs == -1) {
        _numTabs = [[self accessibilityTabsAttribute] count];
    }
    return _numTabs;
}

- (NSArray *) accessibilityContentsAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    NSArray* cont = [self contentsWithEnv:env withTabGroupAxContext:axContext withTabCode:JAVA_AX_ALL_CHILDREN allowIgnored:NO];
    (*env)->DeleteLocalRef(env, axContext);
    return cont;
}

- (BOOL)accessibilityIsContentsAttributeSettable
{
    return NO;
}

// axValue is the currently selected tab
-(id) accessibilityValueAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    id val = [self currentTabWithEnv:env withAxContext:axContext];
    (*env)->DeleteLocalRef(env, axContext);
    return val;
}

- (BOOL)accessibilityIsValueAttributeSettable
{
    return YES;
}

- (void)accessibilitySetValueAttribute:(id)value //cmcnote: not certain this is ever actually called. investigate.
{
    // set the current tab
    NSNumber *number = (NSNumber *)value;
    if (![number boolValue]) return;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    setAxContextSelection(env, axContext, fIndex, fComponent);
    (*env)->DeleteLocalRef(env, axContext);
}

- (NSArray *)accessibilityChildrenAttribute
{
    //children = AXTabs + AXContents
    NSArray *tabs = [self accessibilityTabsAttribute];
    NSArray *contents = [self accessibilityContentsAttribute];

    NSMutableArray *children = [NSMutableArray arrayWithCapacity:[tabs count] + [contents count]];
    [children addObjectsFromArray:tabs];
    [children addObjectsFromArray:contents];

    return (NSArray *)children;
}

// Without this optimization accessibilityChildrenAttribute is called in order to get the entire array of children.
// See similar optimization in JavaComponentAccessibility. We have to extend the base implementation here, since
// children of tabs are AXTabs + AXContents
- (NSArray *)accessibilityArrayAttributeValues:(NSString *)attribute index:(NSUInteger)index maxCount:(NSUInteger)maxCount {
    NSArray *result = nil;
    if ( (maxCount == 1) && [attribute isEqualToString:NSAccessibilityChildrenAttribute]) {
        // Children codes for ALL, SELECTED, VISIBLE are <0. If the code is >=0, we treat it as an index to a single child
        JNIEnv *env = [ThreadUtilities getJNIEnv];
        jobject axContext = [self axContextWithEnv:env];

        //children = AXTabs + AXContents
        NSArray *children = [self tabControlsWithEnv:env withTabGroupAxContext:axContext withTabCode:index allowIgnored:NO]; // first look at the tabs
        if ([children count] > 0) {
            result = children;
         } else {
            children= [self contentsWithEnv:env withTabGroupAxContext:axContext withTabCode:(index-[self numTabs]) allowIgnored:NO];
            if ([children count] > 0) {
                result = children;
            }
        }
        (*env)->DeleteLocalRef(env, axContext);
    } else {
        result = [super accessibilityArrayAttributeValues:attribute index:index maxCount:maxCount];
    }
    return result;
}

@end


static BOOL ObjectEquals(JNIEnv *env, jobject a, jobject b, jobject component);

@implementation TabGroupControlAccessibility

- (id)initWithParent:(NSObject *)parent withEnv:(JNIEnv *)env withAccessible:(jobject)accessible withIndex:(jint)index withTabGroup:(jobject)tabGroup withView:(NSView *)view withJavaRole:(NSString *)javaRole
{
    self = [super initWithParent:parent withEnv:env withAccessible:accessible withIndex:index withView:view withJavaRole:javaRole];
    if (self) {
        if (tabGroup != NULL) {
            fTabGroupAxContext = JNFNewWeakGlobalRef(env, tabGroup);
        } else {
            fTabGroupAxContext = NULL;
        }
    }
    return self;
}

- (void)dealloc
{
    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];

    if (fTabGroupAxContext != NULL) {
        JNFDeleteWeakGlobalRef(env, fTabGroupAxContext);
        fTabGroupAxContext = NULL;
    }

    [super dealloc];
}

- (id)accessibilityValueAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    jobject selAccessible = getAxContextSelection(env, [self tabGroup], fIndex, fComponent);

    // Returns the current selection of the page tab list
    id val = [NSNumber numberWithBool:ObjectEquals(env, axContext, selAccessible, fComponent)];

    (*env)->DeleteLocalRef(env, selAccessible);
    (*env)->DeleteLocalRef(env, axContext);
    return val;
}

- (void)getActionsWithEnv:(JNIEnv *)env
{
    TabGroupAction *action = [[TabGroupAction alloc] initWithEnv:env withTabGroup:[self tabGroup] withIndex:fIndex withComponent:fComponent];
    [fActions setObject:action forKey:NSAccessibilityPressAction];
    [action release];
}

- (jobject)tabGroup
{
    if (fTabGroupAxContext == NULL) {
        JNIEnv* env = [ThreadUtilities getJNIEnv];
        jobject tabGroupAxContext = [(JavaComponentAccessibility *)[self parent] axContextWithEnv:env];
        fTabGroupAxContext = JNFNewWeakGlobalRef(env, tabGroupAxContext);
        (*env)->DeleteLocalRef(env, tabGroupAxContext);
    }
    return fTabGroupAxContext;
}

@end


@implementation ScrollAreaAccessibility

- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env
{
    NSMutableArray *names = (NSMutableArray *)[super initializeAttributeNamesWithEnv:env];

    [names addObject:NSAccessibilityHorizontalScrollBarAttribute];
    [names addObject:NSAccessibilityVerticalScrollBarAttribute];
    [names addObject:NSAccessibilityContentsAttribute];

    return names;
}

- (id)accessibilityHorizontalScrollBarAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    NSArray *children = [JavaComponentAccessibility childrenOfParent:self withEnv:env withChildrenCode:JAVA_AX_ALL_CHILDREN allowIgnored:YES];
    if ([children count] <= 0) return nil;

    // The scroll bars are in the children.
    JavaComponentAccessibility *aElement;
    NSEnumerator *enumerator = [children objectEnumerator];
    while ((aElement = (JavaComponentAccessibility *)[enumerator nextObject])) {
        if ([[aElement accessibilityRoleAttribute] isEqualToString:NSAccessibilityScrollBarRole]) {
            jobject elementAxContext = [aElement axContextWithEnv:env];
            if (isHorizontal(env, elementAxContext, fComponent)) {
                (*env)->DeleteLocalRef(env, elementAxContext);
                return aElement;
            }
            (*env)->DeleteLocalRef(env, elementAxContext);
        }
    }

    return nil;
}

- (BOOL)accessibilityIsHorizontalScrollBarAttributeSettable
{
    return NO;
}

- (id)accessibilityVerticalScrollBarAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    NSArray *children = [JavaComponentAccessibility childrenOfParent:self withEnv:env withChildrenCode:JAVA_AX_ALL_CHILDREN allowIgnored:YES];
    if ([children count] <= 0) return nil;

    // The scroll bars are in the children.
    NSEnumerator *enumerator = [children objectEnumerator];
    JavaComponentAccessibility *aElement;
    while ((aElement = (JavaComponentAccessibility *)[enumerator nextObject])) {
        if ([[aElement accessibilityRoleAttribute] isEqualToString:NSAccessibilityScrollBarRole]) {
            jobject elementAxContext = [aElement axContextWithEnv:env];
            if (isVertical(env, elementAxContext, fComponent)) {
                (*env)->DeleteLocalRef(env, elementAxContext);
                return aElement;
            }
            (*env)->DeleteLocalRef(env, elementAxContext);
        }
    }

    return nil;
}

- (BOOL)accessibilityIsVerticalScrollBarAttributeSettable
{
    return NO;
}

- (NSArray *)accessibilityContentsAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    NSArray *children = [JavaComponentAccessibility childrenOfParent:self withEnv:env withChildrenCode:JAVA_AX_ALL_CHILDREN allowIgnored:YES];

    if ([children count] <= 0) return nil;
    NSArray *contents = [NSMutableArray arrayWithCapacity:[children count]];

    // The scroll bars are in the children. children less the scroll bars is the contents
    NSEnumerator *enumerator = [children objectEnumerator];
    JavaComponentAccessibility *aElement;
    while ((aElement = (JavaComponentAccessibility *)[enumerator nextObject])) {
        if (![[aElement accessibilityRoleAttribute] isEqualToString:NSAccessibilityScrollBarRole]) {
            // no scroll bars in contents
            [(NSMutableArray *)contents addObject:aElement];
        }
    }

    return contents;
}

- (BOOL)accessibilityIsContentsAttributeSettable
{
    return NO;
}

@end

// these constants are duplicated in CAccessibility.java
#define JAVA_AX_ROWS (1)
#define JAVA_AX_COLS (2)

@implementation TableAccessibility

- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env
{
    NSMutableArray *names = (NSMutableArray *)[super initializeAttributeNamesWithEnv:env];

    [names addObject:NSAccessibilityRowCountAttribute];
    [names addObject:NSAccessibilityColumnCountAttribute];
    return names;
}

- (id)getTableInfo:(jint)info {
    if (fAccessible == NULL) return 0;

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    jint count = JNFCallStaticIntMethod(env, jm_getTableInfo, fAccessible,
                                        fComponent, info);
    NSNumber *index = [NSNumber numberWithInt:count];
    return index;
}


- (id)accessibilityRowCountAttribute {
    return [self getTableInfo:JAVA_AX_ROWS];
}

- (id)accessibilityColumnCountAttribute {
    return [self getTableInfo:JAVA_AX_COLS];
}
@end

/*
 * Returns Object.equals for the two items
 * This may use LWCToolkit.invokeAndWait(); don't call while holding fLock
 * and try to pass a component so the event happens on the correct thread.
 */
static JNF_CLASS_CACHE(sjc_Object, "java/lang/Object");
static BOOL ObjectEquals(JNIEnv *env, jobject a, jobject b, jobject component)
{
    static JNF_MEMBER_CACHE(jm_equals, sjc_Object, "equals", "(Ljava/lang/Object;)Z");

    if ((a == NULL) && (b == NULL)) return YES;
    if ((a == NULL) || (b == NULL)) return NO;

    if (pthread_main_np() != 0) {
        // If we are on the AppKit thread
        static JNF_CLASS_CACHE(sjc_LWCToolkit, "sun/lwawt/macosx/LWCToolkit");
        static JNF_STATIC_MEMBER_CACHE(jm_doEquals, sjc_LWCToolkit, "doEquals", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/awt/Component;)Z");
        return JNFCallStaticBooleanMethod(env, jm_doEquals, a, b, component); // AWT_THREADING Safe (AWTRunLoopMode)
    }

    return JNFCallBooleanMethod(env, a, jm_equals, b); // AWT_THREADING Safe (!appKit)
}
