package jdk.javadoc.internal.doclets.formats.html;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.NATIVE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STRICTFP;
import static javax.lang.model.element.Modifier.SYNCHRONIZED;

public class Signatures {

    static class TypeSignature {

        private final TypeElement typeElement;
        private final ClassWriterImpl classWriter;
        private final Utils utils;
        private final HtmlConfiguration configuration;

        TypeSignature(TypeElement typeElement, ClassWriterImpl classWriter) {
            this.typeElement = typeElement;
            this.classWriter = classWriter;
            this.utils = classWriter.utils;
            this.configuration = classWriter.configuration;
        }

        @SuppressWarnings("preview")
        public void addClassSignature(String modifiers, Content classInfoTree) {
            Content hr = new HtmlTree(TagName.HR);
            classInfoTree.add(hr);
            Content pre = new HtmlTree(TagName.PRE);
            classWriter.addAnnotationInfo(typeElement, pre);
            pre.add(modifiers);
            LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS_SIGNATURE, typeElement);
            //Let's not link to ourselves in the signature.
            linkInfo.linkToSelf = false;
            Content className = new StringContent(utils.getSimpleName(typeElement));
            Content parameterLinks = classWriter.getTypeParameterLinks(linkInfo);
            if (classWriter.options.linkSource()) {
                classWriter.addSrcLink(typeElement, className, pre);
                pre.add(parameterLinks);
            } else {
                Content span = HtmlTree.SPAN(HtmlStyle.typeNameLabel, className);
                span.add(parameterLinks);
                pre.add(span);
            }
            if (utils.isRecord(typeElement)) {
                pre.add(getRecordComponents());
            }
            if (!utils.isAnnotationType(typeElement)) {
                if (!utils.isInterface(typeElement)) {
                    TypeMirror superclass = utils.getFirstVisibleSuperClass(typeElement);
                    if (superclass != null) {
                        pre.add(DocletConstants.NL);
                        pre.add("extends ");
                        Content link = classWriter.getLink(new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                                superclass));
                        pre.add(link);
                    }
                }
                List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
                if (!interfaces.isEmpty()) {
                    boolean isFirst = true;
                    for (TypeMirror type : interfaces) {
                        TypeElement tDoc = utils.asTypeElement(type);
                        if (!(utils.isPublic(tDoc) || utils.isLinkable(tDoc))) {
                            continue;
                        }
                        if (isFirst) {
                            pre.add(DocletConstants.NL);
                            pre.add(utils.isInterface(typeElement) ? "extends " : "implements ");
                            isFirst = false;
                        } else {
                            pre.add(", ");
                        }
                        Content link = classWriter.getLink(new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                                type));
                        pre.add(link);
                    }
                }
            }
            List<? extends TypeMirror> permits = typeElement.getPermittedSubclasses();
            List<? extends TypeMirror> linkablePermits = permits.stream()
                    .filter(t -> utils.isLinkable(utils.asTypeElement(t)))
                    .collect(Collectors.toList());
            if (!linkablePermits.isEmpty()) {
                boolean isFirst = true;
                for (TypeMirror type : linkablePermits) {
                    TypeElement tDoc = utils.asTypeElement(type);
                    if (isFirst) {
                        pre.add(DocletConstants.NL);
                        pre.add("permits ");
                        isFirst = false;
                    } else {
                        pre.add(", ");
                    }
                    Content link = classWriter.getLink(new LinkInfoImpl(configuration,
                            LinkInfoImpl.Kind.PERMITTED_SUBCLASSES,
                            type));
                    pre.add(link);
                }
                if (linkablePermits.size() < permits.size()) {
                    Content c = new StringContent(classWriter.resources.getText("doclet.not.exhaustive"));
                    pre.add(" ");
                    pre.add(HtmlTree.SPAN(HtmlStyle.permitsNote, c));
                }
            }
            classInfoTree.add(pre);
        }

        @SuppressWarnings("preview")
        private Content getRecordComponents() {
            Content content = new ContentBuilder();
            content.add("(");
            String sep = "";
            for (RecordComponentElement e : typeElement.getRecordComponents()) {
                content.add(sep);
                classWriter.getAnnotations(e.getAnnotationMirrors(), false)
                        .forEach(a -> { content.add(a); content.add(" "); });
                Content link = classWriter.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RECORD_COMPONENT,
                        e.asType()));
                content.add(link);
                content.add(Entity.NO_BREAK_SPACE);
                content.add(e.getSimpleName());
                sep = ", ";
            }
            content.add(")");
            return content;
        }
    }

    /**
     * A content builder for member signatures.
     */
    static class MemberSignature {

        private final AbstractMemberWriter writer;
        private final Utils utils;

        private final Element element;
        private Content typeParameters;
        private Content returnType;
        private Content parameters;
        private Content exceptions;

        // Threshold for length of type parameters before switching from inline to block representation.
        private static final int TYPE_PARAMS_MAX_INLINE_LENGTH = 50;

        // Threshold for combined length of modifiers, type params and return type before breaking
        // it up with a line break before the return type.
        private static final int RETURN_TYPE_MAX_LINE_LENGTH = 50;

        /**
         * Creates a new member signature builder.
         *
         * @param element the element for which to create a signature
         */
        MemberSignature(Element element, AbstractMemberWriter writer) {
            this.element = element;
            this.writer = writer;
            this.utils = writer.utils;
        }

        /**
         * Adds the type parameters for an executable member.
         *
         * @param typeParameters the content tree containing the type parameters to add.
         * @return this instance
         */
        MemberSignature addTypeParameters(Content typeParameters) {
            this.typeParameters = typeParameters;
            return this;
        }

        /**
         * Adds the return type for an executable member.
         *
         * @param returnType the content tree containing the return type to add.
         * @return this instance
         */
        MemberSignature addReturnType(Content returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Adds the type information for a non-executable member.
         *
         * @param type the type of the member.
         * @return this instance
         */
        MemberSignature addType(TypeMirror type) {
            this.returnType = writer.writer.getLink(new LinkInfoImpl(writer.configuration, LinkInfoImpl.Kind.MEMBER, type));
            return this;
        }

        /**
         * Adds the parameter information of an executable member.
         *
         * @param paramTree the content tree containing the parameter information.
         * @return this instance
         */
        MemberSignature addParameters(Content paramTree) {
            this.parameters = paramTree;
            return this;
        }

        /**
         * Adds the exception information of an executable member.
         *
         * @param exceptionTree the content tree containing the exception information
         * @return this instance
         */
        MemberSignature addExceptions(Content exceptionTree) {
            this.exceptions = exceptionTree;
            return this;
        }

        /**
         * Returns an HTML tree containing the member signature.
         *
         * @return an HTML tree containing the member signature
         */
        Content toContent() {
            Content content = new ContentBuilder();
            // Position of last line separator.
            int lastLineSeparator = 0;

            // Annotations
            Content annotationInfo = writer.writer.getAnnotationInfo(element.getAnnotationMirrors(), true);
            if (!annotationInfo.isEmpty()) {
                content.add(HtmlTree.SPAN(HtmlStyle.annotations, annotationInfo));
                lastLineSeparator = content.charCount();
            }

            // Modifiers
            appendModifiers(content);

            // Type parameters
            if (typeParameters != null && !typeParameters.isEmpty()) {
                lastLineSeparator = appendTypeParameters(content, lastLineSeparator);
            }

            // Return type
            if (returnType != null) {
                content.add(HtmlTree.SPAN(HtmlStyle.returnType, returnType));
                content.add(Entity.NO_BREAK_SPACE);
            }

            // Name
            HtmlTree nameSpan = new HtmlTree(TagName.SPAN);
            nameSpan.setStyle(HtmlStyle.memberName);
            if (writer.options.linkSource()) {
                Content name = new StringContent(writer.name(element));
                writer.writer.addSrcLink(element, name, nameSpan);
            } else {
                nameSpan.add(writer.name(element));
            }
            content.add(nameSpan);

            // Parameters and exceptions
            if (parameters != null) {
                appendParametersAndExceptions(content, lastLineSeparator);
            }

            return HtmlTree.DIV(HtmlStyle.memberSignature, content);
        }

        /**
         * Adds the modifier for the member. The modifiers are ordered as specified
         * by <em>The Java Language Specification</em>.
         *
         * @param htmlTree the content tree to which the modifier information will be added
         */
        private void appendModifiers(Content htmlTree) {
            Set<Modifier> set = new TreeSet<>(element.getModifiers());

            // remove the ones we really don't need
            set.remove(NATIVE);
            set.remove(SYNCHRONIZED);
            set.remove(STRICTFP);

            // According to JLS, we should not be showing public modifier for
            // interface methods and fields.
            if ((utils.isField(element) || utils.isMethod(element))) {
                Element te = element.getEnclosingElement();
                if (utils.isInterface(te) || utils.isAnnotationType(te)) {
                    // Remove the implicit abstract and public modifiers
                    if (utils.isMethod(element)) {
                        set.remove(ABSTRACT);
                    }
                    set.remove(PUBLIC);
                }
            }
            if (!set.isEmpty()) {
                String mods = set.stream().map(Modifier::toString).collect(Collectors.joining(" "));
                htmlTree.add(HtmlTree.SPAN(HtmlStyle.modifiers, new StringContent(mods)))
                        .add(Entity.NO_BREAK_SPACE);
            }
        }

        /**
         * Appends the type parameter information to the HTML tree.
         *
         * @param htmlTree          the HTML tree
         * @param lastLineSeparator index of last line separator in the HTML tree
         * @return the new index of the last line separator
         */
        private int appendTypeParameters(Content htmlTree, int lastLineSeparator) {
            // Apply different wrapping strategies for type parameters
            // depending of combined length of type parameters and return type.
            int typeParamLength = typeParameters.charCount();

            if (typeParamLength >= TYPE_PARAMS_MAX_INLINE_LENGTH) {
                htmlTree.add(HtmlTree.SPAN(HtmlStyle.typeParametersLong, typeParameters));
            } else {
                htmlTree.add(HtmlTree.SPAN(HtmlStyle.typeParameters, typeParameters));
            }

            int lineLength = htmlTree.charCount() - lastLineSeparator;
            int newLastLineSeparator = lastLineSeparator;

            // sum below includes length of modifiers plus type params added above
            if (lineLength + returnType.charCount() > RETURN_TYPE_MAX_LINE_LENGTH) {
                htmlTree.add(DocletConstants.NL);
                newLastLineSeparator = htmlTree.charCount();
            } else {
                htmlTree.add(Entity.NO_BREAK_SPACE);
            }

            return newLastLineSeparator;
        }

        /**
         * Appends the parameters and exceptions information to the HTML tree.
         *
         * @param htmlTree          the HTML tree
         * @param lastLineSeparator the index of the last line separator in the HTML tree
         */
        private void appendParametersAndExceptions(Content htmlTree, int lastLineSeparator) {
            // Record current position for indentation of exceptions
            int indentSize = htmlTree.charCount() - lastLineSeparator;

            if (parameters.charCount() == 2) {
                // empty parameters are added without packing
                htmlTree.add(parameters);
            } else {
                htmlTree.add(Entity.ZERO_WIDTH_SPACE)
                        .add(HtmlTree.SPAN(HtmlStyle.parameters, parameters));
            }

            // Exceptions
            if (exceptions != null && !exceptions.isEmpty()) {
                CharSequence indent = " ".repeat(Math.max(0, indentSize + 1 - 7));
                htmlTree.add(DocletConstants.NL)
                        .add(indent)
                        .add("throws ")
                        .add(HtmlTree.SPAN(HtmlStyle.exceptions, exceptions));
            }
        }
    }
}
