package org.aezden.matchingservice.Service;

import lombok.RequiredArgsConstructor;
import org.aezden.matchingservice.Dto.DriverDto;
import org.aezden.matchingservice.Repo.DriverRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private DriverRepo driverRepo;

    public void match() {
    }
    public List<DriverDto> findNearsetDrivers(float lat, float lng){
        return driverRepo.findALl(lat, lng);
    }
}
