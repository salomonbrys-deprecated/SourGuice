package com.github.sourguice.controller.fetchers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import com.github.sourguice.annotation.request.PathVariablesMap;
import com.github.sourguice.utils.Annotations;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/**
 * Fetcher that handles argument that are not annotated with an annotation handled by previous fetchers
 * This will fetch the argument from Guice
 *
 * @param <T> The type of object to fetch from Guice
 *
 * @author Salomon BRYS <salomon.brys@gmail.com>
 */
public class InjectorArgumentFetcher<T> extends ArgumentFetcher<T> {

	/**
	 * A Guice {@link BindingAnnotation}, if there is one
	 */
	@CheckForNull Annotation bindingAnnotation;

	/**
	 * @see ArgumentFetcher#ArgumentFetcher(Type, int, Annotation[])
	 */
	public InjectorArgumentFetcher(TypeLiteral<T> type, Annotation[] annotations) {
		super(type, annotations);

		bindingAnnotation = Annotations.GetOneAnnotated(BindingAnnotation.class, annotations);
		if (bindingAnnotation == null)
			bindingAnnotation = Annotations.fromArray(annotations).getAnnotation(Named.class);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected @CheckForNull T getPrepared(HttpServletRequest req, @PathVariablesMap Map<String, String> pathVariables, Injector injector) {
		if (bindingAnnotation != null)
			return injector.getInstance(Key.get(this.type, bindingAnnotation));
		return injector.getInstance(Key.get(this.type));
	}
}
