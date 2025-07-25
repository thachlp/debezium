/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.postgresql;

import static io.debezium.connector.postgresql.TestHelper.topicName;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.debezium.config.Configuration;
import io.debezium.data.Envelope;
import io.debezium.doc.FixFor;
import io.debezium.embedded.async.AbstractAsyncEngineConnectorTest;
import io.debezium.relational.RelationalDatabaseConnectorConfig.DecimalHandlingMode;

/**
 * Integration test to verify postgres money types defined in public schema.
 *
 * @author Harvey Yue
 */
public class PostgresMoneyIT extends AbstractAsyncEngineConnectorTest {

    @Before
    public void before() throws Exception {
        initializeConnectorTestFramework();
        TestHelper.dropAllSchemas();
    }

    @After
    public void after() {
        stopConnector();
        TestHelper.dropDefaultReplicationSlot();
        TestHelper.dropPublication();
    }

    @Test
    @FixFor("DBZ-5991")
    public void shouldReceiveChangesForInsertsWithPreciseMode() throws Exception {
        createTable();

        Configuration config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.NO_DATA)
                .build();
        start(PostgresConnector.class, config);
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // insert 2 records for testing
        insertTwoRecords();

        final SourceRecords records = consumeRecordsByTopic(2);
        final List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(2);

        Struct after = ((Struct) recordsForTopic.get(0).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo(new BigDecimal("-92233720368547758.08"));
        after = ((Struct) recordsForTopic.get(1).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo(new BigDecimal("92233720368547758.07"));
    }

    @Test
    @FixFor("DBZ-5991")
    public void shouldReceiveChangesForInsertsWithStringMode() throws Exception {
        createTable();

        Configuration config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.NO_DATA)
                .with(PostgresConnectorConfig.DECIMAL_HANDLING_MODE, "string")
                .build();
        start(PostgresConnector.class, config);
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // insert 2 records for testing
        insertTwoRecords();

        final SourceRecords records = consumeRecordsByTopic(2);
        final List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(2);

        Struct after = ((Struct) recordsForTopic.get(0).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo("-92233720368547758.08");
        after = ((Struct) recordsForTopic.get(1).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo("92233720368547758.07");
    }

    @Test
    @FixFor("DBZ-5991")
    public void shouldReceiveChangesForInsertsWithDoubleMode() throws Exception {
        createTable();

        Configuration config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.NO_DATA)
                .with(PostgresConnectorConfig.DECIMAL_HANDLING_MODE, "double")
                .build();
        start(PostgresConnector.class, config);
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // insert 2 records for testing
        insertTwoRecords();

        final SourceRecords records = consumeRecordsByTopic(2);
        final List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(2);

        Struct after = ((Struct) recordsForTopic.get(0).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo(-92233720368547758.00);
        after = ((Struct) recordsForTopic.get(1).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo(92233720368547758.00);
    }

    @Test
    @FixFor("DBZ-6001")
    public void shouldReceiveChangesForInsertNullAndZeroMoney() throws Exception {
        createTable();

        Configuration config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.NO_DATA)
                .build();
        start(PostgresConnector.class, config);
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // insert 2 records for testing
        TestHelper.execute("insert into post_money.debezium_test(id, m) values(10, null), (11, '0.00'::money);");

        final SourceRecords records = consumeRecordsByTopic(2);
        final List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(2);
        Struct after = ((Struct) recordsForTopic.get(0).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isNull();
        after = ((Struct) recordsForTopic.get(1).value()).getStruct(Envelope.FieldName.AFTER);
        assertThat(after.get("m")).isEqualTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @FixFor("DBZ-8027")
    public void shouldReceiveCorrectDefaultValueForHandlingMode() throws Exception {
        createTableWithNotNull();
        TestHelper.execute("insert into post_money.debezium_test(id, m) values(10, '3.14'::money);");

        var config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.ALWAYS)
                .with(PostgresConnectorConfig.DROP_SLOT_ON_STOP, Boolean.FALSE)
                .with(PostgresConnectorConfig.DECIMAL_HANDLING_MODE, DecimalHandlingMode.STRING)
                .with(PostgresConnectorConfig.SNAPSHOT_SELECT_STATEMENT_OVERRIDES_BY_TABLE, "post_money.debezium_test")
                .with(PostgresConnectorConfig.SNAPSHOT_SELECT_STATEMENT_OVERRIDES_BY_TABLE + ".post_money.debezium_test",
                        "SELECT id, null AS m FROM post_money.debezium_test")
                .build();
        start(PostgresConnector.class, config);

        var records = consumeRecordsByTopic(1);
        var recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(1);
        assertThat(((Struct) recordsForTopic.get(0).value()).getStruct("after").getString("m")).isEqualTo("0.00");

        stopConnector();

        config = TestHelper.defaultConfig()
                .with(PostgresConnectorConfig.SNAPSHOT_MODE, PostgresConnectorConfig.SnapshotMode.ALWAYS)
                .with(PostgresConnectorConfig.DECIMAL_HANDLING_MODE, DecimalHandlingMode.DOUBLE)
                .with(PostgresConnectorConfig.SNAPSHOT_SELECT_STATEMENT_OVERRIDES_BY_TABLE, "post_money.debezium_test")
                .with(PostgresConnectorConfig.SNAPSHOT_SELECT_STATEMENT_OVERRIDES_BY_TABLE + ".post_money.debezium_test",
                        "SELECT id, null AS m FROM post_money.debezium_test")
                .build();
        start(PostgresConnector.class, config);

        records = consumeRecordsByTopic(1);
        recordsForTopic = records.recordsForTopic(topicName("post_money.debezium_test"));

        assertThat(recordsForTopic).hasSize(1);
        assertThat(((Struct) recordsForTopic.get(0).value()).getStruct("after").getFloat64("m")).isEqualTo(0.0);
    }

    private void createTable() {
        TestHelper.execute(
                "DROP SCHEMA IF EXISTS post_money CASCADE;",
                "CREATE SCHEMA post_money;",
                "CREATE TABLE post_money.debezium_test (id int4 NOT NULL, m money, CONSTRAINT dbz_test_pkey PRIMARY KEY (id));");
    }

    private void createTableWithNotNull() {
        TestHelper.execute(
                "DROP SCHEMA IF EXISTS post_money CASCADE;",
                "CREATE SCHEMA post_money;",
                "CREATE TABLE post_money.debezium_test (id int4 NOT NULL, m money NOT NULL, CONSTRAINT dbz_test_pkey PRIMARY KEY (id));");
    }

    private void insertTwoRecords() {
        TestHelper.execute("insert into post_money.debezium_test(id, m) values(8, -92233720368547758.08),(9, 92233720368547758.07);");
    }
}
