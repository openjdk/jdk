package com.sun.org.apache.xerces.internal.xinclude;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponent;
import com.sun.org.apache.xerces.internal.xni.parser.XMLDocumentFilter;



public interface XPointerSchema extends XMLComponent, XMLDocumentFilter{

    /**
     * set the Schema Name  eg element , xpointer
     */
    public void setXPointerSchemaName(String schemaName);

    /**
     * Return  Schema Name  eg element , xpointer
     *
     */
    public String getXpointerSchemaName();

    /**
     * Parent Contenhandler for the this contenthandler.
     * // not sure about the parameter type. It can be Contenthandler instead of Object type.
     */
    public void setParent(Object parent);

    /**
     * return the Parent Contenthandler
     */
    public Object getParent();

    /**
     * Content of the XPointer Schema. Xpath to be resolved.
     */
    public void setXPointerSchemaPointer(String content);

    /**
     * Return the XPointer Schema.
     */
    public String getXPointerSchemaPointer();

    public boolean isSubResourceIndentified();

    public void reset();

}
