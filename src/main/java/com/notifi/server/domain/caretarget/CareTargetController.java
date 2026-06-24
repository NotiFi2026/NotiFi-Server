package com.notifi.server.domain.caretarget;

import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care-targets")
@RequiredArgsConstructor
public class CareTargetController {

    private final CareTargetService careTargetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareTargetCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CareTargetCreateRequest request
    ) {
        return ApiResponse.success(careTargetService.register(userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<CareTargetSummaryResponse>> getMyCareTargets(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(careTargetService.getMyCareTargets(userId, pageable));
    }
}
