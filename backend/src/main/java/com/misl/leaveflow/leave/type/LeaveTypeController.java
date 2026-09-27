package com.misl.leaveflow.leave.type;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Leave types")
@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeRepository leaveTypeRepository;

    @Operation(summary = "All leave types with their yearly quota")
    @GetMapping
    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> findAll() {
        return leaveTypeRepository.findAllByOrderByIdAsc().stream().map(LeaveTypeResponse::from).toList();
    }
}
