-- Leave types with yearly quotas (in working days). NULL quota = unlimited.
INSERT INTO leave_types (code, name, description, annual_quota, color) VALUES
    ('ANNUAL', 'Annual Leave', 'Planned time off, e.g. vacations', 15, '#2563eb'),
    ('SICK',   'Sick Leave',   'Illness or medical appointments', 14, '#dc2626'),
    ('CASUAL', 'Casual Leave', 'Short notice personal matters',   10, '#d97706'),
    ('UNPAID', 'Unpaid Leave', 'Leave without pay, no yearly limit', NULL, '#6b7280');

-- Sample Bangladesh public holidays. Fixed-date holidays are exact; dates of lunar holidays
-- (Eid, Ashura, Durga Puja) are the expected dates and can be corrected by an admin in the app.
INSERT INTO public_holidays (holiday_date, name) VALUES
    ('2026-02-21', 'Shaheed Day & International Mother Language Day'),
    ('2026-03-20', 'Eid ul-Fitr (holiday)'),
    ('2026-03-21', 'Eid ul-Fitr'),
    ('2026-03-22', 'Eid ul-Fitr (holiday)'),
    ('2026-03-26', 'Independence Day'),
    ('2026-04-14', 'Pohela Boishakh (Bengali New Year)'),
    ('2026-05-01', 'May Day'),
    ('2026-05-26', 'Eid ul-Adha (holiday)'),
    ('2026-05-27', 'Eid ul-Adha'),
    ('2026-05-28', 'Eid ul-Adha (holiday)'),
    ('2026-06-26', 'Ashura'),
    ('2026-10-20', 'Durga Puja (Bijoya Dashami)'),
    ('2026-12-16', 'Victory Day'),
    ('2026-12-25', 'Christmas Day'),
    ('2027-02-21', 'Shaheed Day & International Mother Language Day'),
    ('2027-03-26', 'Independence Day'),
    ('2027-04-14', 'Pohela Boishakh (Bengali New Year)'),
    ('2027-05-01', 'May Day'),
    ('2027-12-16', 'Victory Day'),
    ('2027-12-25', 'Christmas Day');
