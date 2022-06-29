/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.identityconnectors.testconnector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.Observer;
import org.identityconnectors.framework.api.operations.batch.BatchEmptyResult;
import org.identityconnectors.framework.api.operations.batch.BatchTask;
import org.identityconnectors.framework.api.operations.batch.CreateBatchTask;
import org.identityconnectors.framework.api.operations.batch.DeleteBatchTask;
import org.identityconnectors.framework.api.operations.batch.UpdateBatchTask;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException;
import org.identityconnectors.framework.common.exceptions.PreconditionRequiredException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.BatchResult;
import org.identityconnectors.framework.common.objects.BatchToken;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Subscription;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.common.objects.filter.FilteredResultsHandlerVisitor;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.BatchOp;
import org.identityconnectors.framework.spi.operations.ConnectorEventSubscriptionOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncEventSubscriptionOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

public abstract class TstAbstractConnector implements AuthenticateOp, ConnectorEventSubscriptionOp,
        CreateOp, DeleteOp, ResolveUsernameOp, SchemaOp, ScriptOnResourceOp, SearchOp<Filter>,
        SyncEventSubscriptionOp, SyncOp, TestOp, UpdateOp, BatchOp {

    private static final class ResourceComparator implements Comparator<ConnectorObject> {
        private final List<SortKey> sortKeys;

        private ResourceComparator(final SortKey... sortKeys) {
            this.sortKeys = Arrays.asList(sortKeys);
        }

        public int compare(final ConnectorObject r1, final ConnectorObject r2) {
            for (final SortKey sortKey : sortKeys) {
                final int result = compare(r1, r2, sortKey);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

        private int compare(final ConnectorObject r1, final ConnectorObject r2,
                final SortKey sortKey) {
            final List<Object> vs1 = getValuesSorted(r1, sortKey.getField());
            final List<Object> vs2 = getValuesSorted(r2, sortKey.getField());
            if (vs1.isEmpty() && vs2.isEmpty()) {
                return 0;
            } else if (vs1.isEmpty()) {
                // Sort resources with missing attributes last.
                return 1;
            } else if (vs2.isEmpty()) {
                // Sort resources with missing attributes last.
                return -1;
            } else {
                final Object v1 = vs1.get(0);
                final Object v2 = vs2.get(0);
                return sortKey.isAscendingOrder() ? CollectionUtil.forceCompare(v1, v2) : -1
                        * CollectionUtil.forceCompare(v1, v2);
            }
        }

        private List<Object> getValuesSorted(final ConnectorObject resource, final String field) {
            final Attribute value = AttributeUtil.find(field, resource.getAttributes());
            if (value == null || value.getValue() == null || value.getValue().isEmpty()) {
                return Collections.emptyList();
            } else if (value.getValue().size() > 1) {
                List<Object> results = new ArrayList<Object>(value.getValue());
                Collections.sort(results, VALUE_COMPARATOR);
                return results;
            } else {
                return value.getValue();
            }
        }
    }

    private static final Comparator<Object> VALUE_COMPARATOR = new Comparator<Object>() {

        public int compare(final Object o1, final Object o2) {
            return CollectionUtil.forceCompare(o1, o2);
        }
    };

    protected TstStatefulConnectorConfig config;

    public void init(Configuration cfg) {
        config = (TstStatefulConnectorConfig) cfg;
        config.getGuid();
    }

    public void update() {
        config.updateTest();
    }
    
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password,
            OperationOptions options) {
        if (config.isReturnNullTest()) {
            return null;
        } else {
            return config.authenticate(objectClass, username, password);
        }
    }

    public Subscription subscribe(final ObjectClass objectClass,final Filter eventFilter,final Observer<ConnectorObject> handler,
                                  final OperationOptions operationOptions) {
        final ConnectorObjectBuilder builder =
                new ConnectorObjectBuilder().setObjectClass(objectClass);
        Object op = operationOptions.getOptions().get("eventCount");
        Integer eventCount = 10;
        if (op instanceof Integer){
            eventCount = (Integer)op;
        }
        final int count  = eventCount;

        final boolean doComplete = operationOptions.getOptions().containsKey("doComplete");
        
        final SelfAwareExecutionRunnable runnable = new SelfAwareExecutionRunnable() {
            protected boolean doAction(int runCount) {

                if (TstAbstractConnector.this.config == null){
                    handler.onError(new IllegalStateException("Connector has been disposed"));
                    return false;
                }
                
                builder.setUid(String.valueOf(runCount));
                builder.setName(String.valueOf(runCount));
                handler.onNext(builder.build());

                if (runCount >= count) {
                    // Locally stop serving subscription
                    if (doComplete){
                        handler.onCompleted();
                    } else {
                        handler.onError(new ConnectorException(
                                "Subscription channel is closed"));
                    }
                    // ScheduledFuture should be stopped from here.
                    return false;
                }
                return true;
            }
        };
        runnable.runAction(config.getExecutorService(), 1000, 500, TimeUnit.MILLISECONDS);
        
        return new Subscription() {
            // Remotely request stop processing subscription
            public void close() {
                runnable.cancel();
            }

            public boolean isUnsubscribed() {
                return !runnable.getRunning();
            }

            public Object getReturnValue() {
                return null;
            }
        };
    }

    public Subscription subscribe(final ObjectClass objectClass,final SyncToken token,final Observer<SyncDelta> handler,
                                  final OperationOptions operationOptions) {
        final SyncDeltaBuilder builder =
                new SyncDeltaBuilder().setDeltaType(SyncDeltaType.CREATE_OR_UPDATE).setObject(
                        new ConnectorObjectBuilder().setObjectClass(objectClass).setUid("0")
                                .setName("SYNC_EVENT").build());

        Object op = operationOptions.getOptions().get("eventCount");
        Integer eventCount = 10;
        if (op instanceof Integer){
            eventCount = (Integer)op;
        }
        final int count  = eventCount;
        
        final boolean doComplete = operationOptions.getOptions().containsKey("doComplete");
        
        final SelfAwareExecutionRunnable runnable = new SelfAwareExecutionRunnable() {
            protected boolean doAction(int runCount) {

                if (TstAbstractConnector.this.config == null){
                    handler.onError(new IllegalStateException("Connector has been disposed"));
                    return false;
                }
                
                builder.setToken(new SyncToken(runCount));
                handler.onNext(builder.build());

                if (runCount >= count) {
                    // Locally stop serving subscription
                    if (doComplete){
                        handler.onCompleted();
                    } else {
                        handler.onError(new ConnectorException(
                                "Subscription channel is closed"));
                    }
                    // ScheduledFuture should be stopped from here.
                    return false;
                }
                return true;
            }
        };
        runnable.runAction(config.getExecutorService(), 1000, 500, TimeUnit.MILLISECONDS);

        return new Subscription() {
            // Remotely request stop processing subscription
            public void close() {
                runnable.cancel();
            }

            public boolean isUnsubscribed() {
                return !runnable.getRunning();
            }

            public Object getReturnValue() {
                return null;
            }
        };
    }

    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes,
            OperationOptions options) {
        AttributesAccessor accessor = new AttributesAccessor(createAttributes);
        if (config.isReturnNullTest()) {
            return null;
        } else if (config.isTestObjectClass(objectClass)) {
            return config.geObjectCache(objectClass).create(createAttributes);
        } else {
            if (accessor.hasAttribute("fail")) {
                throw new ConnectorException("Test Exception");
            } else if (accessor.hasAttribute("exist") && accessor.findBoolean("exist")) {
                throw new AlreadyExistsException(accessor.getName().getNameValue());
            } else if (accessor.hasAttribute("emails")) {
                Object value = AttributeUtil.getSingleValue(accessor.find("emails"));
                if (value instanceof Map) {
                    return new Uid((String) ((Map) value).get("email"));
                } else {
                    throw new InvalidAttributeValueException("Expecting Map");
                }
            }
            return new Uid(config.getGuid().toString());
        }
    }

    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        if (config.isReturnNullTest()) {
            return;
        } else if (config.isTestObjectClass(objectClass)) {
            config.geObjectCache(objectClass).delete(uid);
        } else {
            if (null == uid.getRevision()) {
                throw new PreconditionRequiredException("Version is required for MVCC");
            } else if (config.getGuid().toString().equals(uid.getRevision())) {
                // Delete
            } else {
                throw new PreconditionFailedException(
                        "Current version of resource is 0 and not match with: " + uid.getRevision());
            }
        }
    }

    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        if (config.isReturnNullTest()) {
            return null;
        } else {
            return config.resolveByUsername(objectClass, username);
        }
    }

    @SuppressWarnings("unchecked")
    public Schema schema() {
        if (config.isReturnNullTest()) {
            return null;
        } else {
            SchemaBuilder builder = new SchemaBuilder((Class<? extends Connector>) getClass());
            for (String type : config.getTestObjectClass()) {
                ObjectClassInfoBuilder classInfoBuilder = new ObjectClassInfoBuilder();
                classInfoBuilder.setType(type).addAttributeInfo(OperationalAttributeInfos.PASSWORD);
                builder.defineObjectClass(classInfoBuilder.build());
            }
            return builder.build();
        }
    }

    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        if (config.isReturnNullTest()) {
            return null;
        } else {
            try {
                return ScriptExecutorFactory.newInstance(request.getScriptLanguage())
                        .newScriptExecutor(null, request.getScriptText(), true).execute(
                                request.getScriptArguments());
            } catch (Exception e) {
                throw new ConnectorException(e.getMessage(), e);
            }
        }
    }

    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass,
            OperationOptions options) {
        return new FilterTranslator<Filter>() {

            public List<Filter> translate(Filter filter) {
                return Collections.singletonList(filter);
            }
        };
    }

    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler,
            OperationOptions options) {

        SortKey[] sortKeys = options.getSortKeys();
        if (null == sortKeys) {
            sortKeys = new SortKey[] { new SortKey(Name.NAME, true) };
        }

        // Rebuild the full result set.
        TreeSet<ConnectorObject> resultSet =
                new TreeSet<ConnectorObject>(new ResourceComparator(sortKeys));

        if (config.isReturnNullTest()) {
            return;
        } else if (config.isTestObjectClass(objectClass)) {
            Filter filter = FilteredResultsHandlerVisitor.wrapFilter(query, config.isCaseIgnore());
            for (ConnectorObject co : config.geObjectCache(objectClass).getIterable(filter)) {
                resultSet.add(co);
            }
        } else {
            if (null != query) {
                for (ConnectorObject co : collection.values()) {
                    if (query.accept(co)) {
                        resultSet.add(co);
                    }
                }
            } else {
                resultSet.addAll(collection.values());
            }
        }
        // Handle the results
        if (null != options.getPageSize()) {
            // Paged Search
            final String pagedResultsCookie = options.getPagedResultsCookie();
            String currentPagedResultsCookie = options.getPagedResultsCookie();
            final Integer pagedResultsOffset =
                    null != options.getPagedResultsOffset() ? Math.max(0, options
                            .getPagedResultsOffset()) : 0;
            final Integer pageSize = options.getPageSize();

            int index = 0;
            int pageStartIndex = null == pagedResultsCookie ? 0 : -1;
            int handled = 0;

            for (ConnectorObject entry : resultSet) {
                if (pageStartIndex < 0 && pagedResultsCookie.equals(entry.getName().getNameValue())) {
                    pageStartIndex = index + 1;
                }

                if (pageStartIndex < 0 || index < pageStartIndex) {
                    index++;
                    continue;
                }

                if (handled >= pageSize) {
                    break;
                }

                if (index >= pagedResultsOffset + pageStartIndex) {
                    if (handler.handle(entry)) {
                        handled++;
                        currentPagedResultsCookie = entry.getName().getNameValue();
                    } else {
                        break;
                    }
                }
                index++;
            }

            if (index == resultSet.size()) {
                currentPagedResultsCookie = null;
            }

            if (handler instanceof SearchResultsHandler) {
                ((SearchResultsHandler) handler).handleResult(new SearchResult(
                        currentPagedResultsCookie, SearchResult.CountPolicy.EXACT,
                        resultSet.size(), resultSet.size() - index));
            }
        } else {
            // Normal Search
            for (ConnectorObject entry : resultSet) {
                if (!handler.handle(entry)) {
                    break;
                }
            }
            if (handler instanceof SearchResultsHandler) {
                ((SearchResultsHandler) handler).handleResult(new SearchResult());
            }
        }

    }

    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler,
            OperationOptions options) {
        if (config.isReturnNullTest()) {
            return;
        } else if (config.isTestObjectClass(objectClass)) {
            for (SyncDelta delta : config.sync(objectClass, (Integer) token.getValue()).values()) {
                if (!handler.handle(delta)) {
                    break;
                }
            }
            if (handler instanceof SyncTokenResultsHandler) {
                ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(config
                        .getLatestSyncToken()));
            }
        } else {
            if (handler instanceof SyncTokenResultsHandler) {
                ((SyncTokenResultsHandler) handler).handleResult(getLatestSyncToken(objectClass));
            }
        }
    }

    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        if (config.isReturnNullTest()) {
            return null;
        } else if (config.isTestObjectClass(objectClass)) {
            return new SyncToken(config.getLatestSyncToken());
        } else {
            return new SyncToken(config.getGuid().toString());
        }
    }

    public void test() {
        if (config.getFailValidation()) {
            throw new ConnectorException("test failed " + CurrentLocale.get().getLanguage());
        }
    }

    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
            OperationOptions options) {
        if (config.isReturnNullTest()) {
            return null;
        } else if (config.isTestObjectClass(objectClass)) {
            return config.geObjectCache(objectClass).update(uid, replaceAttributes);
        } else {
            throw new UnsupportedOperationException("Object Update is not supported: "
                    + objectClass.getObjectClassValue());
        }
    }

    private final static SortedMap<String, ConnectorObject> collection =
            new TreeMap<String, ConnectorObject>(String.CASE_INSENSITIVE_ORDER);
    static {
        boolean enabled = true;
        for (int i = 0; i < 100; i++) {
            ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
            builder.setUid(String.valueOf(i));
            builder.setName(String.format("user%03d", i));
            builder.addAttribute(AttributeBuilder.buildEnabled(enabled));
            Map<String, Object> mapAttribute = new HashMap<String, Object>();
            mapAttribute.put("email", "foo@example.com");
            mapAttribute.put("primary", true);
            mapAttribute.put("usage", Arrays.asList("home", "work"));
            builder.addAttribute(AttributeBuilder.build("emails", mapAttribute));
            ConnectorObject co = builder.build();
            collection.put(co.getName().getNameValue(), co);
            enabled = !enabled;
        }
    }

    private static abstract class SelfAwareExecutionRunnable implements Runnable {
        private final AtomicInteger runCount = new AtomicInteger();
        private volatile ScheduledFuture<?> self;
        private AtomicBoolean running = new AtomicBoolean(Boolean.TRUE);

        public void run() {
            if (!doAction(runCount.incrementAndGet())) {
                cancel();
            }
        }

        protected abstract boolean doAction(int runCount);

        public void runAction(final ScheduledExecutorService executor, long initialDelay,
                long period, TimeUnit unit) {
            self = executor.scheduleAtFixedRate(this, initialDelay, period, unit);
        }

        public void cancel() {
            if (running.compareAndSet(Boolean.TRUE, Boolean.FALSE)) {
                self.cancel(false);
            }
        }

        public Boolean getRunning() {
            return running.get();
        }
    }

    BatchUseCase2Processor processorUseCase2 = null;
    BatchUseCase3Processor processorUseCase3 = null;

    public Subscription executeBatch(final List<BatchTask> tasks, final Observer<BatchResult> observer,
                                   final OperationOptions options) {
        if (config.isReturnNullTest()) {
            return null;
        }

        if (options.getOptions().containsKey("TEST_USECASE2")) {
            processorUseCase2 = new BatchUseCase2Processor();
            final BatchToken token = processorUseCase2.executeBatch(tasks, options);
            observer.onCompleted();
            return new Subscription() {
                public void close() {}

                public boolean isUnsubscribed() {
                    return true;
                }

                public Object getReturnValue() {
                    return token;
                }
            };
        } else if (options.getOptions().containsKey("TEST_USECASE3")) {
            processorUseCase3 = new BatchUseCase3Processor(this, config);
            final BatchToken token = processorUseCase3.executeBatch(tasks, options, observer);

            return new Subscription() {
                public void close() {}

                public boolean isUnsubscribed() {
                    return true;
                }

                public Object getReturnValue() {
                    return token;
                }
            };
        } else /* Use Case 1 */ {
            boolean complete = false;
            int failId = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                    ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION") - 1
                    : Integer.MAX_VALUE;
            for (int i = 0; i < tasks.size() && !complete; i++) {
                BatchTask task = tasks.get(i);
                try {
                    complete = (i == tasks.size() - 1);
                    Object result;
                    if (task instanceof DeleteBatchTask) {
                        result = new BatchEmptyResult(task.getClass().toString() + " successful");
                    } else {
                        result = new Uid(String.valueOf(i));
                    }
                    observer.onNext(new BatchResult(result, null, String.valueOf(i), complete, i == failId));
                    if (i == failId && options.getFailOnError()) {
                        break;
                    }
                } catch (RuntimeException e) {
                    observer.onNext(new BatchResult(e, null, String.valueOf(i), complete, true));
                    observer.onError(e);
                    if (options.getFailOnError()) {
                        break;
                    }
                }
            }
            observer.onCompleted();
            return new Subscription() {
                public void close() {}

                public boolean isUnsubscribed() {
                    return true;
                }

                public Object getReturnValue() {
                    return new BatchToken();
                }
            };
        }
    }

    public Subscription queryBatch(final BatchToken batchToken, final Observer<BatchResult> observer,
                                 final OperationOptions options) {
        final AtomicBoolean opComplete = new AtomicBoolean(false);

        if (config.isReturnNullTest()) {
            return null;
        }

        Subscription ret = new Subscription() {
            public void close() {}

            public boolean isUnsubscribed() {
                return true;
            }

            public Object getReturnValue() {
                return opComplete.get() ? new BatchToken() : batchToken;
            }
        };

        if (options.getOptions().containsKey("TEST_USECASE0")
                || options.getOptions().containsKey("TEST_USECASE1")) {
            opComplete.set(true);
            observer.onCompleted();
            return ret;
        } else if (options.getOptions().containsKey("TEST_USECASE3")) {
            return ret;
        } else { // TEST_USECASE2
            opComplete.set(true);
            boolean hasResults = false;
            for (String token : batchToken.getTokens()) {
                List<BatchRemoteCache.CachedBatchResult> results = BatchRemoteCache.getAndResetResults(token);
                boolean lastResult = true;
                for (BatchRemoteCache.CachedBatchResult result : results) {
                    hasResults = true;
                    lastResult = result.complete || (result.error && options.getFailOnError());
                    observer.onNext(new BatchResult(result.result, batchToken, result.resultId,
                            lastResult, result.error));
                    if (result.error) {
                        if (result.result instanceof RuntimeException) {
                            observer.onError((RuntimeException) result.result);
                        }
                        if (options.getFailOnError()) {
                            opComplete.set(true);
                            observer.onCompleted();
                            return ret;
                        }
                    }
                    opComplete.set(opComplete.get() && lastResult);
                }
                if (lastResult) {
                    BatchRemoteCache.flushResults(token);
                    batchToken.removeToken(token);
                } else {
                    batchToken.setQueryRequired(true);
                }
            }
            if (batchToken.getTokens().size() == 0) {
                batchToken.setAsynchronousResults(false);
                batchToken.setQueryRequired(false);
                batchToken.setReturnsResults(hasResults);
            }
            observer.onCompleted();
            return ret;
        }
    }

    /**
     * Simulates Use Case 2: Batch submitted to remote resource and token returned immediately. Results gathered
     * via queryBatch() calls using the token.  Results are returned before returning the token with each call.
     */
    private class BatchUseCase2Processor extends Thread {
        private OperationOptions options;
        private String token;

        public BatchToken executeBatch(List<BatchTask> tasks, OperationOptions options) {
            this.token = UUID.randomUUID().toString();
            this.options = new OperationOptions(options.getOptions());
            BatchRemoteCache.addTasks(token, tasks);
            start();
            try {
                // Simulate a query return trip to a remote resource to ensure the batch task processing has been
                // initiated before we return a token
                sleep(1000);
            } catch (Exception e) {}
            BatchToken tok = new BatchToken(token);
            tok.setQueryRequired(true);
            tok.setReturnsResults(false);
            return tok;
        }

        public void run() {
            List<BatchTask> tasks = BatchRemoteCache.getTasks(token);
            int failId = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                    ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION") - 1
                    : -1;

            for (int i = 0; i < tasks.size(); i++) {
                BatchTask task = tasks.get(i);
                try {
                    sleep(1);
                    Object result;
                    try {
                        if (i == failId) {
                            throw new ConnectorException(task.getClass().toString() + " failed");
                        }
                        if (task instanceof DeleteBatchTask) {
                            config.geObjectCache(task.getObjectClass()).delete(((DeleteBatchTask) task).getUid());
                            result = new BatchEmptyResult("Delete successful");
                        } else if (task instanceof UpdateBatchTask) {
                            result = config.geObjectCache(task.getObjectClass()).update(
                                    ((UpdateBatchTask) task).getUid(),
                                    ((UpdateBatchTask) task).getAttributes());
                        } else if (task instanceof CreateBatchTask) {
                            result = config.geObjectCache(task.getObjectClass()).create(
                                    ((CreateBatchTask) task).getCreateAttributes());
                        } else {
                            throw new UnsupportedOperationException(String.format(
                                    "Operation %s is not permitted", task.getClass().toString()));
                        }
                        BatchRemoteCache.addResult(token, new BatchRemoteCache.CachedBatchResult(
                                i == tasks.size() - 1, false, String.valueOf(i), result));
                    } catch (Exception e) {
                        BatchRemoteCache.addResult(token, new BatchRemoteCache.CachedBatchResult(
                                i == tasks.size() - 1, true, String.valueOf(i), new BatchEmptyResult(e.getMessage())));
                        if (options.getFailOnError()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // interrupted
                }
            }

            BatchRemoteCache.flushTasks(token);
        }
    }

    /**
     * Simulated Use Case 3: execute returns a batch token immediately and processes results in a separate thread.
     * No need to call queryBatch() unless the caller is interrupted and needs to reestablish the connection.
     */
    private class BatchUseCase3Processor extends Thread {
        private OperationOptions options;
        private Observer<BatchResult> observer;
        private BatchToken token;
        private final TstStatefulConnectorConfig config;

        public BatchUseCase3Processor(TstAbstractConnector connector, TstStatefulConnectorConfig config) {
            this.token = new BatchToken(UUID.randomUUID().toString());
            this.config = config;
            this.config.getBatchConnector().put(token, connector);
        }

        public BatchToken executeBatch(List<BatchTask> tasks, OperationOptions options,
                                       Observer<BatchResult> observer) {
            this.options = new OperationOptions(options.getOptions());
            this.observer = observer;
            BatchRemoteCache.addTasks(token.getTokens().get(0), tasks);
            this.start();
            token.setAsynchronousResults(true);
            return token;
        }

        public void run() {
            try {
                List<BatchTask> tasks = BatchRemoteCache.getTasks(token.getTokens().get(0));
                int failId = options.getOptions().containsKey("FAIL_TEST_ITERATION")
                        ? (Integer) options.getOptions().get("FAIL_TEST_ITERATION") - 1
                        : -1;

                for (int i = 0; i < tasks.size(); i++) {
                    BatchTask task = tasks.get(i);
                    try {
                        sleep(1);
                        Object result;
                        boolean complete = (i == tasks.size() - 1);
                        try {
                            if (i == failId) {
                                throw new ConnectorException(task.getClass().toString() + " failed");
                            }
                            if (task instanceof DeleteBatchTask) {
                                config.geObjectCache(task.getObjectClass())
                                        .delete(((DeleteBatchTask) task).getUid());
                                result = new BatchEmptyResult("Delete successful");
                            } else if (task instanceof UpdateBatchTask) {
                                result = config.geObjectCache(task.getObjectClass()).update(
                                        ((UpdateBatchTask) task).getUid(),
                                        ((UpdateBatchTask) task).getAttributes());
                            } else if (task instanceof CreateBatchTask) {
                                result = config.geObjectCache(task.getObjectClass()).create(
                                        ((CreateBatchTask) task).getCreateAttributes());
                            } else {
                                throw new UnsupportedOperationException(String.format(
                                        "Operation %s is not permitted", task.getClass().toString()));
                            }
                            observer.onNext(new BatchResult(result, token, String.valueOf(i), complete, false));
                        } catch (Exception e) {
                            observer.onNext(new BatchResult(new BatchEmptyResult(e.getMessage()), token,
                                    String.valueOf(i), complete, true));
                            if (options.getFailOnError()) {
                                BatchRemoteCache.setComplete(token.getTokens().get(0));
                                observer.onCompleted();
                                break;
                            }
                        }

                        if (complete) {
                            BatchRemoteCache.setComplete(token.getTokens().get(0));
                            observer.onCompleted();
                        }
                    } catch (Exception e) {
                        // interrupted
                    }
                }
            } finally {
                BatchRemoteCache.flushTasks(token.getTokens().get(0));
                config.getBatchConnector().remove(token);
            }
        }
    }
}
