/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.im.InputContext;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodContext;
import sun.awt.im.InputMethodAdapter;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.FocusEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.InputMethodEvent;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.peer.ComponentPeer;
import java.lang.Character.Subset;
import java.text.AttributedString;
import java.text.AttributedCharacterIterator;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import sun.util.logging.PlatformLogger;
import java.util.StringTokenizer;
import java.util.regex.Pattern;


/**
 * Input Method Adapter for XIM
 *
 * @author JavaSoft International
 */
public abstract class X11InputMethod extends InputMethodAdapter {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.X11InputMethod");
    /*
     * The following XIM* values must be the same as those defined in
     * Xlib.h
     */
    private static final int XIMReverse = (1<<0);
    private static final int XIMUnderline = (1<<1);
    private static final int XIMHighlight = (1<<2);
    private static final int XIMPrimary = (1<<5);
    private static final int XIMSecondary = (1<<6);
    private static final int XIMTertiary = (1<<7);

    /*
     * visible position values
     */
    private static final int XIMVisibleToForward = (1<<8);
    private static final int XIMVisibleToBackward = (1<<9);
    private static final int XIMVisibleCenter = (1<<10);
    private static final int XIMVisibleMask = (XIMVisibleToForward|
                                               XIMVisibleToBackward|
                                               XIMVisibleCenter);

    private Locale locale;
    private static boolean isXIMOpened = false;
    protected Container clientComponentWindow = null;
    private Component awtFocussedComponent = null;
    private Component lastXICFocussedComponent = null;
    private boolean   isLastXICActive = false;
    private boolean   isLastTemporary = false;
    private boolean   isActive = false;
    private boolean   isActiveClient = false;
    private static Map[] highlightStyles;
    private boolean disposed = false;

    //reset the XIC if necessary
    private boolean   needResetXIC = false;
    private Component needResetXICClient = null;

    // The use of compositionEnableSupported is to reduce unnecessary
    // native calls if set/isCompositionEnabled
    // throws UnsupportedOperationException.
    // It is set to false if that exception is thrown first time
    // either of the two methods are called.
    private boolean compositionEnableSupported = true;
    // The savedCompositionState indicates the composition mode when
    // endComposition or setCompositionEnabled is called. It doesn't always
    // reflect the actual composition state because it doesn't get updated
    // when the user changes the composition state through direct interaction
    // with the input method. It is used to save the composition mode when
    // focus is traversed across different client components sharing the
    // same java input context. Also if set/isCompositionEnabled are not
    // supported, it remains false.
    private boolean savedCompositionState = false;

    // variables to keep track of preedit context.
    // these variables need to be accessed within AWT_LOCK/UNLOCK
    private String committedText = null;
    private StringBuffer composedText = null;
    private IntBuffer rawFeedbacks;

    // private data (X11InputMethodData structure defined in
    // awt_InputMethod.c) for native methods
    // this structure needs to be accessed within AWT_LOCK/UNLOCK
    transient private long pData = 0; // accessed by native

    // Initialize highlight mapping table
    static {
        Map styles[] = new Map[4];
        HashMap map;

        // UNSELECTED_RAW_TEXT_HIGHLIGHT
        map = new HashMap(1);
        map.put(TextAttribute.WEIGHT,
                  TextAttribute.WEIGHT_BOLD);
        styles[0] = Collections.unmodifiableMap(map);

        // SELECTED_RAW_TEXT_HIGHLIGHT
        map = new HashMap(1);
        map.put(TextAttribute.SWAP_COLORS,
                  TextAttribute.SWAP_COLORS_ON);
        styles[1] = Collections.unmodifiableMap(map);

        // UNSELECTED_CONVERTED_TEXT_HIGHLIGHT
        map = new HashMap(1);
        map.put(TextAttribute.INPUT_METHOD_UNDERLINE,
                  TextAttribute.UNDERLINE_LOW_ONE_PIXEL);
        styles[2] = Collections.unmodifiableMap(map);

        // SELECTED_CONVERTED_TEXT_HIGHLIGHT
        map = new HashMap(1);
        map.put(TextAttribute.SWAP_COLORS,
                  TextAttribute.SWAP_COLORS_ON);
        styles[3] = Collections.unmodifiableMap(map);

        highlightStyles = styles;
    }

    static {
        initIDs();
    }

    /**
     * Initialize JNI field and method IDs for fields that may be
       accessed from C.
     */
    private static native void initIDs();

    /**
     * Constructs an X11InputMethod instance. It initializes the XIM
     * environment if it's not done yet.
     *
     * @exception AWTException if XOpenIM() failed.
     */
    public X11InputMethod() throws AWTException {
        // supports only the locale in which the VM is started
        locale = X11InputMethodDescriptor.getSupportedLocale();
        if (initXIM() == false) {
            throw new AWTException("Cannot open X Input Method");
        }
    }

    protected void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    /**
     * Invokes openIM() that invokes XOpenIM() if it's not opened yet.
     * @return  true if openXIM() is successful or it's already been opened.
     */
    private synchronized boolean initXIM() {
        if (isXIMOpened == false)
            isXIMOpened = openXIM();
        return isXIMOpened;
    }

    protected abstract boolean openXIM();

    protected boolean isDisposed() {
        return disposed;
    }

    protected abstract void setXICFocus(ComponentPeer peer,
                                    boolean value, boolean active);

    /**
     * Does nothing - this adapter doesn't use the input method context.
     *
     * @see java.awt.im.spi.InputMethod#setInputMethodContext
     */
    public void setInputMethodContext(InputMethodContext context) {
    }

    /**
     * Set locale to input. If input method doesn't support specified locale,
     * false will be returned and its behavior is not changed.
     *
     * @param lang locale to input
     * @return the true is returned when specified locale is supported.
     */
    public boolean setLocale(Locale lang) {
        if (lang.equals(locale)) {
            return true;
        }
        // special compatibility rule for Japanese and Korean
        if (locale.equals(Locale.JAPAN) && lang.equals(Locale.JAPANESE) ||
                locale.equals(Locale.KOREA) && lang.equals(Locale.KOREAN)) {
            return true;
        }
        return false;
    }

    /**
     * Returns current input locale.
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Does nothing - XIM doesn't let you specify which characters you expect.
     *
     * @see java.awt.im.spi.InputMethod#setCharacterSubsets
     */
    public void setCharacterSubsets(Subset[] subsets) {
    }

    /**
     * Dispatch event to input method. InputContext dispatch event with this
     * method. Input method set consume flag if event is consumed in
     * input method.
     *
     * @param e event
     */
    public void dispatchEvent(AWTEvent e) {
    }


    protected final void resetXICifneeded(){
        /* needResetXIC is used to indicate whether to call
           resetXIC on the active client. resetXIC will always be
           called on the passive client when endComposition is called.
        */
        if (needResetXIC && haveActiveClient() &&
            getClientComponent() != needResetXICClient){
            resetXIC();

            // needs to reset the last xic focussed component.
            lastXICFocussedComponent = null;
            isLastXICActive = false;

            needResetXICClient = null;
            needResetXIC = false;
        }
    }

    /**
     * Reset the composition state to the current composition state.
     */
    private void resetCompositionState() {
        if (compositionEnableSupported) {
            try {
                /* Restore the composition mode to the last saved composition
                   mode. */
                setCompositionEnabled(savedCompositionState);
            } catch (UnsupportedOperationException e) {
                compositionEnableSupported = false;
            }
        }
    }

    /**
     * Query and then return the current composition state.
     * @returns the composition state if isCompositionEnabled call
     * is successful. Otherwise, it returns false.
     */
    private boolean getCompositionState() {
        boolean compositionState = false;
        if (compositionEnableSupported) {
            try {
                compositionState = isCompositionEnabled();
            } catch (UnsupportedOperationException e) {
                compositionEnableSupported = false;
            }
        }
        return compositionState;
    }

    /**
     * Activate input method.
     */
    public synchronized void activate() {
        clientComponentWindow = getClientComponentWindow();
        if (clientComponentWindow == null)
            return;

        if (lastXICFocussedComponent != null){
            if (log.isLoggable(PlatformLogger.FINE)) log.fine("XICFocused {0}, AWTFocused {1}",
                                                              lastXICFocussedComponent, awtFocussedComponent);
        }

        if (pData == 0) {
            if (!createXIC()) {
                return;
            }
            disposed = false;
        }

        /*  reset input context if necessary and set the XIC focus
        */
        resetXICifneeded();
        ComponentPeer lastXICFocussedComponentPeer = null;
        ComponentPeer awtFocussedComponentPeer = getPeer(awtFocussedComponent);

        if (lastXICFocussedComponent != null) {
           lastXICFocussedComponentPeer = getPeer(lastXICFocussedComponent);
        }

        /* If the last XIC focussed component has a different peer as the
           current focussed component, change the XIC focus to the newly
           focussed component.
        */
        if (isLastTemporary || lastXICFocussedComponentPeer != awtFocussedComponentPeer ||
            isLastXICActive != haveActiveClient()) {
            if (lastXICFocussedComponentPeer != null) {
                setXICFocus(lastXICFocussedComponentPeer, false, isLastXICActive);
            }
            if (awtFocussedComponentPeer != null) {
                setXICFocus(awtFocussedComponentPeer, true, haveActiveClient());
            }
            lastXICFocussedComponent = awtFocussedComponent;
            isLastXICActive = haveActiveClient();
        }
        resetCompositionState();
        isActive = true;
    }

    protected abstract boolean createXIC();

    /**
     * Deactivate input method.
     */
    public synchronized void deactivate(boolean isTemporary) {
        boolean   isAc =  haveActiveClient();
        /* Usually as the client component, let's call it component A,
           loses the focus, this method is called. Then when another client
           component, let's call it component B,  gets the focus, activate is first called on
           the previous focused compoent which is A, then endComposition is called on A,
           deactivate is called on A again. And finally activate is called on the newly
           focused component B. Here is the call sequence.

           A loses focus               B gains focus
           -------------> deactivate A -------------> activate A -> endComposition A ->
           deactivate A -> activate B ----....

           So in order to carry the composition mode across the components sharing the same
           input context, we save it when deactivate is called so that when activate is
           called, it can be restored correctly till activate is called on the newly focused
           component. (See also sun/awt/im/InputContext and bug 6184471).
           Last note, getCompositionState should be called before setXICFocus since
           setXICFocus here sets the XIC to 0.
        */
        savedCompositionState = getCompositionState();

        if (isTemporary){
            //turn the status window off...
            turnoffStatusWindow();
        }

        /* Delay resetting the XIC focus until activate is called and the newly
           focussed component has a different peer as the last focussed component.
        */
        lastXICFocussedComponent = awtFocussedComponent;
        isLastXICActive = isAc;
        isLastTemporary = isTemporary;
        isActive = false;
    }

    /**
     * Explicitly disable the native IME. Native IME is not disabled when
     * deactivate is called.
     */
    public void disableInputMethod() {
        if (lastXICFocussedComponent != null) {
            setXICFocus(getPeer(lastXICFocussedComponent), false, isLastXICActive);
            lastXICFocussedComponent = null;
            isLastXICActive = false;

            resetXIC();
            needResetXICClient = null;
            needResetXIC = false;
        }
    }

    // implements java.awt.im.spi.InputMethod.hideWindows
    public void hideWindows() {
        // ??? need real implementation
    }

    /**
     * @see java.awt.Toolkit#mapInputMethodHighlight
     */
    public static Map mapInputMethodHighlight(InputMethodHighlight highlight) {
        int index;
        int state = highlight.getState();
        if (state == InputMethodHighlight.RAW_TEXT) {
            index = 0;
        } else if (state == InputMethodHighlight.CONVERTED_TEXT) {
            index = 2;
        } else {
            return null;
        }
        if (highlight.isSelected()) {
            index += 1;
        }
        return highlightStyles[index];
    }

    /**
     * @see sun.awt.im.InputMethodAdapter#setAWTFocussedComponent
     */
    protected void setAWTFocussedComponent(Component component) {
        if (component == null) {
            return;
        }
        if (isActive) {
            // deactivate/activate are being suppressed during a focus change -
            // this may happen when an input method window is made visible
            boolean ac = haveActiveClient();
            setXICFocus(getPeer(awtFocussedComponent), false, ac);
            setXICFocus(getPeer(component), true, ac);
        }
        awtFocussedComponent = component;
    }

    /**
     * @see sun.awt.im.InputMethodAdapter#stopListening
     */
    protected void stopListening() {
        // It is desirable to disable XIM by calling XSetICValues with
        // XNPreeditState == XIMPreeditDisable.  But Solaris 2.6 and
        // Solaris 7 do not implement this correctly without a patch,
        // so just call resetXIC here.  Prior endComposition call commits
        // the existing composed text.
        endComposition();
        // disable the native input method so that the other input
        // method could get the input focus.
        disableInputMethod();
        if (needResetXIC) {
            resetXIC();
            needResetXICClient = null;
            needResetXIC = false;
        }
    }

    /**
     * Returns the Window instance in which the client component is
     * contained. If not found, null is returned. (IS THIS POSSIBLE?)
     */
    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    private Window getClientComponentWindow() {
        Component client = getClientComponent();
        Container container;

        if (client instanceof Container) {
            container = (Container) client;
        } else {
            container = getParent(client);
        }

        while (container != null && !(container instanceof java.awt.Window)) {
            container = getParent(container);
        }
        return (Window) container;
    }

    protected abstract Container getParent(Component client);

    /**
     * Returns peer of the given client component. If the given client component
     * doesn't have peer, peer of the native container of the client is returned.
     */
    protected abstract ComponentPeer getPeer(Component client);

    /**
     * Used to protect preedit data
     */
    protected abstract void awtLock();
    protected abstract void awtUnlock();

    /**
     * Creates an input method event from the arguments given
     * and posts it on the AWT event queue. For arguments,
     * see InputMethodEvent. Called by input method.
     *
     * @see java.awt.event.InputMethodEvent#InputMethodEvent
     */
    private void postInputMethodEvent(int id,
                                      AttributedCharacterIterator text,
                                      int committedCharacterCount,
                                      TextHitInfo caret,
                                      TextHitInfo visiblePosition,
                                      long when) {
        Component source = getClientComponent();
        if (source != null) {
            InputMethodEvent event = new InputMethodEvent(source,
                id, when, text, committedCharacterCount, caret, visiblePosition);
            SunToolkit.postEvent(SunToolkit.targetToAppContext(source), (AWTEvent)event);
        }
    }

    private void postInputMethodEvent(int id,
                                      AttributedCharacterIterator text,
                                      int committedCharacterCount,
                                      TextHitInfo caret,
                                      TextHitInfo visiblePosition) {
        postInputMethodEvent(id, text, committedCharacterCount,
                             caret, visiblePosition, EventQueue.getMostRecentEventTime());
    }

    /**
     * Dispatches committed text from XIM to the awt event queue. This
     * method is invoked from the event handler in canvas.c in the
     * AWT Toolkit thread context and thus inside the AWT Lock.
     * @param   str     committed text
     * @param   long    when
     */
    // NOTE: This method may be called by privileged threads.
    //       This functionality is implemented in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void dispatchCommittedText(String str, long when) {
        if (str == null)
            return;

        if (composedText == null) {
            AttributedString attrstr = new AttributedString(str);
            postInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                 attrstr.getIterator(),
                                 str.length(),
                                 null,
                                 null,
                                 when);
        } else {
            // if there is composed text, wait until the preedit
            // callback is invoked.
            committedText = str;
        }
    }

    private void dispatchCommittedText(String str) {
        dispatchCommittedText(str, EventQueue.getMostRecentEventTime());
    }

    /**
     * Updates composed text with XIM preedit information and
     * posts composed text to the awt event queue. The args of
     * this method correspond to the XIM preedit callback
     * information. The XIM highlight attributes are translated via
     * fixed mapping (i.e., independent from any underlying input
     * method engine). This method is invoked in the AWT Toolkit
     * (X event loop) thread context and thus inside the AWT Lock.
     */
    // NOTE: This method may be called by privileged threads.
    //       This functionality is implemented in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void dispatchComposedText(String chgText,
                                           int chgStyles[],
                                           int chgOffset,
                                           int chgLength,
                                           int caretPosition,
                                           long when) {
        if (disposed) {
            return;
        }

        //Workaround for deadlock bug on solaris2.6_zh bug#4170760
        if (chgText == null
            && chgStyles == null
            && chgOffset == 0
            && chgLength == 0
            && caretPosition == 0
            && composedText == null
            && committedText == null)
            return;

        if (composedText == null) {
            // TODO: avoid reallocation of those buffers
            composedText = new StringBuffer(INITIAL_SIZE);
            rawFeedbacks = new IntBuffer(INITIAL_SIZE);
        }
        if (chgLength > 0) {
            if (chgText == null && chgStyles != null) {
                rawFeedbacks.replace(chgOffset, chgStyles);
            } else {
                if (chgLength == composedText.length()) {
                    // optimization for the special case to replace the
                    // entire previous text
                    composedText = new StringBuffer(INITIAL_SIZE);
                    rawFeedbacks = new IntBuffer(INITIAL_SIZE);
                } else {
                    if (composedText.length() > 0) {
                        if (chgOffset+chgLength < composedText.length()) {
                            String text;
                            text = composedText.toString().substring(chgOffset+chgLength,
                                                                     composedText.length());
                            composedText.setLength(chgOffset);
                            composedText.append(text);
                        } else {
                            // in case to remove substring from chgOffset
                            // to the end
                            composedText.setLength(chgOffset);
                        }
                        rawFeedbacks.remove(chgOffset, chgLength);
                    }
                }
            }
        }
        if (chgText != null) {
            composedText.insert(chgOffset, chgText);
            if (chgStyles != null)
                rawFeedbacks.insert(chgOffset, chgStyles);
        }

        if (composedText.length() == 0) {
            composedText = null;
            rawFeedbacks = null;

            // if there is any outstanding committed text stored by
            // dispatchCommittedText(), it has to be sent to the
            // client component.
            if (committedText != null) {
                dispatchCommittedText(committedText, when);
                committedText = null;
                return;
            }

            // otherwise, send null text to delete client's composed
            // text.
            postInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                 null,
                                 0,
                                 null,
                                 null,
                                 when);

            return;
        }

        // Now sending the composed text to the client
        int composedOffset;
        AttributedString inputText;

        // if there is any partially committed text, concatenate it to
        // the composed text.
        if (committedText != null) {
            composedOffset = committedText.length();
            inputText = new AttributedString(committedText + composedText);
            committedText = null;
        } else {
            composedOffset = 0;
            inputText = new AttributedString(composedText.toString());
        }

        int currentFeedback;
        int nextFeedback;
        int startOffset = 0;
        int currentOffset;
        int visiblePosition = 0;
        TextHitInfo visiblePositionInfo = null;

        rawFeedbacks.rewind();
        currentFeedback = rawFeedbacks.getNext();
        rawFeedbacks.unget();
        while ((nextFeedback = rawFeedbacks.getNext()) != -1) {
            if (visiblePosition == 0) {
                visiblePosition = nextFeedback & XIMVisibleMask;
                if (visiblePosition != 0) {
                    int index = rawFeedbacks.getOffset() - 1;

                    if (visiblePosition == XIMVisibleToBackward)
                        visiblePositionInfo = TextHitInfo.leading(index);
                    else
                        visiblePositionInfo = TextHitInfo.trailing(index);
                }
            }
            nextFeedback &= ~XIMVisibleMask;
            if (currentFeedback != nextFeedback) {
                rawFeedbacks.unget();
                currentOffset = rawFeedbacks.getOffset();
                inputText.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT,
                                       convertVisualFeedbackToHighlight(currentFeedback),
                                       composedOffset + startOffset,
                                       composedOffset + currentOffset);
                startOffset = currentOffset;
                currentFeedback = nextFeedback;
            }
        }
        currentOffset = rawFeedbacks.getOffset();
        if (currentOffset >= 0) {
            inputText.addAttribute(TextAttribute.INPUT_METHOD_HIGHLIGHT,
                                   convertVisualFeedbackToHighlight(currentFeedback),
                                   composedOffset + startOffset,
                                   composedOffset + currentOffset);
        }

        postInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                             inputText.getIterator(),
                             composedOffset,
                             TextHitInfo.leading(caretPosition),
                             visiblePositionInfo,
                             when);
    }

    /**
     * Flushes composed and committed text held in this context.
     * This method is invoked in the AWT Toolkit (X event loop) thread context
     * and thus inside the AWT Lock.
     */
    // NOTE: This method may be called by privileged threads.
    //       This functionality is implemented in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void flushText() {
        String flush = (committedText != null ? committedText : "");
        if (composedText != null) {
            flush += composedText.toString();
        }

        if (!flush.equals("")) {
            AttributedString attrstr = new AttributedString(flush);
            postInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                 attrstr.getIterator(),
                                 flush.length(),
                                 null,
                                 null,
                                 EventQueue.getMostRecentEventTime());
            composedText = null;
            committedText = null;
        }
    }

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    protected synchronized void disposeImpl() {
        disposeXIC();
        awtLock();
        composedText = null;
        committedText = null;
        rawFeedbacks = null;
        awtUnlock();
        awtFocussedComponent = null;
        lastXICFocussedComponent = null;
    }

    /**
     * Frees all X Window resources associated with this object.
     *
     * @see java.awt.im.spi.InputMethod#dispose
     */
    public final void dispose() {
        boolean call_disposeImpl = false;

        if (!disposed) {
            synchronized (this) {
                if (!disposed) {
                    disposed = call_disposeImpl = true;
                }
            }
        }

        if (call_disposeImpl) {
            disposeImpl();
        }
    }

    /**
     * Returns null.
     *
     * @see java.awt.im.spi.InputMethod#getControlObject
     */
    public Object getControlObject() {
        return null;
    }

    /**
     * @see java.awt.im.spi.InputMethod#removeNotify
     */
    public synchronized void removeNotify() {
        dispose();
    }

    /**
     * @see java.awt.im.spi.InputMethod#setCompositionEnabled(boolean)
     */
    public void setCompositionEnabled(boolean enable) {
        /* If the composition state is successfully changed, set
           the savedCompositionState to 'enable'. Otherwise, simply
           return.
           setCompositionEnabledNative may throw UnsupportedOperationException.
           Don't try to catch it since the method may be called by clients.
           Use package private mthod 'resetCompositionState' if you want the
           exception to be caught.
        */
        if (setCompositionEnabledNative(enable)) {
            savedCompositionState = enable;
        }
    }

    /**
     * @see java.awt.im.spi.InputMethod#isCompositionEnabled
     */
    public boolean isCompositionEnabled() {
        /* isCompositionEnabledNative may throw UnsupportedOperationException.
           Don't try to catch it since this method may be called by clients.
           Use package private method 'getCompositionState' if you want the
           exception to be caught.
        */
        return isCompositionEnabledNative();
    }

    /**
     * Ends any input composition that may currently be going on in this
     * context. Depending on the platform and possibly user preferences,
     * this may commit or delete uncommitted text. Any changes to the text
     * are communicated to the active component using an input method event.
     *
     * <p>
     * A text editing component may call this in a variety of situations,
     * for example, when the user moves the insertion point within the text
     * (but outside the composed text), or when the component's text is
     * saved to a file or copied to the clipboard.
     *
     */
    public void endComposition() {
        if (disposed) {
            return;
        }

        /* Before calling resetXIC, record the current composition mode
           so that it can be restored later. */
        savedCompositionState = getCompositionState();
        boolean active = haveActiveClient();
        if (active && composedText == null && committedText == null){
            needResetXIC = true;
            needResetXICClient = getClientComponent();
            return;
        }

        String text = resetXIC();
        /* needResetXIC is only set to true for active client. So passive
           client should not reset the flag to false. */
        if (active) {
            needResetXIC = false;
        }

        // Remove any existing composed text by posting an InputMethodEvent
        // with null composed text.  It would be desirable to wait for a
        // dispatchComposedText call from X input method engine, but some
        // input method does not conform to the XIM specification and does
        // not call the preedit callback to erase preedit text on calling
        // XmbResetIC.  To work around this problem, do it here by ourselves.
        awtLock();
        composedText = null;
        postInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                             null,
                             0,
                             null,
                             null);

        if (text != null && text.length() > 0) {
            dispatchCommittedText(text);
        }
        awtUnlock();

        // Restore the preedit state if it was enabled
        if (savedCompositionState) {
            resetCompositionState();
        }
    }

    /**
     * Returns a string with information about the current input method server, or null.
     * On both Linux & SunOS, the value of environment variable XMODIFIERS is
     * returned if set. Otherwise, on SunOS, $HOME/.dtprofile will be parsed
     * to find out the language service engine (atok or wnn) since there is
     * no API in Xlib which returns the information of native
     * IM server or language service and we want to try our best to return as much
     * information as possible.
     *
     * Note: This method could return null on Linux if XMODIFIERS is not set properly or
     * if any IOException is thrown.
     * See man page of XSetLocaleModifiers(3X11) for the usgae of XMODIFIERS,
     * atok12setup(1) and wnn6setup(1) for the information written to
     * $HOME/.dtprofile when you run these two commands.
     *
     */
    public String getNativeInputMethodInfo() {
        String xmodifiers = System.getenv("XMODIFIERS");
        String imInfo = null;

        // If XMODIFIERS is set, return the value
        if (xmodifiers != null) {
            int imIndex = xmodifiers.indexOf("@im=");
            if (imIndex != -1) {
                imInfo = xmodifiers.substring(imIndex + 4);
            }
        } else if (System.getProperty("os.name").startsWith("SunOS")) {
            File dtprofile = new File(System.getProperty("user.home") +
                                      "/.dtprofile");
            String languageEngineInfo = null;
            try {
                BufferedReader br = new BufferedReader(new FileReader(dtprofile));
                String line = null;

                while ( languageEngineInfo == null && (line = br.readLine()) != null) {
                    if (line.contains("atok") || line.contains("wnn")) {
                        StringTokenizer tokens =  new StringTokenizer(line);
                        while (tokens.hasMoreTokens()) {
                            String token = tokens.nextToken();
                            if (Pattern.matches("atok.*setup", token) ||
                                Pattern.matches("wnn.*setup", token)){
                                languageEngineInfo = token.substring(0, token.indexOf("setup"));
                                break;
                            }
                        }
                    }
                }

                br.close();
            } catch(IOException ioex) {
                // Since this method is provided for internal testing only,
                // we dump the stack trace for the ease of debugging.
                ioex.printStackTrace();
            }

            imInfo = "htt " + languageEngineInfo;
        }

        return imInfo;
    }


    /**
     * Performs mapping from an XIM visible feedback value to Java IM highlight.
     * @return Java input method highlight
     */
    private InputMethodHighlight convertVisualFeedbackToHighlight(int feedback) {
        InputMethodHighlight highlight;

        switch (feedback) {
        case XIMUnderline:
            highlight = InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT;
            break;
        case XIMReverse:
            highlight = InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT;
            break;
        case XIMHighlight:
            highlight = InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT;
            break;
        case XIMPrimary:
            highlight = InputMethodHighlight.UNSELECTED_CONVERTED_TEXT_HIGHLIGHT;
            break;
        case XIMSecondary:
            highlight = InputMethodHighlight.SELECTED_CONVERTED_TEXT_HIGHLIGHT;
            break;
        case XIMTertiary:
            highlight = InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT;
            break;
        default:
            highlight = InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT;
            break;
        }
        return highlight;
    }

    // initial capacity size for string buffer, etc.
    private static final int INITIAL_SIZE = 64;

    /**
     * IntBuffer is an inner class that manipulates an int array and
     * provides UNIX file io stream-like programming interfaces to
     * access it. (An alternative would be to use ArrayList which may
     * be too expensive for the work.)
     */
    private final class IntBuffer {
        private int[] intArray;
        private int size;
        private int index;

        IntBuffer(int initialCapacity) {
            intArray = new int[initialCapacity];
            size = 0;
            index = 0;
        }

        void insert(int offset, int[] values) {
            int newSize = size + values.length;
            if (intArray.length < newSize) {
                int[] newIntArray = new int[newSize * 2];
                System.arraycopy(intArray, 0, newIntArray, 0, size);
                intArray = newIntArray;
            }
            System.arraycopy(intArray, offset, intArray, offset+values.length,
                             size - offset);
            System.arraycopy(values, 0, intArray, offset, values.length);
            size += values.length;
            if (index > offset)
                index = offset;
        }

        void remove(int offset, int length) {
            if (offset + length != size)
                System.arraycopy(intArray, offset+length, intArray, offset,
                                 size - offset - length);
            size -= length;
            if (index > offset)
                index = offset;
        }

        void replace(int offset, int[] values) {
            System.arraycopy(values, 0, intArray, offset, values.length);
        }

        void removeAll() {
            size = 0;
            index = 0;
        }

        void rewind() {
            index = 0;
        }

        int getNext() {
            if (index == size)
                return -1;
            return intArray[index++];
        }

        void unget() {
            if (index != 0)
                index--;
        }

        int getOffset() {
            return index;
        }

        public String toString() {
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < size;) {
                s.append(intArray[i++]);
                if (i < size)
                    s.append(",");
            }
            return s.toString();
        }
    }

    /*
     * Native methods
     */
    protected native String resetXIC();
    private native void disposeXIC();
    private native boolean setCompositionEnabledNative(boolean enable);
    private native boolean isCompositionEnabledNative();
    private native void turnoffStatusWindow();
}
