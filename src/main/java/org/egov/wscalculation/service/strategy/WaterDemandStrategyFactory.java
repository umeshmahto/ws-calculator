package org.egov.wscalculation.service.strategy;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Selects one and only one strategy for the revised DJB WaterDemandNorms2 group. */
@Component
@RequiredArgsConstructor
public class WaterDemandStrategyFactory {

    private final List<GroupDemandStrategy> strategies;

    public GroupDemandStrategy getStrategy(String subCategoryCode, String parentUsageCode) {
        List<GroupDemandStrategy> matches = strategies.stream()
                .filter(strategy -> strategy.supports(subCategoryCode, parentUsageCode))
                .collect(Collectors.toList());

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple water-demand strategies matched norm code '"
                    + subCategoryCode + "' and parentUsageCode '" + parentUsageCode
                    + "'. Each revised norm must resolve to exactly one strategy.");
        }

        throw new IllegalStateException("No water-demand strategy supports norm code '" + subCategoryCode
                + "' and parentUsageCode '" + parentUsageCode
                + "'. Supported revised norms are A, B-1..B-4 and C-1..C-4. "
                + "Remove legacy Group D-J norm entries and correct the MDMS parentUsageCode mapping.");
    }
}
