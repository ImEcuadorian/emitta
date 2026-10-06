package io.github.imecuadorian.emitta.documentsequence.adapter.out.persistence;

import io.github.imecuadorian.emitta.documentsequence.application.port.out.SequenceAllocationPort;
import io.github.imecuadorian.emitta.documentsequence.domain.SequenceExhaustedException;
import io.github.imecuadorian.emitta.documentsequence.domain.SequenceScope;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgreSqlSequenceAllocationAdapter
        implements SequenceAllocationPort {

    private static final String ALLOCATE_SQL = """
            INSERT INTO emitta.document_sequences AS ds (
                point_of_issue_id,
                document_type,
                environment,
                current_value,
                updated_at
            )
            VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP)

            ON CONFLICT (
                point_of_issue_id,
                document_type,
                environment
            )
            DO UPDATE
            SET
                current_value = ds.current_value + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE ds.current_value < 999999999

            RETURNING current_value
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlSequenceAllocationAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SequentialNumber allocateNext(
            SequenceScope scope
    ) {

        List<Long> values =
                jdbcTemplate.query(
                        ALLOCATE_SQL,
                        (resultSet, rowNumber) ->
                                resultSet.getLong(
                                        "current_value"
                                ),
                        scope.pointOfIssueId(),
                        scope.documentType().name(),
                        scope.environment().name()
                );

        if (values.isEmpty()) {
            throw new SequenceExhaustedException(
                    scope
            );
        }

        return new SequentialNumber(
                values.get(0)
        );
    }
}