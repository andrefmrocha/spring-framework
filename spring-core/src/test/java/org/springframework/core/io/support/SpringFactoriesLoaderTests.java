/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io.support;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FactoryInstantiator;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;
import org.springframework.core.log.LogMessage;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringFactoriesLoader}.
 *
 * @author Arjen Poutsma
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
class SpringFactoriesLoaderTests {

	@BeforeAll
	static void clearCache() {
		SpringFactoriesLoader.cache.clear();
		assertThat(SpringFactoriesLoader.cache).isEmpty();
	}

	@AfterAll
	static void checkCache() {
		assertThat(SpringFactoriesLoader.cache).hasSize(3);
		SpringFactoriesLoader.cache.clear();
	}



	void loadFactoryNames() {
		List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(DummyFactory.class, null);
		assertThat(factoryNames).containsExactlyInAnyOrder(MyDummyFactory1.class.getName(), MyDummyFactory2.class.getName());
	}

	void loadWhenNoRegisteredImplementationsReturnsEmptyList() {
		List<Integer> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(Integer.class);
		assertThat(factories).isEmpty();
	}

	void loadWhenDuplicateRegistrationsPresentReturnsListInCorrectOrder() {
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(DummyFactory.class);
		assertThat(factories).hasSize(2);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
	}

	void loadWhenPackagePrivateFactory() {
		List<DummyPackagePrivateFactory> factories =
				SpringFactoriesLoader.forDefaultResourceLocation().load(DummyPackagePrivateFactory.class);
		assertThat(factories).hasSize(1);
		assertThat(Modifier.isPublic(factories.get(0).getClass().getModifiers())).isFalse();
	}

	void loadWhenIncompatibleTypeThrowsException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SpringFactoriesLoader.forDefaultResourceLocation().load(String.class))
			.withMessageContaining("Unable to instantiate factory class "
					+ "[org.springframework.core.io.support.MyDummyFactory1] for factory type [java.lang.String]");
	}

	void loadWithLoggingFailureHandlerWhenIncompatibleTypeReturnsEmptyList() {
		Log logger = mock(Log.class);
		FailureHandler failureHandler = FailureHandler.logging(logger);
		List<String> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(String.class, failureHandler);
		assertThat(factories).isEmpty();
	}

	void loadWithArgumentResolverWhenNoDefaultConstructor() {
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.constructorArgumentFactories)
					.load(DummyFactory.class, resolver);
		assertThat(factories).hasSize(3);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
		assertThat(factories.get(2)).isInstanceOf(ConstructorArgsDummyFactory.class);
		assertThat(factories).extracting(DummyFactory::getString).containsExactly("Foo", "Bar", "injected");
	}

	void loadWhenMultipleConstructorsThrowsException() {
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.multipleArgumentFactories)
							.load(DummyFactory.class, resolver))
				.withMessageContaining("Unable to instantiate factory class "
						+ "[org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] for factory type [org.springframework.core.io.support.DummyFactory]")
				.havingRootCause().withMessageContaining("Class [org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] has no suitable constructor");
	}

	void loadWithLoggingFailureHandlerWhenMissingArgumentDropsItem() {
		Log logger = mock(Log.class);
		FailureHandler failureHandler = FailureHandler.logging(logger);
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.multipleArgumentFactories)
					.load(DummyFactory.class, failureHandler);
		assertThat(factories).hasSize(2);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
	}

	void loadFactoriesLoadsFromDefaultLocation() {
		List<DummyFactory> factories = SpringFactoriesLoader.loadFactories(
				DummyFactory.class, null);
		assertThat(factories).hasSize(2);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
		assertThat(factories.get(1)).isInstanceOf(MyDummyFactory2.class);
	}

	void loadForResourceLocationWhenLocationDoesNotExistReturnsEmptyList() {
		List<DummyFactory> factories = SpringFactoriesLoader.forResourceLocation(
				"META-INF/missing/missing-spring.factories").load(DummyFactory.class);
		assertThat(factories).isEmpty();
	}

	void loadForResourceLocationLoadsFactories() {
		List<DummyFactory> factories = SpringFactoriesLoader.forResourceLocation(
				"META-INF/custom/custom-spring.factories").load(DummyFactory.class);
		assertThat(factories).hasSize(1);
		assertThat(factories.get(0)).isInstanceOf(MyDummyFactory1.class);
	}

	void sameCachedResultIsUsedForDefaultClassLoaderAndNullClassLoader() {
		SpringFactoriesLoader forNull = SpringFactoriesLoader.forDefaultResourceLocation(null);
		SpringFactoriesLoader forDefault = SpringFactoriesLoader.forDefaultResourceLocation(ClassUtils.getDefaultClassLoader());
		assertThat(forNull).isSameAs(forDefault);
	}


	@Nested
	class FailureHandlerTests {

		void throwingReturnsHandlerThatThrowsIllegalArgumentException() {
			FailureHandler handler = FailureHandler.throwing();
			RuntimeException cause = new RuntimeException();
			assertThatIllegalArgumentException().isThrownBy(() -> handler.handleFailure(
					DummyFactory.class, MyDummyFactory1.class.getName(),
					cause)).withMessageStartingWith("Unable to instantiate factory class").withCause(cause);
		}

		void throwingWithFactoryReturnsHandlerThatThrows() {
			FailureHandler handler = FailureHandler.throwing(IllegalStateException::new);
			RuntimeException cause = new RuntimeException();
			assertThatIllegalStateException().isThrownBy(() -> handler.handleFailure(
					DummyFactory.class, MyDummyFactory1.class.getName(),
					cause)).withMessageStartingWith("Unable to instantiate factory class").withCause(cause);
		}

		void loggingReturnsHandlerThatLogs() {
			Log logger = mock(Log.class);
			FailureHandler handler = FailureHandler.logging(logger);
			RuntimeException cause = new RuntimeException();
			handler.handleFailure(DummyFactory.class, MyDummyFactory1.class.getName(), cause);
			verify(logger).trace(isA(LogMessage.class), eq(cause));
		}

		void handleMessageReturnsHandlerThatAcceptsMessage() {
			List<Throwable> failures = new ArrayList<>();
			List<String> messages = new ArrayList<>();
			FailureHandler handler = FailureHandler.handleMessage((message, failure) -> {
				failures.add(failure);
				messages.add(message.get());
			});
			RuntimeException cause = new RuntimeException();
			handler.handleFailure(DummyFactory.class, MyDummyFactory1.class.getName(), cause);
			assertThat(failures).containsExactly(cause);
			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).startsWith("Unable to instantiate factory class");
		}

	}


	@Nested
	class ArgumentResolverTests {

		void ofValueResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		void ofValueSupplierResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.ofSupplied(CharSequence.class, () -> "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		void fromAdaptsFunction() {
			ArgumentResolver resolver = ArgumentResolver.from(
					type -> CharSequence.class.equals(type) ? "test" : null);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		void andValueReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		void andValueWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(CharSequence.class, "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		void andValueSupplierReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(Integer.class, () -> 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		void andValueSupplierWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(CharSequence.class, () -> "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		void andResolverReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			resolver = resolver.and(ArgumentResolver.of(CharSequence.class, "ignore").and(Long.class, 234L));
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
			assertThat(resolver.resolve(Long.class)).isEqualTo(234L);
		}

	}

	@Nested
	class FactoryInstantiatorTests {

		private final ArgumentResolver resolver = ArgumentResolver.of(String.class, "test");

		void defaultConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					DefaultConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void singleConstructorWithArgumentsCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SingleConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void multiplePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void multiplePackagePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePackagePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void singlePackagePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePackagePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void singlePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		void multiplePackagePrivateConstructorsThrowsException() {
			assertThatIllegalStateException().isThrownBy(
					() -> FactoryInstantiator.forClass(MultiplePackagePrivateConstructors.class))
				.withMessageContaining("has no suitable constructor");
		}

		static class DefaultConstructor {

		}

		static class SingleConstructor {

			SingleConstructor(String arg) {
			}

		}

		static class MultiplePrivateAndSinglePublicConstructor {

			public MultiplePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			private MultiplePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}

		}

		static class MultiplePackagePrivateAndSinglePublicConstructor {

			public MultiplePackagePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}

		}


		static class SinglePackagePrivateConstructor {

			SinglePackagePrivateConstructor(String arg) {
			}

		}

		static class SinglePrivateConstructor {

			private SinglePrivateConstructor(String arg) {
			}

		}

		static class MultiplePackagePrivateConstructors {

			MultiplePackagePrivateConstructors(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateConstructors(String arg, boolean extra) {
			}

		}

	}

	private static class LimitedClassLoader extends URLClassLoader {

		private static final ClassLoader constructorArgumentFactories = new LimitedClassLoader("constructor-argument-factories");

		private static final ClassLoader multipleArgumentFactories = new LimitedClassLoader("multiple-arguments-factories");

		LimitedClassLoader(String location) {
			super(new URL[] { toUrl(location) });
		}

		private static URL toUrl(String location) {
			try {
				return new File("src/test/resources/org/springframework/core/io/support/" + location + "/").toURI().toURL();
			}
			catch (MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
		}

	}

}
