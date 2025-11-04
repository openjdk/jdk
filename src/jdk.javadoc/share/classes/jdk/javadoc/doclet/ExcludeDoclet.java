package jdk.javadoc.doclet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;

import jdk.javadoc.internal.tool.DocEnvImpl;

/**
 * A Doclet that wraps the StandardDoclet to exclude elements from the
 * generated documentation based on rules defined in the central Utils class.
 */
public class ExcludeDoclet extends StandardDoclet {
	public ExcludeDoclet() {
	}

	@Override
	public String getName() {
		return "ExcludeDoclet";
	}

	@Override
	public boolean run(DocletEnvironment environment) {
		FilteredDocletEnvironment filteredEnv = new FilteredDocletEnvironment((DocEnvImpl) environment);
		return super.run(filteredEnv);
	}

	private static class FilteredDocletEnvironment extends DocEnvImpl {
		private static final Set<String> msgs = Collections.synchronizedSet(new HashSet<>());
		private static final Set<String> excludeMethods = Set.of(
				"add", "addAsRHS", "and", "andAsRHS", "cat", "complement", "div", "divAsRHS",
				"get", "gt", "gtAsRHS", "gte", "gteAsRHS", "lt", "ltAsRHS", "lte", "lteAsRHS",
				"mul", "mulAsRHS", "neg", "or", "orAsRHS", "shiftLeft", "shiftRight",
				"sub", "subAsRHS", "ternaryIf", "xor", "xorAsRHS");

		public FilteredDocletEnvironment(DocEnvImpl delegate) {
			super(delegate.toolEnv, delegate.etable);
		}

		private boolean shouldBeExcluded(Element element) {
			boolean isHidden = getVisibility(element) == Visibility.HIDDEN;

			boolean isUndocumentedSpecialMethod = (element.getKind() == ElementKind.METHOD &&
					!element.getModifiers().contains(Modifier.STATIC) &&
					element.getEnclosingElement().toString().contains("com.maxeler.") &&
					(getDocTrees().getDocCommentTree(element) == null) &&
					excludeMethods.contains(element.getSimpleName().toString()));

			boolean result = isHidden || isUndocumentedSpecialMethod;

			if (result && msgs.add(element.toString())) {
				System.out.println("Excluding: " + element);
			}

			return result;
		}

		@Override
		public Set<Element> getIncludedElements() {
			return super.getIncludedElements().stream()
					.filter(element -> !shouldBeExcluded(element))
					.collect(Collectors.toSet());
		}

		@Override
		public Set<Element> getSpecifiedElements() {
			return super.getSpecifiedElements().stream()
					.filter(element -> !shouldBeExcluded(element))
					.collect(Collectors.toSet());
		}

		@Override
		public boolean isIncluded(Element e) {
			return super.isIncluded(e) && !shouldBeExcluded(e);
		}

		/**
		 * Custom visibility levels for a Java API element.
		 */
		public enum Visibility {
			/** Is visible externally and published. */
			PUBLISHED,
			/** Is visible externally but deprecated. */
			DEPRECATED,
			/**
			 * Is visible externally but explicitly marked not for publishing
			 * (e.g., @exclude or _name).
			 */
			HIDDEN,
			/** Is not visible externally (e.g., private, package-private). */
			INTERNAL
		}

		/**
		 * Determines the custom visibility of any program element based on modifiers,
		 * tags, and naming conventions.
		 *
		 * @param e            The element to check.
		 * @return The calculated visibility of the element.
		 */
		private Visibility getVisibility(Element e) {
			Set<Modifier> modifiers = e.getModifiers();
			if (!modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.PROTECTED)) {
				return Visibility.INTERNAL;
			}

			if (getElementUtils().isDeprecated(e)) {
				return Visibility.DEPRECATED;
			}

			if (hasExcludeTag(e)) {
				return Visibility.HIDDEN;
			}

			Element pkg = e;
			while (pkg != null && pkg.getKind() != ElementKind.PACKAGE) {
				pkg = pkg.getEnclosingElement();
			}
			if (pkg != null && hasExcludeTag(pkg)) {
				return Visibility.HIDDEN;
			}

			if (e.getSimpleName().toString().startsWith("_")) {
				return Visibility.HIDDEN;
			}

			if (e.getKind().isClass() || e.getKind().isInterface()) {
				Element current = e.getEnclosingElement();
				while (current instanceof TypeElement) {
					if (current.getSimpleName().toString().startsWith("_")) {
						return Visibility.HIDDEN;
					}
					current = current.getEnclosingElement();
				}
			}

			// If no other rules apply, the element is considered published.
			return Visibility.PUBLISHED;
		}

		/**
		 * Robustly checks if an element has an @exclude tag in its Javadoc.
		 */
		private boolean hasExcludeTag(Element element) {
			DocCommentTree docCommentTree = getDocTrees().getDocCommentTree(element);
			if (docCommentTree == null) {
				return false;
			}

			return docCommentTree.getBlockTags().stream()
					.filter(tag -> tag.getKind() == DocTree.Kind.UNKNOWN_BLOCK_TAG)
					.map(UnknownBlockTagTree.class::cast)
					.anyMatch(tag -> tag.getTagName().equalsIgnoreCase("exclude"));
		}
	}
}
