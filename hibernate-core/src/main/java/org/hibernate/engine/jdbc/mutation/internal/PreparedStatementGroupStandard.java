/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.engine.jdbc.mutation.internal;

import java.sql.PreparedStatement;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.hibernate.engine.jdbc.mutation.group.PreparedStatementDetails;
import org.hibernate.engine.jdbc.mutation.group.PreparedStatementGroup;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.MutationStatementPreparer;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.mutation.EntityMutationTarget;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.PreparableMutationOperation;
import org.hibernate.sql.model.TableMapping;

/**
 * A group of {@link PreparedStatementDetails} references related to multi-table
 * entity mappings.  The statements are keyed by each table-names.
 *
 * @author Steve Ebersole
 */
public class PreparedStatementGroupStandard implements PreparedStatementGroup {
	private final MutationType mutationType;
	private final MutationTarget<?> mutationTarget;
	private final List<PreparableMutationOperation> jdbcMutations;
	private final SharedSessionContractImplementor session;

	private final SortedMap<String, PreparedStatementDetails> statementMap;


	public PreparedStatementGroupStandard(
			MutationType mutationType,
			MutationTarget<?> mutationTarget,
			List<PreparableMutationOperation> jdbcMutations,
			SharedSessionContractImplementor session) {
		this.mutationType = mutationType;
		this.mutationTarget = mutationTarget;
		this.jdbcMutations = jdbcMutations;

		this.session = session;

		this.statementMap = createStatementDetailsMap( jdbcMutations, mutationType, mutationTarget, session );
	}

	@Override
	public int getNumberOfStatements() {
		return jdbcMutations.size();
	}

	@Override
	public int getNumberOfActiveStatements() {
		int count = 0;
		for ( Map.Entry<String, PreparedStatementDetails> entry : statementMap.entrySet() ) {
			if ( entry.getValue().getStatement() != null ) {
				count++;
			}
		}
		return count;
	}

	@Override
	public PreparedStatementDetails getSingleStatementDetails() {
		throw new IllegalStateException(
				String.format(
						Locale.ROOT,
						"Statement group contained more than one statement - %s : %s",
						mutationType.name(),
						mutationTarget.getNavigableRole().getFullPath()
				)
		);
	}

	@Override
	public void forEachStatement(BiConsumer<String, PreparedStatementDetails> action) {
		statementMap.forEach( action );
	}

	@Override
	public PreparedStatementDetails getPreparedStatementDetails(String tableName) {
		return statementMap.get( tableName );
	}

	@Override
	public PreparedStatementDetails resolvePreparedStatementDetails(String tableName) {
		return statementMap.get( tableName );
	}

	@Override
	public boolean hasMatching(Predicate<PreparedStatementDetails> filter) {
		for ( Map.Entry<String, PreparedStatementDetails> entry : statementMap.entrySet() ) {
			if ( filter.test( entry.getValue() ) ) {
				return true;
			}
		}
		return false;
	}

	private static PreparedStatementDetails createPreparedStatementDetails(
			PreparableMutationOperation jdbcMutation,
			MutationType mutationType,
			MutationTarget<?> mutationTarget,
			SharedSessionContractImplementor session) {
		final JdbcCoordinator jdbcCoordinator = session.getJdbcCoordinator();
		final MutationStatementPreparer statementPreparer = jdbcCoordinator.getMutationStatementPreparer();

		final TableMapping tableDetails = jdbcMutation.getTableDetails();

		final Supplier<PreparedStatement> jdbcStatementCreator;
		if ( mutationType == MutationType.INSERT
				&& mutationTarget instanceof EntityMutationTarget
				&& ( (EntityMutationTarget) mutationTarget ).getIdentityInsertDelegate() != null
				&& tableDetails.getTableName().equals( mutationTarget.getIdentifierTableName() ) ) {
			jdbcStatementCreator = () -> ( (EntityMutationTarget) mutationTarget ).getIdentityInsertDelegate().prepareStatement(
					jdbcMutation.getSqlString(),
					session
			);
		}
		else {
			jdbcStatementCreator = () -> statementPreparer.prepareStatement(
					jdbcMutation.getSqlString(),
					jdbcMutation.isCallable()
			);
		}

		return new PreparedStatementDetailsStandard(
				jdbcMutation,
				jdbcMutation.getSqlString(),
				jdbcStatementCreator,
				jdbcMutation.getExpectation(),
				session.getJdbcServices()
		);
	}

	@Override
	public void release() {
		statementMap.forEach( (tableName, statementDetails) -> statementDetails.releaseStatement( session ) );
		statementMap.clear();
	}


	private SortedMap<String, PreparedStatementDetails> createStatementDetailsMap(
			List<PreparableMutationOperation> jdbcMutations,
			MutationType mutationType,
			MutationTarget<?> mutationTarget,
			SharedSessionContractImplementor session) {
		final Comparator<String> comparator;

		if ( mutationType == MutationType.DELETE ) {
			// reverse order
			comparator = Comparator.comparingInt( (tableName) -> {
				final TableMapping tableMapping = locateTableMapping( tableName );
				if ( tableMapping == null ) {
					return -1;
				}
				return this.jdbcMutations.size() - tableMapping.getRelativePosition();
			} );
		}
		else {
			comparator = Comparator.comparingInt( (tableName) -> {
				final TableMapping tableMapping = locateTableMapping( tableName );
				if ( tableMapping == null ) {
					return -1;
				}
				return tableMapping.getRelativePosition();
			} );
		}

		final TreeMap<String, PreparedStatementDetails> map = new TreeMap<>( comparator );

		for ( int i = 0; i < jdbcMutations.size(); i++ ) {
			final PreparableMutationOperation jdbcMutation = jdbcMutations.get( i );
			map.put(
					jdbcMutation.getTableDetails().getTableName(),
					createPreparedStatementDetails( jdbcMutation, mutationType, mutationTarget, session )
			);
		}

		return map;
	}

	private TableMapping locateTableMapping(String name) {
		for ( int i = 0; i < jdbcMutations.size(); i++ ) {
			final TableMapping tableMapping = jdbcMutations.get( i ).getTableDetails();
			if ( tableMapping.getTableName().equals( name ) ) {
				return tableMapping;
			}
		}
		return null;
	}

}
