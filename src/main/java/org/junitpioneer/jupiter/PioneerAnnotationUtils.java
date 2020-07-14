/*
 * Copyright 2015-2020 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junitpioneer.jupiter;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Pioneer-internal utility class to handle annotations.
 *
 * It uses the following terminology to describe annotations that are not
 * immediately present on an element:
 *
 * <ul>
 *     <li><em>indirectly present</em> if a supertype of the element is annotated</li>
 *     <li><em>meta-present</em> if an annotation that is present on the element is itself annotated</li>
 *     <li><em>enclosing-present</em> if an enclosing type (think opposite of
 *     		{@link org.junit.jupiter.api.Nested @Nested}) is annotated</li>
 * </ul>
 *
 * All of the above mechanisms apply recursively, meaning that, e.g., for an annotation to be
 * <em>meta-present</em> it can present on an annotation that is present on another annotation
 * that is present on the element.
 */
public class PioneerAnnotationUtils {

	private PioneerAnnotationUtils() {
		// private constructor to prevent instantiation of utility class
	}

	/**
	 * Determines whether an annotation of any of the specified {@code annotationTypes}
	 * is either <em>present</em>, <em>indirectly present</em>, <em>meta-present</em>, or
	 * <em>enclosing-present</em> on the test element (method or class) belonging to the
	 * specified {@code context}.
	 */
	public static boolean isAnyAnnotationPresent(ExtensionContext context,
			Class<? extends Annotation>... annotationTypes) {
		return Stream
				.of(annotationTypes)
				// to check for presence, we don't need all annotations - the closest ones suffice
				.map(annotationType -> findClosestEnclosingAnnotation(context, annotationType))
				.anyMatch(Optional::isPresent);
	}

	/**
	 * Determines whether an annotation of any of the specified repeatable {@code annotationTypes}
	 * is either <em>present</em>, <em>indirectly present</em>, <em>meta-present</em>, or
	 * <em>enclosing-present</em> on the test element (method or class) belonging to the specified
	 * {@code context}.
	 */
	public static boolean isAnyRepeatableAnnotationPresent(ExtensionContext context,
			Class<? extends Annotation>... annotationTypes) {
		return Stream
				.of(annotationTypes)
				.flatMap(annotationType -> findClosestEnclosingRepeatableAnnotations(context, annotationType))
				.iterator()
				.hasNext();
	}

	/**
	 * Returns the specified annotation if it is either <em>present</em>, <em>indirectly present</em>,
	 * <em>meta-present</em>, or <em>enclosing-present</em> on the test element (method or class) belonging
	 * to the specified {@code context}. If the annotations are present on more than one enclosing type,
	 * the closest ones are returned.
	 */
	public static <A extends Annotation> Optional<A> findClosestEnclosingAnnotation(ExtensionContext context,
			Class<A> annotationType) {
		return findAnnotations(context, annotationType, false, false).findFirst();
	}

	/**
	 * Returns the specified annotations if they are either <em>present</em>, <em>indirectly present</em>,
	 * <em>meta-present</em>, or <em>enclosing-present</em> on the test element (method or class) belonging
	 * to the specified {@code context}. If the annotations are present on more than one enclosing type,
	 * all instances are returned.
	 */
	public static <A extends Annotation> Stream<A> findAllEnclosingAnnotations(ExtensionContext context,
			Class<A> annotationType) {
		return findAnnotations(context, annotationType, false, true);
	}

	/**
	 * Returns the specified repeatable annotations if they are either <em>present</em>,
	 * <em>indirectly present</em>, <em>meta-present</em>, or <em>enclosing-present</em> on the test
	 * element (method or class) belonging to the specified {@code context}. If the annotations are
	 * present on more than one enclosing type, the instances on the closest one are returned.
	 */
	public static <A extends Annotation> Stream<A> findClosestEnclosingRepeatableAnnotations(ExtensionContext context,
			Class<A> annotationType) {
		return findAnnotations(context, annotationType, true, false);
	}

	/**
	 * Returns the specified repeatable annotations if they are either <em>present</em>,
	 * <em>indirectly present</em>, <em>meta-present</em>, or <em>enclosing-present</em> on the test
	 * element (method or class) belonging to the specified {@code context}. If the annotation is
	 * present on more than one enclosing type, all instances are returned.
	 */
	public static <A extends Annotation> Stream<A> findAllEnclosingRepeatableAnnotations(ExtensionContext context,
			Class<A> annotationType) {
		return findAnnotations(context, annotationType, true, true);
	}

	static <A extends Annotation> Stream<A> findAnnotations(ExtensionContext context, Class<A> annotationType,
			boolean findRepeated, boolean findAllEnclosing) {
		/*
		 * Implementation notes:
		 *
		 * This method starts with the specified element and, if not happy with the results (depends on the
		 * arguments and whether the annotation is present) kicks off a recursive search. The recursion steps
		 * through enclosing types (if required by the arguments, thus handling _enclosing-presence_) and
		 * eventually calls either `AnnotationSupport::findRepeatableAnnotations` or
		 * `AnnotationSupport::findAnnotation` (depending on arguments, thus handling the repeatable case).
		 * Both of these methods check for _meta-presence_ and _indirect presence_.
		 */
		List<A> onMethod = context
				.getTestMethod()
				.map(method -> findOnElement(method, annotationType, findRepeated))
				.orElse(Collections.emptyList());
		if (!findAllEnclosing && !onMethod.isEmpty())
			return onMethod.stream();
		Stream<A> onClass = findOnOuterClasses(context.getTestClass(), annotationType, findRepeated, findAllEnclosing);

		return Stream.concat(onMethod.stream(), onClass);
	}

	private static <A extends Annotation> List<A> findOnElement(AnnotatedElement element, Class<A> annotationType,
			boolean findRepeated) {
		if (findRepeated)
			return AnnotationSupport.findRepeatableAnnotations(element, annotationType);
		else
			return AnnotationSupport
					.findAnnotation(element, annotationType)
					.map(Collections::singletonList)
					.orElse(Collections.emptyList());
	}

	private static <A extends Annotation> Stream<A> findOnOuterClasses(Optional<Class<?>> type, Class<A> annotationType,
			boolean findRepeated, boolean findAllEnclosing) {
		if (!type.isPresent())
			return Stream.empty();

		List<A> onThisClass = findOnElement(type.get(), annotationType, findRepeated);
		if (!findAllEnclosing && !onThisClass.isEmpty())
			return onThisClass.stream();

		Stream<A> onParentClass = findOnOuterClasses(type.map(Class::getEnclosingClass), annotationType, findRepeated,
			findAllEnclosing);
		return Stream.concat(onThisClass.stream(), onParentClass);
	}

}
