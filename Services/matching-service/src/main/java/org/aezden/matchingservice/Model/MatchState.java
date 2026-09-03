package org.aezden.matchingservice.Model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.aezden.matchingservice.Dto.DriverDto;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter @Setter
public class MatchState {
    private UUID tripId;
    private List<DriverDto> selectedDrivers;
    private int currentDriver;
    private UUID currentOfferId;
}
