/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.jdbc.dialect.maria;

import java.util.Optional;

import org.apache.kafka.connect.data.Schema;

import io.debezium.connector.jdbc.type.debezium.AbstractDoubleVectorType;
import io.debezium.sink.column.ColumnDescriptor;

/**
 * An implementation of {@link AbstractDoubleVectorType} for Maria's {@code vector} data type.
 *
 * @author Pranav Tiwari
 */
public class DoubleVectorType extends AbstractDoubleVectorType {

    public static DoubleVectorType INSTANCE = new DoubleVectorType();

    @Override
    public String getTypeName(Schema schema, boolean isKey) {
        final Optional<String> size = getSourceColumnSize(schema);

        // MariaDB requires an explicit vector length, which may be unspecified in source databases like MySQL or Postgres.
        // Defaulting to 16383 to match MySQL’s vector default for double.
        return size.map(s -> String.format("vector(%s)", s)).orElse("vector(16383)");
    }

    @Override
    public String getQueryBinding(ColumnDescriptor column, Schema schema, Object value) {
        return "VEC_FromText(?)";
    }
}
