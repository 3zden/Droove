package org.aezden.tripservice.Services;


import org.aezden.tripservice.Entities.TripStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;


@Component
public class TransitionService {
    Map<TripStatus, Set<TripStatus>> transitionMap = Map.of(
            TripStatus.REQUESTED, Set.of(
                    TripStatus.MATCHING,
                    TripStatus.CANCELLED,
                    TripStatus.NO_DRIVER),
            TripStatus.MATCHING, Set.of(
                    TripStatus.CANCELLED,
                    TripStatus.MATCHED
            ),
            TripStatus.MATCHED, Set.of(
                    TripStatus.CANCELLED,
                    TripStatus.DRIVER_ARRIVED
            ),
            TripStatus.DRIVER_ARRIVED, Set.of(
                    TripStatus.CANCELLED,
                    TripStatus.ONGOING
            ),
            TripStatus.ONGOING, Set.of(
                    TripStatus.COMPLETED,
                    TripStatus.CANCELLED
            ),
            TripStatus.NO_DRIVER, Set.of(
                    TripStatus.CANCELLED,
                    TripStatus.MATCHING
            )
    );
    public boolean isValid(TripStatus status, TripStatus currentStatus){
        return transitionMap.containsKey(currentStatus) && transitionMap.get(currentStatus).contains(status);
    }
}
