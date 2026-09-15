package org.egov.wscalculation.djbmonthlybilling.web.model;

import java.util.List;

import org.egov.common.contract.response.ResponseInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DJBZroVerificationSearchResponse {

    private ResponseInfo responseInfo;
    private List<DJBZroVerificationInboxItem> cases;
    private Integer count;
}
