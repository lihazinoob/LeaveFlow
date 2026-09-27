package com.misl.leaveflow.holiday;

import com.misl.leaveflow.common.exception.ConflictException;
import com.misl.leaveflow.common.exception.ResourceNotFoundException;
import com.misl.leaveflow.holiday.dto.HolidayRequest;
import com.misl.leaveflow.holiday.dto.HolidayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Public holiday management. Changing holidays affects future leave requests only:
 * the working-day count of an existing request is fixed when it is submitted or edited.
 */
@Service
@RequiredArgsConstructor
public class PublicHolidayService {

    private final PublicHolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<HolidayResponse> findByYear(int year) {
        return holidayRepository.findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .map(HolidayResponse::from)
                .toList();
    }

    @Transactional
    public HolidayResponse create(HolidayRequest request) {
        if (holidayRepository.existsByDate(request.date())) {
            throw new ConflictException("A holiday already exists on " + request.date());
        }
        PublicHoliday holiday = new PublicHoliday();
        holiday.setDate(request.date());
        holiday.setName(request.name().trim());
        return HolidayResponse.from(holidayRepository.save(holiday));
    }

    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request) {
        PublicHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
        if (holidayRepository.existsByDateAndIdNot(request.date(), id)) {
            throw new ConflictException("A holiday already exists on " + request.date());
        }
        holiday.setDate(request.date());
        holiday.setName(request.name().trim());
        return HolidayResponse.from(holiday);
    }

    @Transactional
    public void delete(Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday", id);
        }
        holidayRepository.deleteById(id);
    }
}
