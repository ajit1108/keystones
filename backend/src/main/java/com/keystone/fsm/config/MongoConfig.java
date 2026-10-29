package com.keystone.fsm.config;

import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;

/**
 * Enables auditing and multi-document transactions.
 *
 * <p>MongoDB only supports multi-document transactions on replica sets or
 * sharded clusters. A standalone mongod rejects transaction numbers with
 * error 20, which would turn every {@code @Transactional} write into a 500.
 * The deployment topology is probed once at startup: replica sets and mongos
 * get the real {@link MongoTransactionManager}; a standalone server gets a
 * no-op manager so writes still work, without cross-document ACID guarantees
 * (a warning is logged). Point {@code MONGODB_URI} at Atlas or a replica set
 * in production.
 */
@Slf4j
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public PlatformTransactionManager transactionManager(MongoDatabaseFactory dbFactory, MongoClient mongoClient) {
        return supportsTransactions(mongoClient)
                ? new MongoTransactionManager(dbFactory)
                : noOpTransactionManager();
    }

    private boolean supportsTransactions(MongoClient client) {
        Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
        boolean replicaSet = hello.getString("setName") != null;
        boolean mongos = "isdbgrid".equals(hello.getString("msg"));
        return replicaSet || mongos;
    }

    private PlatformTransactionManager noOpTransactionManager() {
        log.warn("MongoDB deployment is standalone (no replica set); multi-document transactions are disabled. "
                + "Writes still work but without cross-document atomicity. Use Atlas or a replica set in production.");
        return new NoOpTransactionManager();
    }

    @Bean
    public GridFsTemplate gridFsTemplate(MongoDatabaseFactory dbFactory, MongoConverter converter) {
        return new GridFsTemplate(dbFactory, converter);
    }

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Collections.emptyList());
    }

    /** Runs each unit of work without a transaction; used only on standalone MongoDB. */
    static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus(true);
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }
}
