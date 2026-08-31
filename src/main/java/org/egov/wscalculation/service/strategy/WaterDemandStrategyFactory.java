package org.egov.wscalculation.service.strategy;

import java.util.List;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaterDemandStrategyFactory {

    private final List<GroupDemandStrategy> strategies;

    public GroupDemandStrategy getStrategy(String subCategoryCode, String parentUsageCode) {
        return strategies.stream()
                .filter(s -> s.supports(subCategoryCode, parentUsageCode))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No dedicated group strategy found for code '{}' (Parent: '{}'). Falling back to Default Strategy.", subCategoryCode, parentUsageCode);
                    return strategies.stream()
                            .filter(s -> s.supports("DEFAULT", "DEFAULT"))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("DefaultGroupDemandStrategy missing in Spring Context."));
                });
    }
}