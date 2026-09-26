-- Demo organisation used for local runs and the assessment demo.
--   Admin:    admin@leaveflow.com / Admin@123
--   Everyone else:                  Password@123
-- Hierarchy: Admin -> 2 managers -> their team members.
-- The password hashes are BCrypt (cost 10).

INSERT INTO employees (full_name, email, password_hash, role, department, designation, manager_id, joining_date)
VALUES ('Nusrat Jahan', 'admin@leaveflow.com',
        '$2a$10$bIWDnZeI6CE.EU/72S3qJezIdUcyORLaT1edRyRWdf67a9JZLjLLu',
        'ADMIN', 'Human Resources', 'HR Administrator', NULL, '2021-03-01');

INSERT INTO employees (full_name, email, password_hash, role, department, designation, manager_id, joining_date)
VALUES ('Tanvir Ahmed', 'tanvir.ahmed@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'MANAGER', 'Engineering', 'Engineering Manager',
        (SELECT id FROM employees WHERE email = 'admin@leaveflow.com'), '2021-06-15'),
       ('Farhana Rahman', 'farhana.rahman@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'MANAGER', 'Operations', 'Operations Manager',
        (SELECT id FROM employees WHERE email = 'admin@leaveflow.com'), '2022-01-10');

INSERT INTO employees (full_name, email, password_hash, role, department, designation, manager_id, joining_date)
VALUES ('Rakib Hasan', 'rakib.hasan@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Engineering', 'Senior Software Engineer',
        (SELECT id FROM employees WHERE email = 'tanvir.ahmed@leaveflow.com'), '2022-04-03'),
       ('Sadia Islam', 'sadia.islam@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Engineering', 'Software Engineer',
        (SELECT id FROM employees WHERE email = 'tanvir.ahmed@leaveflow.com'), '2023-02-12'),
       ('Mahmud Hossain', 'mahmud.hossain@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Engineering', 'QA Engineer',
        (SELECT id FROM employees WHERE email = 'tanvir.ahmed@leaveflow.com'), '2023-08-20'),
       ('Nabila Chowdhury', 'nabila.chowdhury@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Engineering', 'Junior Software Engineer',
        (SELECT id FROM employees WHERE email = 'tanvir.ahmed@leaveflow.com'), '2025-01-05'),
       ('Arif Khan', 'arif.khan@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Operations', 'Operations Executive',
        (SELECT id FROM employees WHERE email = 'farhana.rahman@leaveflow.com'), '2022-09-18'),
       ('Tasnim Akter', 'tasnim.akter@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Operations', 'Business Analyst',
        (SELECT id FROM employees WHERE email = 'farhana.rahman@leaveflow.com'), '2024-03-11'),
       ('Imran Kabir', 'imran.kabir@leaveflow.com',
        '$2a$10$Kj4S4pWYK.QImD7RD7y/S.4BavZutIKgi.pGqC48LfZF4ERgoyu5S',
        'EMPLOYEE', 'Operations', 'Support Specialist',
        (SELECT id FROM employees WHERE email = 'farhana.rahman@leaveflow.com'), '2024-11-02');
