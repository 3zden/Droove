package org.aezden.tripservice.Services;


import org.aezden.tripservice.Entities.TripStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;


@Component
public class TransitionService {
    Map<TripStatus, Set<TripStatus>> transitionMap = Map.of(
            TripStatus.REQUESTED, Set.of(
                    TripStatus.MATCHED,
                    TripStatus.CANCELLED,
                    TripStatus.NO_DRIVER),
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
                    TripStatus.CANCELLED
            )
    );
    public boolean isValid(TripStatus status, TripStatus currentStatus){
        return transitionMap.containsKey(status) && transitionMap.get(status).contains(currentStatus);
    }
}
