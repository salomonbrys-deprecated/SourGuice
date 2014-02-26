package com.github.sourguice;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.github.sourguice.MvcControlerModule.BindBuilder;
import com.github.sourguice.MvcControlerModule.MvcControlerModuleHelperProxy;
import com.github.sourguice.annotation.request.GuiceRequest;
import com.github.sourguice.annotation.request.PathVariablesMap;
import com.github.sourguice.call.MvcCaller;
import com.github.sourguice.call.impl.MvcCallerImpl;
import com.github.sourguice.call.impl.PathVariablesProvider;
import com.github.sourguice.controller.ControllerHandlersRepository;
import com.github.sourguice.controller.ControllerInterceptor;
import com.github.sourguice.controller.ControllersServlet;
import com.github.sourguice.controller.InstanceGetter;
import com.github.sourguice.controller.InterceptWithMatcher;
import com.github.sourguice.conversion.ConversionService;
import com.github.sourguice.conversion.Converter;
import com.github.sourguice.conversion.def.BooleanConverter;
import com.github.sourguice.conversion.def.DoubleConverter;
import com.github.sourguice.conversion.def.EnumConverter;
import com.github.sourguice.conversion.def.FloatConverter;
import com.github.sourguice.conversion.def.IntegerConverter;
import com.github.sourguice.conversion.def.LongConverter;
import com.github.sourguice.conversion.def.ShortConverter;
import com.github.sourguice.conversion.def.StringConverter;
import com.github.sourguice.conversion.impl.ConversionServiceImpl;
import com.github.sourguice.exception.ExceptionHandler;
import com.github.sourguice.exception.ExceptionService;
import com.github.sourguice.exception.def.MVCResponseExceptionHandler;
import com.github.sourguice.exception.impl.ExceptionServiceImpl;
import com.github.sourguice.request.ForwardableRequestFactory;
import com.github.sourguice.request.wrapper.GuiceForwardHttpRequest;
import com.github.sourguice.throwable.controller.MvcResponseException;
import com.github.sourguice.throwable.service.exception.UnreachableExceptionHandlerException;
import com.github.sourguice.utils.MVCCallInterceptSetter;
import com.github.sourguice.utils.RequestScopeContainer;
import com.github.sourguice.view.Model;
import com.github.sourguice.view.ViewRenderer;
import com.github.sourguice.view.ViewRendererService;
import com.github.sourguice.view.def.JSPViewRenderer;
import com.github.sourguice.view.impl.ViewRendererServiceImpl;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletScopes;

/**
 * This is used by {@link MvcControlerModule} to actually bind the implementations of SourGuices classes
 * This is needed because SourGuice builds two different jars : one for API and one for implementation
 *
 * @author Salomon BRYS <salomon.brys@gmail.com>
 */
public class MvcControlerModuleHelperImpl implements MvcControlerModuleHelperProxy {

	/**
	 * The list of registered path and their corresponding controllers
	 * The purpose of this is to ensure that a path is handled by one controller,
	 * even if this path has been registered multiple times
	 */
	private @CheckForNull HashMap<String, ControllersServlet> servlets = null;

	/**
	 * Contains all ControllerHandlers
	 * This is to make sure that there will be one and only one ControllerHandler for each controller class
	 */
	private ControllerHandlersRepository repository = new ControllerHandlersRepository();

	/**
	 * The actual module that ws subclassed to bind controllers
	 */
	MvcControlerModule module;

	/**
	/**
	 * Conversion service
	 */
	private ConversionServiceImpl conversionService = new ConversionServiceImpl();

	/**
	 * Exception service
	 */
	private ExceptionServiceImpl exceptionService = new ExceptionServiceImpl();

	/**
	 * ViewRenderer service
	 */
	private ViewRendererServiceImpl viewRendererService = new ViewRendererServiceImpl();

	/**
	 * Constructor, used by {@link MvcControlerModule}.
	 * @param module The module itself, so this helper will access to Guice binding methods
	 */
	public MvcControlerModuleHelperImpl(MvcControlerModule module) {
		super();
		this.module = module;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ForwardableRequestFactory getForwardableRequestFactory(@GuiceRequest HttpServletRequest req, ServletContext context) {
		return new GuiceForwardHttpRequest(req, context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void configureServlets() {

		// Registers default converters
		module.convertTo(boolean.class, Boolean.class).withInstance(new BooleanConverter());
		module.convertTo(short.class, Short.class).withInstance(new ShortConverter());
		module.convertTo(int.class, Integer.class).withInstance(new IntegerConverter());
		module.convertTo(long.class, Long.class).withInstance(new LongConverter());
		module.convertTo(float.class, Float.class).withInstance(new FloatConverter());
		module.convertTo(double.class, Double.class).withInstance(new DoubleConverter());
		module.convertTo(Enum.class).withInstance(new EnumConverter());
		module.convertTo(String.class).withInstance(new StringConverter());

		// Registers default exception handlers
		try {
			module.handleException(MvcResponseException.class).withInstance(new MVCResponseExceptionHandler());
		}
		catch (UnreachableExceptionHandlerException e) {
			// THIS SHOULD NEVER HAPPEN
			throw new RuntimeException(e);
		}

		// Registers default view renderers
		module.renderViews(".*\\.jsp").with(JSPViewRenderer.class);

		// Creates servlet map to be later filled by configureControllers()
		servlets = new HashMap<>();

		// Asks for controller registration by subclass
		// This will fill the servlets map
		module.configureControllers();

		// Binds RequestScope container that will contains RequestScope objects that cannot be directly integrated into Guice
		module.binder().bind(RequestScopeContainer.class);

		// Binds the services
		module.binder().bind(ConversionService.class).toInstance(conversionService);
		module.binder().bind(ExceptionService.class).toInstance(exceptionService);
		module.binder().bind(ViewRendererService.class).toInstance(viewRendererService);

		// Binds view related classes
		module.binder().bind(Model.class).in(ServletScopes.REQUEST);

		// Binds method calling related classes
		module.binder().bind(MvcCaller.class).to(MvcCallerImpl.class).in(RequestScoped.class);
		module.binder().bind(new TypeLiteral<Map<String, String>>() {/**/}).annotatedWith(PathVariablesMap.class).toProvider(PathVariablesProvider.class).in(RequestScoped.class);

		// Creates a controllerHandler repository and registers it in guice
		// We create it because we need to handle it directly in this method
		module.binder().bind(ControllerHandlersRepository.class).toInstance(repository);

		// Binds Intercept
		module.binder().bind(MVCCallInterceptSetter.class);

		// Binds interceptors
		ControllerInterceptor interceptor = new ControllerInterceptor();
		module.binder().requestInjection(interceptor);
		module.binder().bindInterceptor(Matchers.any(), new InterceptWithMatcher(), interceptor);
		module.binder().bindInterceptor(new InterceptWithMatcher(), Matchers.any(), interceptor);

		assert servlets != null;
		Map<String, ControllersServlet> allServlets = servlets;
		// Loops through all registered patterns and their corresponding ControllerHandler.
		// Registers each couple in Guice.
		for (String pattern : allServlets.keySet()) {
			ControllersServlet servlet = allServlets.get(pattern);
			module.binder().requestInjection(servlet);
			module._serve(pattern).with(servlet);
		}

		// Sets null to the servlets variable so any further call to control().with() will raise a NullPointerException
		servlets = null;
	}

	/**
	 * Registers a pattern to a controller class
	 * This is called by {@link BindBuilder#with(Class)}
	 *
	 * @param clazz The controller class to register
	 * @param pattern The pattern on which to register to controller
	 */
	@Override
	public void registerControl(final String pattern, InstanceGetter<?> ig) {
		// Registers all filters that are declared by the @FilterThrough annotation of this class and of all its parents
		Map<String, String> initParams = new HashMap<>();
		initParams.put("pattern", pattern);

		// Creates a controller servlet for this pattern or gets it if this pattern has already been registered
		ControllersServlet servlet;
		assert servlets != null;
		if (servlets.containsKey(pattern))
			servlet = servlets.get(pattern);
		else {
			servlet = new ControllersServlet();
			servlets.put(pattern, servlet);
		}

		// Registers a controller handler into the controller servlet
		// The handler is retrived from the repository to avoid creating two handlers for the same controller class
		servlet.addController(repository.get(ig));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerViewRenderer(Pattern pattern, InstanceGetter<? extends ViewRenderer> renderer) {
		viewRendererService.register(pattern, renderer);
	}

	@Override
	public void registerConverter(Class<?> type, InstanceGetter<? extends Converter<?>> ig) {
		conversionService.register(type, ig);
	}

	@Override
	public <T extends Exception> void registerExceptionHandler(Class<? extends T> cls, InstanceGetter<? extends ExceptionHandler<T>> ig) {
		exceptionService.register(cls, ig);
	}

}