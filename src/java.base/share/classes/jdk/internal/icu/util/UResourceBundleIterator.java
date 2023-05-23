// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
* Copyright (C) 2004-2009, International Business Machines Corporation and   *
* others. All Rights Reserved.                                               *
******************************************************************************
*/

package jdk.internal.icu.util;

import java.util.NoSuchElementException;

/**
 * <p>Class for enabling iteration over UResourceBundle objects.
 * Example of use:<br>
 * <pre>
 * ICUResourceBundleIterator iterator = resB.getIterator();
 * ICUResourceBundle temp;
 * while (iterator.hasNext()) {
 *    temp = iterator.next();  
 *    int type = temp.getType();
 *    switch(type){
 *      case UResourceBundle.STRING:
 *          str = temp.getString();
 *          break;
 *      case UResourceBundle.INT:
 *          integer = temp.getInt();
 *          break;
 *     .....
 *    }
 *   // do something interesting with data collected
 * }
 * </pre>
 * @author ram
 * @stable ICU 3.8
 */
public class UResourceBundleIterator{
    private UResourceBundle bundle;
    private int index = 0;
    private int size = 0;
    /**
     * Construct a resource bundle iterator for the
     * given resource bundle
     * 
     * @param bndl The resource bundle to iterate over
     * @stable ICU 3.8
     */
    public UResourceBundleIterator(UResourceBundle bndl){
        bundle = bndl;   
        size = bundle.getSize();
    }

    /**
     * Returns the next element of this iterator if this iterator object has at least one more element to provide
     * @return the UResourceBundle object
     * @throws NoSuchElementException If there does not exist such an element.
     * @stable ICU 3.8
     */
    public UResourceBundle next()throws NoSuchElementException{
        if(index<size){
            return bundle.get(index++);
        }
        throw new NoSuchElementException();
    }
    /**
     * Returns the next String of this iterator if this iterator object has at least one more element to provide
     * @return the UResourceBundle object
     * @throws NoSuchElementException If there does not exist such an element.
     * @throws UResourceTypeMismatchException If resource has a type mismatch.
     * @stable ICU 3.8
     */
    public String nextString()throws NoSuchElementException, UResourceTypeMismatchException{
        if(index<size){
            return bundle.getString(index++);
        }  
        throw new NoSuchElementException();
    }
    
    /**
     * Resets the internal context of a resource so that iteration starts from the first element.
     * @stable ICU 3.8
     */
    public void reset(){
        //reset the internal context   
        index = 0;
    }
    
    /**
     * Checks whether the given resource has another element to iterate over.
     * @return true if there are more elements, false if there is no more elements
     * @stable ICU 3.8
     */
    public boolean hasNext(){
        return index < size;   
    }
}
