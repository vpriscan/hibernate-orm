package org.hibernate.orm.test.timezones;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.SybaseDialect;
import org.hibernate.type.descriptor.DateTimeUtils;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DomainModel(annotatedClasses = JDBCTimeZoneZonedTest.Zoned.class)
@SessionFactory
@ServiceRegistry(settings = {@Setting(name = AvailableSettings.TIMEZONE_DEFAULT_STORAGE, value = "NORMALIZE"),
							@Setting(name = AvailableSettings.JDBC_TIME_ZONE, value = "GMT+5")})
public class JDBCTimeZoneZonedTest {

	@Test void test(SessionFactoryScope scope) {
		ZonedDateTime nowZoned = ZonedDateTime.now().withZoneSameInstant( ZoneId.of("CET") );
		OffsetDateTime nowOffset = OffsetDateTime.now().withOffsetSameInstant( ZoneOffset.ofHours( 3) );
		long id = scope.fromTransaction( s-> {
			Zoned z = new Zoned();
			z.zonedDateTime = nowZoned;
			z.offsetDateTime = nowOffset;
			s.persist(z);
			return z.id;
		});
		scope.inSession( s-> {
			Zoned z = s.find(Zoned.class, id);
			ZoneId systemZone = ZoneId.systemDefault();
			ZoneOffset systemOffset = systemZone.getRules().getOffset( Instant.now() );
			final Dialect dialect = scope.getSessionFactory().getJdbcServices().getDialect();
			if ( dialect instanceof SybaseDialect) {
				// Sybase with jTDS driver has 1/300th sec precision
				assertEquals( nowZoned.toInstant().truncatedTo(ChronoUnit.SECONDS), z.zonedDateTime.toInstant().truncatedTo(ChronoUnit.SECONDS));
				assertEquals( nowOffset.toInstant().truncatedTo(ChronoUnit.SECONDS), z.offsetDateTime.toInstant().truncatedTo(ChronoUnit.SECONDS));
			}
			else {
				assertEquals(
						DateTimeUtils.roundToDefaultPrecision( nowZoned.toInstant(), dialect ),
						DateTimeUtils.roundToDefaultPrecision( z.zonedDateTime.toInstant(), dialect )
				);
				assertEquals(
						DateTimeUtils.roundToDefaultPrecision( nowOffset.toInstant(), dialect ),
						DateTimeUtils.roundToDefaultPrecision( z.offsetDateTime.toInstant(), dialect )
				);
			}
			assertEquals( systemZone, z.zonedDateTime.getZone() );
			assertEquals( systemOffset, z.offsetDateTime.getOffset() );
		});
	}

	@Entity(name = "Zoned")
	public static class Zoned {
		@Id
		@GeneratedValue Long id;
		ZonedDateTime zonedDateTime;
		OffsetDateTime offsetDateTime;
	}
}
