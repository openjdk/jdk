/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlIds;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * A taglet that represents the {@code @param} tag.
 */
public class ParamTaglet extends BaseTaglet implements InheritableTaglet {

    public enum ParamKind {
        /** Parameter of an executable element. */
        PARAMETER,
        /** State components of a record. */
        RECORD_COMPONENT,
        /** Type parameters of an executable element or type element. */
        TYPE_PARAMETER
    }

    private final Contents contents;

    ParamTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.PARAM, false, EnumSet.of(Taglet.Location.TYPE, Taglet.Location.CONSTRUCTOR, Taglet.Location.METHOD));
        contents = config.contents;
    }

    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence) {
        assert dst.getKind() == ElementKind.METHOD;
        assert tag.getKind() == DocTree.Kind.PARAM;
        var method = (ExecutableElement) dst;
        var param = (ParamTree) tag;
        // find the position of an owner parameter described by the given tag
        List<? extends Element> parameterElements;
        if (param.isTypeParameter()) {
            parameterElements = method.getTypeParameters();
        } else {
            parameterElements = method.getParameters();
        }
        Map<String, Integer> stringIntegerMap = mapNameToPosition(utils, parameterElements);
        CommentHelper ch = utils.getCommentHelper(dst);
        Integer position = stringIntegerMap.get(ch.getParameterName(param));
        if (position == null) {
            return new Output(null, null, List.of(), true);
        }
        // try to inherit description of the respective parameter in an overridden method
        try {
            var docFinder = utils.docFinder();

            Optional<Documentation> r;
            if (src != null){
                r = docFinder.search((ExecutableElement) src,
                                m -> DocFinder.Result.fromOptional(extract(utils, m, position, param.isTypeParameter())))
                        .toOptional();
            } else {
                r = docFinder.find((ExecutableElement) dst,
                                m -> DocFinder.Result.fromOptional(extract(utils, m, position, param.isTypeParameter())))
                        .toOptional();
            }
            return r.map(result -> new Output(result.paramTree, result.method, result.paramTree.getDescription(), true))
                    .orElseGet(() -> new Output(null, null, List.of(), true));
        } catch (DocFinder.NoOverriddenMethodFound e) {
            return new Output(null, null, List.of(), false);
        }
    }

    /**
     * Given a list of parameter elements, returns a name-position map.
     * @param params the list of parameters from a type or an executable member
     * @return a name-position map
     */
    private static Map<String, Integer> mapNameToPosition(Utils utils, List<? extends Element> params) {
        Map<String, Integer> result = new HashMap<>();
        int position = 0;
        for (Element e : params) {
            String name = utils.isTypeParameterElement(e)
                    ? utils.getTypeName(e.asType(), false)
                    : utils.getSimpleName(e);
            result.put(name, position);
            position++;
        }
        return result;
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        if (utils.isExecutableElement(holder)) {
            ExecutableElement member = (ExecutableElement) holder;
            Content output = convertParams(member, ParamKind.TYPE_PARAMETER,
                    utils.getTypeParamTrees(member), member.getTypeParameters(), tagletWriter);
            output.add(convertParams(member, ParamKind.PARAMETER,
                    utils.getParamTrees(member), member.getParameters(), tagletWriter));
            return output;
        } else {
            TypeElement typeElement = (TypeElement) holder;
            Content output = convertParams(typeElement, ParamKind.TYPE_PARAMETER,
                    utils.getTypeParamTrees(typeElement), typeElement.getTypeParameters(), tagletWriter);
            output.add(convertParams(typeElement, ParamKind.RECORD_COMPONENT,
                    utils.getParamTrees(typeElement), typeElement.getRecordComponents(), tagletWriter));
            return output;
        }
    }

    /**
     * Returns a {@code Content} representation of a list of {@code ParamTree}
     * of the specified kind.
     *
     * <p> This method correlates a {@code ParamTree} with a parameter
     * {@code Element} by name. Once it's done, a particular {@code ParamTree}
     * is addressed by the position (index) of the correlated {@code Element}
     * in the list of parameter elements. This is needed for documentation
     * inheritance because the corresponding parameters in the inheritance
     * hierarchy may be named differently.
     *
     * <p> This method warns about {@code @param} tags that do not map to
     * parameter elements and param tags that are duplicated. </p>
     *
     * @param kind the kind of <em>all</em> parameters in the lists
     */
    private Content convertParams(Element e,
                                  ParamKind kind,
                                  List<ParamTree> tags,
                                  List<? extends Element> parameters,
                                  TagletWriter writer) {
        Map<Integer, ParamTree> tagOfPosition = new HashMap<>();
        CommentHelper ch = utils.getCommentHelper(e);
        if (!tags.isEmpty()) {
            Map<String, Integer> positionOfName = mapNameToPosition(utils, parameters);
            for (ParamTree tag : tags) {
                String name = ch.getParameterName(tag);
                String paramName = kind == ParamKind.TYPE_PARAMETER ? "<" + name + ">" : name;
                if (!positionOfName.containsKey(name)) {
                    String key = switch (kind) {
                        case PARAMETER -> "doclet.Parameters_warn";
                        case TYPE_PARAMETER -> "doclet.TypeParameters_warn";
                        case RECORD_COMPONENT -> "doclet.RecordComponents_warn";
                    };
                    if (!config.isDocLintReferenceGroupEnabled()) {
                        messages.warning(ch.getDocTreePath(tag), key, paramName);
                    }
                }
                Integer position = positionOfName.get(name);
                if (position != null) {
                    if (tagOfPosition.containsKey(position)) {
                        String key = switch (kind) {
                            case PARAMETER -> "doclet.Parameters_dup_warn";
                            case TYPE_PARAMETER -> "doclet.TypeParameters_dup_warn";
                            case RECORD_COMPONENT -> "doclet.RecordComponents_dup_warn";
                        };
                        if (!config.isDocLintReferenceGroupEnabled()) {
                            messages.warning(ch.getDocTreePath(tag), key, paramName);
                        }
                    } else {
                        tagOfPosition.put(position, tag);
                    }
                }
            }
        }
        // Document declared parameters for which tag documentation is available
        // (either directly or inherited) in order of their declaration.
        Content result = writer.getOutputInstance();
        for (int i = 0; i < parameters.size(); i++) {
            ParamTree tag = tagOfPosition.get(i);
            if (tag != null) {
                result.add(convertParam(e, kind, writer, tag,
                        ch.getParameterName(tag), result.isEmpty()));
            } else if (utils.isMethod(e)) {
                result.add(getInheritedTagletOutput(kind, e, writer,
                        parameters.get(i), i, result.isEmpty()));
            }
        }
        if (tags.size() > tagOfPosition.size()) {
            // Generate documentation for remaining tags that do not match a declared parameter.
            // These are erroneous but we generate them anyway.
            for (ParamTree tag : tags) {
                if (!tagOfPosition.containsValue(tag)) {
                    result.add(convertParam(e, kind, writer, tag,
                            ch.getParameterName(tag), result.isEmpty()));
                }
            }
        }
        return result;
    }

    /**
     * Tries to inherit documentation for a specific parameter (element).
     * If unsuccessful, the returned content is empty.
     */
    private Content getInheritedTagletOutput(ParamKind kind,
                                             Element holder,
                                             TagletWriter writer,
                                             Element param,
                                             int position,
                                             boolean isFirst) {
        Content result = writer.getOutputInstance();
        var r = utils.docFinder().search((ExecutableElement) holder,
                        m -> DocFinder.Result.fromOptional(extract(utils, m, position, kind == ParamTaglet.ParamKind.TYPE_PARAMETER)))
                .toOptional();
        if (r.isPresent()) {
            String name = kind != ParamKind.TYPE_PARAMETER
                    ? utils.getSimpleName(param)
                    : utils.getTypeName(param.asType(), false);
            Content content = convertParam(r.get().method, kind, writer,
                    r.get().paramTree, name, isFirst);
            result.add(content);
        }
        return result;
    }

    private Content getParamHeader(ParamKind kind) {
        var header = switch (kind) {
            case PARAMETER -> contents.parameters;
            case TYPE_PARAMETER -> contents.typeParameters;
            case RECORD_COMPONENT -> contents.recordComponents;
        };
        return HtmlTree.DT(header);
    }

    private Content paramTagOutput(Element element, ParamTree paramTag, String paramName) {
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;
        var body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        // define id attributes for state components so that generated descriptions may refer to them
        boolean defineID = (element.getKind() == ElementKind.RECORD)
                && !paramTag.isTypeParameter();
        Content nameContent = Text.of(paramName);
        body.add(HtmlTree.CODE(defineID ? HtmlTree.SPAN_ID(HtmlIds.forParam(paramName), nameContent) : nameContent));
        body.add(" - ");
        List<? extends DocTree> description = ch.getDescription(paramTag);
        body.add(htmlWriter.commentTagsToContent(element, description, context.within(paramTag)));
        return HtmlTree.DD(body);
    }

    private record Documentation(ParamTree paramTree, ExecutableElement method) { }

    private static Optional<Documentation> extract(Utils utils, ExecutableElement method, Integer position, boolean typeParam) {
        var ch = utils.getCommentHelper(method);
        List<ParamTree> tags = typeParam
                ? utils.getTypeParamTrees(method)
                : utils.getParamTrees(method);
        List<? extends Element> parameters = typeParam
                ? method.getTypeParameters()
                : method.getParameters();
        var positionOfName = mapNameToPosition(utils, parameters);
        return tags.stream().filter(t -> position.equals(positionOfName.get(ch.getParameterName(t))))
                .map(t -> new Documentation(t, method)).findAny();
    }

    /**
     * Converts an individual {@code ParamTree} to {@code Content}, which is
     * prepended with the header if the parameter is first in the list.
     */
    private Content convertParam(Element e,
                                 ParamKind kind,
                                 TagletWriter writer,
                                 ParamTree paramTag,
                                 String name,
                                 boolean isFirstParam) {
        Content result = writer.getOutputInstance();
        if (isFirstParam) {
            result.add(getParamHeader(kind));
        }
        result.add(paramTagOutput(e, paramTag, name));
        return result;
    }
}
