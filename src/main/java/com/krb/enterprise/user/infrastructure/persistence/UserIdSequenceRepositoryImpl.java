package com.krb.enterprise.user.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.krb.enterprise.user.domain.UserIdSequenceRepository;
import com.krb.enterprise.user.domain.UserRole;

@Repository
public class UserIdSequenceRepositoryImpl
        implements UserIdSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserIdSequenceRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long nextValue(UserRole role) {

        return jdbcTemplate.queryForObject(
                """
                        UPDATE user_id_sequence
                        SET next_value = next_value + 1
                        WHERE user_role = ?
                        RETURNING next_value - 1
                        """,
                Long.class,
                role.name());
    }
}