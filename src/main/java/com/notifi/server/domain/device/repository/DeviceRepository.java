package com.notifi.server.domain.device.repository;

import com.notifi.server.domain.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceUid(String deviceUid);
    List<Device> findByCareTargetIdOrderByRegisteredAtAsc(Long careTargetId);
    boolean existsByDeviceUid(String deviceUid);

    @Query("SELECT d.careTargetId, COUNT(d) FROM Device d WHERE d.careTargetId IN :ids GROUP BY d.careTargetId")
    List<Object[]> countByCareTargetIds(@Param("ids") List<Long> ids);

    default Map<Long, Integer> deviceCountMap(List<Long> careTargetIds) {
        if (careTargetIds.isEmpty()) return Map.of();
        return countByCareTargetIds(careTargetIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }
}
