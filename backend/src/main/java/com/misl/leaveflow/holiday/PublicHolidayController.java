package com.misl.leaveflow.holiday;

import com.misl.leaveflow.holiday.dto.HolidayRequest;
import com.misl.leaveflow.holiday.dto.HolidayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Public holidays")
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class PublicHolidayController {

    private final PublicHolidayService holidayService;
    private final Clock clock;

    @Operation(summary = "Holidays of a year (defaults to the current year)")
    @GetMapping
    public List<HolidayResponse> findByYear(@RequestParam(required = false) Integer year) {
        return holidayService.findByYear(year != null ? year : LocalDate.now(clock).getYear());
    }

    @Operation(summary = "Add a holiday")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidayService.create(request));
    }

    @Operation(summary = "Edit a holiday")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public HolidayResponse update(@PathVariable Long id, @Valid @RequestBody HolidayRequest request) {
        return holidayService.update(id, request);
    }

    @Operation(summary = "Delete a holiday")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
