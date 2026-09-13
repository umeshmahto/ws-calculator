package org.egov.wscalculation.djbmonthlybilling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.egov.wscalculation.djbmonthlybilling.model.WaterBillingCycle;
import org.egov.wscalculation.djbmonthlybilling.repository.DJBMonthlyBillingQueryBuilder;
import org.egov.wscalculation.djbmonthlybilling.repository.WaterBillingCycleDaoImpl;
import org.egov.wscalculation.djbmonthlybilling.repository.rowmapper.WaterBillingCycleRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WaterBillingCycleDaoTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private DJBMonthlyBillingQueryBuilder queryBuilder;

	@Mock
	private WaterBillingCycleRowMapper rowMapper;

	private WaterBillingCycleDaoImpl dao;

	@BeforeEach
	void setUp() {
		dao = new WaterBillingCycleDaoImpl();

		ReflectionTestUtils.setField(dao, "jdbcTemplate", jdbcTemplate);
		ReflectionTestUtils.setField(dao, "queryBuilder", queryBuilder);
		ReflectionTestUtils.setField(dao, "rowMapper", rowMapper);
	}

	@Test
	void shouldFindBillingCycleByConnectionAndExactPeriod() {
		String tenantId = "dl";
		String connectionNo = "WS/DJB/2026-27/000367";
		Long from = 1780338600000L;
		Long to = 1783017000000L;

		WaterBillingCycle expected = cycle("CYCLE-001", tenantId, connectionNo, from, to);

		String sql = "SELECT billing cycle by connection and period";

		when(queryBuilder.findByConnectionAndPeriod()).thenReturn(sql);
		when(jdbcTemplate.query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(from), eq(to)))
				.thenReturn(Collections.singletonList(expected));

		WaterBillingCycle actual = dao.findByConnectionAndPeriod(tenantId, connectionNo, from, to);

		assertEquals(expected, actual);

		verify(jdbcTemplate).query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(from), eq(to));
	}

	@Test
	void shouldReturnNullWhenExactBillingPeriodDoesNotExist() {
		String tenantId = "dl";
		String connectionNo = "WS/DJB/2026-27/000367";
		Long from = 1780338600000L;
		Long to = 1783017000000L;

		String sql = "SELECT billing cycle by connection and period";

		when(queryBuilder.findByConnectionAndPeriod()).thenReturn(sql);
		when(jdbcTemplate.query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(from), eq(to)))
				.thenReturn(Collections.<WaterBillingCycle>emptyList());

		WaterBillingCycle actual = dao.findByConnectionAndPeriod(tenantId, connectionNo, from, to);

		assertNull(actual);
	}

	@Test
	void shouldFindLatestOkCycleBeforeCurrentPeriod() {
		String tenantId = "dl";
		String connectionNo = "WS/DJB/2026-27/000367";
		Long currentPeriodTo = 1785695400000L;

		WaterBillingCycle expected = cycle("CYCLE-002", tenantId, connectionNo, 1783017000000L, 1785695400000L);

		String sql = "SELECT previous OK billing cycle";

		when(queryBuilder.findPreviousOkByConnectionBefore()).thenReturn(sql);
		when(jdbcTemplate.query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(currentPeriodTo)))
				.thenReturn(Collections.singletonList(expected));

		WaterBillingCycle actual = dao.findPreviousOkByConnectionBefore(tenantId, connectionNo, currentPeriodTo);

		assertEquals(expected, actual);

		verify(jdbcTemplate).query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(currentPeriodTo));
	}

	@Test
	void shouldReturnNullWhenNoPreviousOkCycleExists() {
		String tenantId = "dl";
		String connectionNo = "WS/DJB/2026-27/000367";
		Long currentPeriodTo = 1785695400000L;

		String sql = "SELECT previous OK billing cycle";

		when(queryBuilder.findPreviousOkByConnectionBefore()).thenReturn(sql);
		when(jdbcTemplate.query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo), eq(currentPeriodTo)))
				.thenReturn(Collections.<WaterBillingCycle>emptyList());

		WaterBillingCycle actual = dao.findPreviousOkByConnectionBefore(tenantId, connectionNo, currentPeriodTo);

		assertNull(actual);
	}

	@Test
	void shouldFindLatestCycleForConnection() {
		String tenantId = "dl";
		String connectionNo = "WS/DJB/2026-27/000367";

		WaterBillingCycle expected = cycle("CYCLE-003", tenantId, connectionNo, 1785695400000L, 1788373800000L);

		String sql = "SELECT latest billing cycle";

		when(queryBuilder.findLatestByConnection()).thenReturn(sql);
		when(jdbcTemplate.query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo)))
				.thenReturn(Collections.singletonList(expected));

		WaterBillingCycle actual = dao.findLatestByConnection(tenantId, connectionNo);

		assertEquals(expected, actual);

		verify(jdbcTemplate).query(eq(sql), eq(rowMapper), eq(tenantId), eq(connectionNo));
	}

	private WaterBillingCycle cycle(String id, String tenantId, String connectionNo, Long from, Long to) {

		WaterBillingCycle cycle = new WaterBillingCycle();
		cycle.setId(id);
		cycle.setTenantid(tenantId);
		cycle.setConnectionno(connectionNo);
		cycle.setBillingperiodfrom(from);
		cycle.setBillingperiodto(to);
		return cycle;
	}
}
