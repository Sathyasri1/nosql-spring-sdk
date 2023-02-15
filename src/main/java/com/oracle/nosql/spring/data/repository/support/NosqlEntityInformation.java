/*-
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.  All rights reserved.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 *  https://oss.oracle.com/licenses/upl/
 */
package com.oracle.nosql.spring.data.repository.support;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oracle.nosql.driver.Consistency;
import oracle.nosql.driver.Durability;
import oracle.nosql.driver.ops.TableLimits;
import oracle.nosql.driver.values.FieldValue;

import com.oracle.nosql.spring.data.Constants;
import com.oracle.nosql.spring.data.NosqlDbFactory;
import com.oracle.nosql.spring.data.core.NosqlTemplateBase;
import com.oracle.nosql.spring.data.core.mapping.NosqlCapacityMode;
import com.oracle.nosql.spring.data.core.mapping.NosqlId;
import com.oracle.nosql.spring.data.core.mapping.NosqlTable;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.core.support.AbstractEntityInformation;
import org.springframework.data.spel.EvaluationContextProvider;
import org.springframework.data.spel.ExtensionAwareEvaluationContextProvider;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ReflectionUtils;

public class NosqlEntityInformation <T, ID> extends
    AbstractEntityInformation<T, ID> {

    private ApplicationContext applicationContext;
    private Field id;
    private String tableName;
    private boolean autoCreateTable;
    private TableLimits tableLimits;
    private boolean autoGeneratedId;
    private Consistency consistency;
    private Durability durability;
    private int timeout;
    private FieldValue.Type idNosqlType;
    private boolean useDefaultTableLimits = false;
//    private boolean isComposite;

    public NosqlEntityInformation(ApplicationContext applicationContext,
                                  Class<T> domainClass) {
        super(domainClass);

        this.applicationContext = applicationContext;
        this.id = getIdField(domainClass);
        ReflectionUtils.makeAccessible(this.id);
        idNosqlType = findIdNosqlType();

        final NosqlId nosqlIdAnn = id.getAnnotation(NosqlId.class);
        if (nosqlIdAnn != null && nosqlIdAnn.generated()) {
            FieldValue.Type nosqlKeyType = getIdNosqlType();
            switch (nosqlKeyType) {
            case LONG:
            case INTEGER:
            case NUMBER:
            case STRING:
                autoGeneratedId = true;
                break;
            default:
                throw new IllegalArgumentException("Type not supported to be " +
                    "autogenerated: " + id.getType() + ". Only int, long, " +
                    "Integer, Long, BigInteger, BigDecimal and String are " +
                    "supported.");
            }
        }

        setTableOptions(domainClass);
    }

    @SuppressWarnings("unchecked")
    public ID getId(T entity) {
        return (ID) ReflectionUtils.getField(id, entity);
    }

    public Field getIdField() {
        return this.id;
    }

    public String getIdColumnName() {
        return id.getName();
    }

    @SuppressWarnings("unchecked")
    public Class<ID> getIdType() {
        return (Class<ID>) id.getType();
    }

    public FieldValue.Type getIdNosqlType() {
        return idNosqlType;
    }

    private FieldValue.Type findIdNosqlType() {
        Class<ID> idClass = getIdType();
        if (idClass == String.class) {
            return FieldValue.Type.STRING;
        }
        if (idClass == int.class || idClass == Integer.class) {
            return FieldValue.Type.INTEGER;
        }
        if (idClass == long.class || idClass == Long.class) {
            return FieldValue.Type.LONG;
        }
        if (idClass == float.class || idClass == Float.class ||
            idClass == double.class || idClass == Double.class) {
            return FieldValue.Type.DOUBLE;
        }
        if (idClass == BigInteger.class || idClass == BigDecimal.class) {
            return FieldValue.Type.NUMBER;
        }
        if (idClass == Timestamp.class || idClass == Date.class || idClass ==
            Instant.class) {
            return FieldValue.Type.TIMESTAMP;
        }
        throw new IllegalStateException("Unsupported ID type.");
    }

    public String getTableName() {
        return this.tableName;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public boolean isAutoGeneratedId() {
        return autoGeneratedId;
    }

    private Field getIdField(Class<?> domainClass) {
        Field idField;

        final List<Field> idFields = FieldUtils.getFieldsListWithAnnotation(
            domainClass, Id.class);

        if (idFields.isEmpty()) {
            idField = ReflectionUtils.findField(getJavaType(),
                Constants.ID_PROPERTY_NAME);
        } else if (idFields.size() == 1) {
            idField = idFields.get(0);
        } else {
            throw new IllegalArgumentException("Only one field can be with " +
                "@Id annotation in " + domainClass.getName());
        }

        final List<Field> nsFields =
            FieldUtils.getFieldsListWithAnnotation(domainClass, NosqlId.class);

        if (nsFields.size() == 1) {
            idField = nsFields.get(0);
        } else if (nsFields.size() > 1) {
            throw new IllegalArgumentException("Only one field in class " +
                domainClass + " can be with @NosqlId annotation.");
        }

        if (!idFields.isEmpty() && !nsFields.isEmpty()) {
            throw new IllegalArgumentException("Only one of @Id or @NosqlId " +
                "annotation can be used on entity class: " +
                domainClass.getCanonicalName());
        }

        if (idField == null) {
            throw new IllegalArgumentException("Entity should contain @Id or " +
                "@NosqlId annotated field or field named id: " +
                domainClass.getName());
        } else if (idField.getType() != String.class &&
            idField.getType() != Integer.class &&
            idField.getType() != int.class &&
            idField.getType() != Long.class &&
            idField.getType() != long.class &&
            idField.getType() != Float.class &&
            idField.getType() != float.class &&
            idField.getType() != Double.class &&
            idField.getType() != double.class &&
            idField.getType() != BigInteger.class &&
            idField.getType() != BigDecimal.class &&
            idField.getType() != Timestamp.class &&
            idField.getType() != Date.class &&
            idField.getType() != Instant.class
            //todo: implement composite keys
        ) {
            throw new IllegalArgumentException("Id field must be of " +
                "type java.lang.String, int, java.lang.Integer, long, " +
                "java.lang.Long, java.math.BigInteger, java.math.BigDecimal, " +
                "java.sql.Timestamp, java.util.Date or java.time.Instant in " +
                domainClass.getName());
        }

        if (NosqlTemplateBase.JSON_COLUMN.equals(idField.getName())) {
            throw new IllegalArgumentException("Id field can not be named '" +
                NosqlTemplateBase.JSON_COLUMN + "' in " + domainClass.getName());
        }

        return idField;
    }


    private void setTableOptions(Class<T> domainClass) {
        autoCreateTable = Constants.DEFAULT_AUTO_CREATE_TABLE;
        tableLimits = null;
        consistency = Consistency.EVENTUAL;
        durability = Durability.COMMIT_NO_SYNC;
        timeout = Constants.NOTSET_TABLE_TIMEOUT_MS;
        tableName = domainClass.getSimpleName();
        if (domainClass.isArray()) {
            tableName = domainClass.getComponentType().getSimpleName() +
                "_Array";
        }

        final NosqlTable annotation =
            domainClass.getAnnotation(NosqlTable.class);

        if (annotation != null) {
            autoCreateTable = annotation.autoCreateTable();

            // If storageGB is 0 or less than -1 no tableLimits are set.
            if (annotation.capacityMode() == NosqlCapacityMode.PROVISIONED &&
               (annotation.storageGB() > 0 || annotation.storageGB() ==
                   Constants.NOTSET_TABLE_STORAGE_GB )) {
                tableLimits = new TableLimits(annotation.readUnits(),
                    annotation.writeUnits(), annotation.storageGB());
            } else if (annotation.capacityMode() == NosqlCapacityMode.ON_DEMAND
                && (annotation.storageGB() > 0  || annotation.storageGB() ==
                Constants.NOTSET_TABLE_STORAGE_GB ))
            {
                tableLimits = new TableLimits(annotation.storageGB());
            }

            setConsistency(annotation.consistency());
            setDurability(annotation.durability());

            timeout = annotation.timeout();

            if (!annotation.tableName().isEmpty()) {
                tableName = annotation.tableName();

                Environment environment = null;
                if (applicationContext != null) {
                    environment = applicationContext.getEnvironment();
                }

                // to evaluate against application.properties
                if (tableName.contains("$") && environment != null) {
                    tableName = environment.resolvePlaceholders(tableName);
                    System.out.println("appCtx resolve $: " + tableName);
                }

                // to evaluate against SpEl and environment/system properties
                if (tableName.contains("#")) {
                    SpelExpressionParser PARSER = new SpelExpressionParser();
                    Expression expression = PARSER.parseExpression(tableName, ParserContext.TEMPLATE_EXPRESSION);
                    if (!(expression instanceof LiteralExpression)) {
                        EvaluationContextProvider evalCtxProvider = //EvaluationContextProvider.DEFAULT;
                            new ExtensionAwareEvaluationContextProvider(applicationContext);
                        tableName = expression.getValue(evalCtxProvider.getEvaluationContext(environment), String.class);
                    }
                    System.out.println("appCtx resolve #: " + tableName);
                }
                tableName = tableName.trim();
                if (tableName.startsWith(":")) {
                    tableName = tableName.substring(1);
                }
            }
        } else {
            // No annotation exists, use the values set in NosqlDbConfig
            useDefaultTableLimits = true;
        }
    }

    public static class CachingExpressionParser implements ExpressionParser {

        private final ExpressionParser delegate;
        private final Map<String, Expression> cache = new ConcurrentHashMap<>();

        CachingExpressionParser(ExpressionParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public Expression parseExpression(String expressionString) throws ParseException {
            return cache.computeIfAbsent(expressionString, delegate::parseExpression);
        }

        @Override
        public Expression parseExpression(String expressionString, ParserContext context) throws ParseException {
            throw new UnsupportedOperationException("Parsing using ParserContext is not supported");
        }
    }

    private Durability getDurability(String durability) {
        if ("COMMIT_SYNC".equals(durability.toUpperCase())) {
            return Durability.COMMIT_SYNC;
        }
        if ("COMMIT_WRITE_NO_SYNC".equals(durability.toUpperCase())) {
            return Durability.COMMIT_WRITE_NO_SYNC;
        }
        return Durability.COMMIT_NO_SYNC;
    }

    public TableLimits getTableLimits(NosqlDbFactory nosqlDbFactory) {
        // set table limits for when no annotation exists
        if (useDefaultTableLimits) {
            if (nosqlDbFactory.getDefaultCapacityMode() ==
                NosqlCapacityMode.ON_DEMAND) {
                return new TableLimits(nosqlDbFactory.getDefaultStorageGB());
            } else {
                return new TableLimits(nosqlDbFactory.getDefaultReadUnits(),
                    nosqlDbFactory.getDefaultWriteUnits(),
                    nosqlDbFactory.getDefaultStorageGB());
            }
        }

        if (tableLimits != null) {
            if (tableLimits.getStorageGB() == Constants.NOTSET_TABLE_STORAGE_GB) {
                tableLimits.setStorageGB(nosqlDbFactory.getDefaultStorageGB());
            }
            if (tableLimits.getReadUnits() == Constants.NOTSET_TABLE_READ_UNITS) {
                tableLimits.setReadUnits(nosqlDbFactory.getDefaultReadUnits());
            }
            if (tableLimits.getWriteUnits() == Constants.NOTSET_TABLE_WRITE_UNITS) {
                tableLimits.setWriteUnits(nosqlDbFactory.getDefaultWriteUnits());
            }
        }

        return tableLimits;
    }

    public Consistency getConsistency() {
        return consistency;
    }

    public void setConsistency(String consistency) {
        this.consistency = Consistency.valueOf(consistency);
    }

    public Durability getDurability() {
        return durability;
    }

    public void setDurability(String durability) {
        this.durability = getDurability(durability);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Timeout cannot be a negative " +
                "value.");
        }
        timeout = milliseconds;
    }
}
