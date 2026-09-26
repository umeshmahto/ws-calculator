package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.math.BigDecimal;
import java.util.List;

import org.egov.common.contract.response.ResponseInfo;
import org.egov.wscalculation.djbmonthlybilling.service.dto.DJBPenaltyItem;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBPenaltyCalculationResponse {
    private ResponseInfo responseInfo;
    private String tenantId;
    private String connectionNo;
    private BigDecimal totalPenalty;
    private List<DJBPenaltyItem> penaltyItems;
    private String explanation;
}
