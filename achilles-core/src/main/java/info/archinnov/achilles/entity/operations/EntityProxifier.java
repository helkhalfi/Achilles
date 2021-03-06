/**
 *
 * Copyright (C) 2012-2013 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.archinnov.achilles.entity.operations;

import info.archinnov.achilles.context.PersistenceContext;
import info.archinnov.achilles.proxy.EntityInterceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EntityProxifier<CONTEXT extends PersistenceContext> {
	private static final Logger log = LoggerFactory.getLogger(EntityProxifier.class);

	public Class<?> deriveBaseClass(Object entity) {
		log.debug("Deriving base class for entity {} ", entity);

		Class<?> baseClass = entity.getClass();
		if (isProxy(entity)) {
			EntityInterceptor<CONTEXT, ?> interceptor = getInterceptor(entity);
			baseClass = interceptor.getTarget().getClass();
		}

		return baseClass;
	}

	public <T> T buildProxy(T entity, CONTEXT context) {
		return buildProxy(entity, context, new HashSet<Method>());
	}

	@SuppressWarnings("unchecked")
	public <T> T buildProxy(T entity, CONTEXT context, Set<Method> alreadyLoaded) {

		if (entity == null) {
			return null;
		}

		log.debug("Build Cglib proxy for entity {} ", entity);

		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(entity.getClass());
		enhancer.setCallback(buildInterceptor(context, entity, alreadyLoaded));

		return (T) enhancer.create();
	}

	@SuppressWarnings("unchecked")
	public <T> T getRealObject(T proxy) {
		log.debug("Get real entity from proxy {} ", proxy);

		if (isProxy(proxy)) {
			Factory factory = (Factory) proxy;
			EntityInterceptor<CONTEXT, ?> interceptor = (EntityInterceptor<CONTEXT, ?>) factory.getCallback(0);
			return (T) interceptor.getTarget();
		} else {
			return proxy;
		}

	}

	public boolean isProxy(Object entity) {
		return Factory.class.isAssignableFrom(entity.getClass());
	}

	public <T> EntityInterceptor<CONTEXT, T> getInterceptor(T proxy) {
		log.debug("Get interceptor from proxy {} ", proxy);

		Factory factory = (Factory) proxy;

		@SuppressWarnings("unchecked")
		EntityInterceptor<CONTEXT, T> interceptor = (EntityInterceptor<CONTEXT, T>) factory.getCallback(0);
		return interceptor;
	}

	public <T> void ensureProxy(T proxy) {
		if (!isProxy(proxy)) {
			throw new IllegalStateException("The entity '" + proxy + "' is not in 'managed' state.");
		}
	}

	public <T> T unwrap(T proxy) {
		log.debug("Unproxying object {} ", proxy);

		if (proxy != null) {

			if (isProxy(proxy)) {
				return getRealObject(proxy);
			} else {
				return proxy;
			}
		} else {
			return null;
		}
	}

	public <K, V> Entry<K, V> unwrap(Entry<K, V> entry) {
		V value = entry.getValue();
		if (isProxy(value)) {
			value = getRealObject(value);
			entry.setValue(value);
		}
		return entry;
	}

	public <T> Collection<T> unwrap(Collection<T> proxies) {
		Collection<T> result = new ArrayList<T>();
		for (T proxy : proxies) {
			result.add(unwrap(proxy));
		}
		return result;
	}

	public <T> List<T> unwrap(List<T> proxies) {
		List<T> result = new ArrayList<T>();
		for (T proxy : proxies) {
			result.add(this.unwrap(proxy));
		}

		return result;
	}

	public <T> Set<T> unwrap(Set<T> proxies) {
		Set<T> result = new HashSet<T>();
		for (T proxy : proxies) {
			result.add(this.unwrap(proxy));
		}

		return result;
	}

	public abstract <T> EntityInterceptor<CONTEXT, T> buildInterceptor(CONTEXT context, T entity,
			Set<Method> alreadyLoaded);
}
