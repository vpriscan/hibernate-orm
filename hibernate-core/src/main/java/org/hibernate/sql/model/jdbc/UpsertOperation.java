/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.sql.model.jdbc;

import java.util.List;

import org.hibernate.jdbc.Expectations;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.TableMapping;

/**
 * JdbcMutation implementation for MERGE/UPSERT handling
 *
 * @author Steve Ebersole
 */
public class UpsertOperation extends AbstractJdbcMutation {
	public UpsertOperation(
			TableMapping tableDetails,
			MutationTarget<?> mutationTarget,
			String sql,
			List<? extends JdbcParameterBinder> parameterBinders) {
		super( tableDetails, mutationTarget, sql, false, Expectations.NONE, parameterBinders );
	}

	@Override
	public MutationType getMutationType() {
		return MutationType.UPDATE;
	}


}
