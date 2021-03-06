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
package info.archinnov.achilles.context;

import static info.archinnov.achilles.entity.metadata.PropertyType.*;
import static info.archinnov.achilles.type.ConsistencyLevel.*;
import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Mockito.*;
import info.archinnov.achilles.consistency.ThriftConsistencyLevelPolicy;
import info.archinnov.achilles.context.execution.SafeExecutionContext;
import info.archinnov.achilles.dao.ThriftCounterDao;
import info.archinnov.achilles.dao.ThriftGenericEntityDao;
import info.archinnov.achilles.dao.ThriftGenericWideRowDao;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.operations.EntityInitializer;
import info.archinnov.achilles.entity.operations.EntityRefresher;
import info.archinnov.achilles.entity.operations.ThriftEntityLoader;
import info.archinnov.achilles.entity.operations.ThriftEntityMerger;
import info.archinnov.achilles.entity.operations.ThriftEntityPersister;
import info.archinnov.achilles.entity.operations.ThriftEntityProxifier;
import info.archinnov.achilles.proxy.ReflectionInvoker;
import info.archinnov.achilles.proxy.ThriftEntityInterceptor;
import info.archinnov.achilles.test.builders.CompleteBeanTestBuilder;
import info.archinnov.achilles.test.builders.PropertyMetaTestBuilder;
import info.archinnov.achilles.test.mapping.entity.CompleteBean;
import info.archinnov.achilles.test.mapping.entity.UserBean;
import info.archinnov.achilles.type.OptionsBuilder;
import me.prettyprint.hector.api.mutation.Mutator;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

@RunWith(MockitoJUnitRunner.class)
public class ThriftPersistenceContextTest {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	private ThriftPersistenceContext context;

	@Mock
	private ReflectionInvoker introspector;

	private EntityMeta entityMeta;
	private PropertyMeta idMeta;

	@Mock
	private ThriftDaoContext thriftDaoContext;

	@Mock
	private ThriftConsistencyLevelPolicy policy;

	@Mock
	private ThriftGenericEntityDao entityDao;

	@Mock
	private ThriftGenericWideRowDao wideRowDao;

	@Mock
	private Mutator<Long> mutator;

	@Mock
	private ThriftImmediateFlushContext flushContext;

	@Mock
	private ThriftConsistencyContext consistencyContext;

	@Mock
	private ThriftEntityProxifier proxifier;

	@Mock
	private ThriftEntityPersister persister;

	@Mock
	private ThriftEntityMerger merger;

	@Mock
	private ThriftEntityLoader loader;

	@Mock
	private EntityInitializer initializer;

	@Mock
	private ThriftEntityInterceptor<CompleteBean> interceptor;

	@Mock
	private EntityRefresher<ThriftPersistenceContext> refresher;

	@Mock
	private ReflectionInvoker invoker;

	@Captor
	private ArgumentCaptor<SafeExecutionContext<Void>> voidExecCaptor;

	@Captor
	private ArgumentCaptor<SafeExecutionContext<CompleteBean>> execCaptor;

	private ConfigurationContext configContext = new ConfigurationContext();

	private Long id = RandomUtils.nextLong();

	private CompleteBean entity = CompleteBeanTestBuilder.builder().id(id).buid();

	private UserBean bean;

	@Before
	public void setUp() throws Exception {

		bean = new UserBean();
		bean.setUserId(id);

		idMeta = PropertyMetaTestBuilder.completeBean(Void.class, Long.class).field("id").type(ID).accessors()
				.invoker(invoker).build();

		configContext.setConsistencyPolicy(policy);
		entityMeta = new EntityMeta();
		entityMeta.setTableName("table");
		entityMeta.setClusteredEntity(false);
		entityMeta.setIdMeta(idMeta);
		entityMeta.setEntityClass(CompleteBean.class);

		when(flushContext.getConsistencyContext()).thenReturn(consistencyContext);
		when(thriftDaoContext.findEntityDao("table")).thenReturn(entityDao);
		when(invoker.getPrimaryKey(entity, idMeta)).thenReturn(id);

		context = new ThriftPersistenceContext(entityMeta, configContext, thriftDaoContext, flushContext, entity,
				OptionsBuilder.noOptions());
	}

	@Test
	public void should_init_with_entity() throws Exception {

		assertThat(context.getPrimaryKey()).isEqualTo(entity.getId());
		assertThat(context.getEntity()).isEqualTo(entity);
		assertThat(context.getEntityMeta()).isSameAs(entityMeta);
		assertThat(context.getEntityDao()).isSameAs(entityDao);
	}

	@Test
	public void should_init_with_type_and_primary_key() throws Exception {
		entityMeta.setClusteredEntity(true);
		when(thriftDaoContext.findWideRowDao("table")).thenReturn(wideRowDao);

		context = new ThriftPersistenceContext(entityMeta, configContext, thriftDaoContext, flushContext,
				CompleteBean.class, entity.getId(), OptionsBuilder.noOptions());

		assertThat(context.getPrimaryKey()).isEqualTo(entity.getId());
		assertThat(context.getEntity()).isNull();
		assertThat(context.getEntityMeta()).isSameAs(entityMeta);
		assertThat(context.getWideRowDao()).isSameAs(wideRowDao);
	}

	@Test
	public void should_duplicate_for_new_entity() throws Exception {
		Long primaryKey = RandomUtils.nextLong();
		CompleteBean bean = new CompleteBean();
		bean.setId(primaryKey);

		when(invoker.getPrimaryKey(bean, idMeta)).thenReturn(primaryKey);

		ThriftPersistenceContext duplicate = context.duplicate(bean);

		assertThat(duplicate.getPrimaryKey()).isSameAs(primaryKey);
	}

	@Test
	public void should_persist() throws Exception {
		Whitebox.setInternalState(context, ThriftEntityPersister.class, persister);

		context.persist();

		verify(consistencyContext).executeWithWriteConsistencyLevel(voidExecCaptor.capture());
		voidExecCaptor.getValue().execute();
		verify(persister).persist(context);
		verify(flushContext).flush();
	}

	@Test
	public void should_merge() throws Exception {
		Whitebox.setInternalState(context, ThriftEntityMerger.class, merger);
		when(merger.merge(context, entity)).thenReturn(entity);

		context.merge(entity);

		verify(consistencyContext).executeWithWriteConsistencyLevel(execCaptor.capture());
		CompleteBean merged = execCaptor.getValue().execute();

		assertThat(merged).isSameAs(entity);
		verify(flushContext).flush();
	}

	@Test
	public void should_remove() throws Exception {
		Whitebox.setInternalState(context, ThriftEntityPersister.class, persister);

		context.remove();

		verify(consistencyContext).executeWithWriteConsistencyLevel(voidExecCaptor.capture());
		voidExecCaptor.getValue().execute();

		verify(persister).remove(context);
		verify(flushContext).flush();
	}

	@Test
	public void should_find() throws Exception {
		Whitebox.setInternalState(context, ThriftEntityLoader.class, loader);
		Whitebox.setInternalState(context, ThriftEntityProxifier.class, proxifier);

		when(loader.load(context, CompleteBean.class)).thenReturn(entity);
		when(proxifier.buildProxy(entity, context)).thenReturn(entity);
		when(consistencyContext.executeWithReadConsistencyLevel(execCaptor.capture())).thenReturn(entity);

		CompleteBean actual = context.find(CompleteBean.class);

		CompleteBean actual2 = execCaptor.getValue().execute();

		assertThat(actual).isSameAs(entity);
		assertThat(actual2).isSameAs(entity);
	}

	@Test
	public void should_not_find() throws Exception {
		Whitebox.setInternalState(context, ThriftEntityLoader.class, loader);
		Whitebox.setInternalState(context, ThriftEntityProxifier.class, proxifier);

		when(loader.load(context, CompleteBean.class)).thenReturn(null);
		when(proxifier.buildProxy(entity, context)).thenReturn(entity);

		context.find(CompleteBean.class);

		verify(consistencyContext).executeWithReadConsistencyLevel(execCaptor.capture());
		CompleteBean actual = execCaptor.getValue().execute();

		assertThat(actual).isNull();
	}

	@Test
	public void should_get_reference() throws Exception {
		context = new ThriftPersistenceContext(entityMeta, configContext, thriftDaoContext, flushContext, entity,
				OptionsBuilder.noOptions());

		Whitebox.setInternalState(context, ThriftEntityLoader.class, loader);
		Whitebox.setInternalState(context, ThriftEntityProxifier.class, proxifier);

		when(loader.load(context, CompleteBean.class)).thenReturn(entity);
		when(proxifier.buildProxy(entity, context)).thenReturn(entity);

		context.getReference(CompleteBean.class);

		verify(consistencyContext).executeWithReadConsistencyLevel(execCaptor.capture());
		CompleteBean actual = execCaptor.getValue().execute();

		assertThat(context.isLoadEagerFields()).isFalse();
		assertThat(actual).isSameAs(entity);
	}

	@Test
	public void should_refresh() throws Exception {
		Whitebox.setInternalState(context, EntityRefresher.class, refresher);

		context.refresh();

		verify(consistencyContext).executeWithReadConsistencyLevel(voidExecCaptor.capture());
		voidExecCaptor.getValue().execute();

		verify(refresher).refresh(context);
	}

	@Test
	public void should_initialize() throws Exception {
		Whitebox.setInternalState(context, "initializer", initializer);
		Whitebox.setInternalState(context, ThriftEntityProxifier.class, proxifier);

		when(proxifier.getInterceptor(entity)).thenReturn(interceptor);

		CompleteBean actual = context.initialize(entity);
		assertThat(actual).isSameAs(entity);

		verify(consistencyContext).executeWithReadConsistencyLevel(voidExecCaptor.capture());
		voidExecCaptor.getValue().execute();

		verify(initializer).initializeEntity(entity, entityMeta, interceptor);
	}

	@Test
	public void should_find_entity_dao() throws Exception {
		assertThat(context.findEntityDao("table")).isSameAs(entityDao);
	}

	@Test
	public void should_find_wide_row_dao() throws Exception {
		when(thriftDaoContext.findWideRowDao("table")).thenReturn(wideRowDao);
		assertThat(context.findWideRowDao("table")).isSameAs(wideRowDao);
	}

	@Test
	public void should_get_counter_dao() throws Exception {
		ThriftCounterDao counterDao = mock(ThriftCounterDao.class);

		when(thriftDaoContext.getCounterDao()).thenReturn(counterDao);
		assertThat(context.getCounterDao()).isSameAs(counterDao);
	}

	@Test
	public void should_get_entity_mutator() throws Exception {
		Mutator<Object> mutator = mock(Mutator.class);

		when(flushContext.getEntityMutator("table")).thenReturn(mutator);
		assertThat(context.getEntityMutator("table")).isSameAs(mutator);
	}

	@Test
	public void should_get_wide_row_mutator() throws Exception {
		Mutator<Object> mutator = mock(Mutator.class);

		when(flushContext.getWideRowMutator("table")).thenReturn(mutator);
		assertThat(context.getWideRowMutator("table")).isSameAs(mutator);
	}

	@Test
	public void should_get_counter_mutator() throws Exception {
		Mutator<Object> mutator = mock(Mutator.class);

		when(flushContext.getCounterMutator()).thenReturn(mutator);
		assertThat(context.getCounterMutator()).isSameAs(mutator);
	}

	@Test
	public void should_execute_with_read_consistency_level() throws Exception {
		SafeExecutionContext<CompleteBean> execContext = mock(SafeExecutionContext.class);

		when(consistencyContext.executeWithReadConsistencyLevel(execContext, LOCAL_QUORUM)).thenReturn(entity);

		CompleteBean actual = context.executeWithReadConsistencyLevel(execContext, LOCAL_QUORUM);

		assertThat(actual).isSameAs(entity);

	}

	@Test
	public void should_execute_with_write_consistency_level() throws Exception {
		SafeExecutionContext<CompleteBean> execContext = mock(SafeExecutionContext.class);

		when(consistencyContext.executeWithWriteConsistencyLevel(execContext, LOCAL_QUORUM)).thenReturn(entity);

		CompleteBean actual = context.executeWithWriteConsistencyLevel(execContext, LOCAL_QUORUM);

		assertThat(actual).isSameAs(entity);
	}

}
