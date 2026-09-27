package com.misl.leaveflow.holiday;

import com.misl.leaveflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A non-working day for the whole company. Leave days that fall on a holiday are not counted. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "public_holidays")
public class PublicHoliday extends BaseEntity {

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false, length = 100)
    private String name;
}
