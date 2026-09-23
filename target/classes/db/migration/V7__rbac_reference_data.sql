-- =============================================================================
-- V7: roles, permissions and the default role -> permission grants.
--
-- These are defaults. Administrators can change grants from the Roles screen
-- (audited), and each client decides which roles are enabled in configuration.
-- =============================================================================

INSERT INTO roles (code, name, description, display_order) VALUES
    ('DIRECTORS',      'Directors',      'Read-only oversight of the whole centre',            1),
    ('ADMINISTRATIVE', 'Administrative', 'Full administration: users, settings and all data',  2),
    ('SALES',          'Sales',          'Admissions: enquiries, new students and parents',     3),
    ('ACCOUNTS',       'Accounts',       'Fees, payments and fine collection',                  4),
    ('ACADEMICS',      'Academics',      'Academic administration across all batches',          5),
    ('STUDENTS',       'Students',       'Student self-service portal (own data only)',          6),
    ('MENTORS',        'Mentors',        'Mentoring of assigned batches and students',          7),
    ('FACULTY',        'Faculty',        'Teaching staff and guest teachers',                   8);

INSERT INTO permissions (code, name, module_code) VALUES
    -- Platform
    ('DASHBOARD_VIEW',            'View dashboard',                      'PLATFORM'),
    -- Administration
    ('USER_VIEW',                 'View users',                          'ADMINISTRATION'),
    ('USER_MANAGE',               'Create and update users',             'ADMINISTRATION'),
    ('ROLE_VIEW',                 'View roles and permissions',          'ADMINISTRATION'),
    ('ROLE_MANAGE',               'Change role permissions',             'ADMINISTRATION'),
    ('SETTINGS_VIEW',             'View configuration',                  'ADMINISTRATION'),
    ('AUDIT_VIEW',                'View audit log',                      'ADMINISTRATION'),
    ('PARENT_VIEW',               'View parents',                        'ADMINISTRATION'),
    ('PARENT_MANAGE',             'Create and update parents',           'ADMINISTRATION'),
    -- Academics: people
    ('STUDENT_VIEW',              'View all students',                   'ACADEMICS'),
    ('ASSIGNED_STUDENT_VIEW',     'View students of assigned batches',   'ACADEMICS'),
    ('STUDENT_CREATE',            'Create students',                     'ACADEMICS'),
    ('STUDENT_UPDATE',            'Update students',                     'ACADEMICS'),
    ('MENTOR_VIEW',               'View mentors',                        'ACADEMICS'),
    ('MENTOR_MANAGE',             'Create and update mentors',           'ACADEMICS'),
    ('FACULTY_VIEW',              'View faculty',                        'ACADEMICS'),
    ('FACULTY_MANAGE',            'Create and update faculty',           'ACADEMICS'),
    -- Academics: master data
    ('COURSE_VIEW',               'View courses',                        'ACADEMICS'),
    ('COURSE_CREATE',             'Create courses',                      'ACADEMICS'),
    ('COURSE_UPDATE',             'Update courses',                      'ACADEMICS'),
    ('SUBJECT_VIEW',              'View subjects',                       'ACADEMICS'),
    ('SUBJECT_CREATE',            'Create subjects',                     'ACADEMICS'),
    ('SUBJECT_UPDATE',            'Update subjects',                     'ACADEMICS'),
    ('ACADEMIC_YEAR_VIEW',        'View academic years',                 'ACADEMICS'),
    ('ACADEMIC_YEAR_MANAGE',      'Create and update academic years',    'ACADEMICS'),
    ('BATCH_VIEW',                'View batches',                        'ACADEMICS'),
    ('BATCH_CREATE',              'Create batches',                      'ACADEMICS'),
    ('BATCH_UPDATE',              'Update batches',                      'ACADEMICS'),
    ('MY_BATCH_VIEW',             'View own batches',                    'ACADEMICS'),
    ('MASTER_DATA_MANAGE',        'Manage discipline and exam types',    'ACADEMICS'),
    -- Academics: operations
    ('SCHEDULE_VIEW',             'View class schedule',                 'ACADEMICS'),
    ('SCHEDULE_CREATE',           'Create schedule entries',             'ACADEMICS'),
    ('SCHEDULE_UPDATE',           'Update schedule entries',             'ACADEMICS'),
    ('MY_SCHEDULE_VIEW',          'View own schedule',                   'ACADEMICS'),
    ('ATTENDANCE_VIEW',           'View attendance',                     'ACADEMICS'),
    ('ATTENDANCE_CREATE',         'Mark attendance',                     'ACADEMICS'),
    ('ATTENDANCE_UPDATE',         'Correct attendance',                  'ACADEMICS'),
    ('DISCIPLINE_VIEW',           'View discipline records',             'ACADEMICS'),
    ('DISCIPLINE_CREATE',         'Record discipline',                   'ACADEMICS'),
    ('DISCIPLINE_UPDATE',         'Update discipline records',           'ACADEMICS'),
    ('FINE_VIEW',                 'View fines',                          'ACADEMICS'),
    ('FINE_CREATE',               'Create fines',                        'ACADEMICS'),
    ('FINE_UPDATE',               'Update, collect or waive fines',      'ACADEMICS'),
    ('TEST_VIEW',                 'View tests',                          'ACADEMICS'),
    ('TEST_CREATE',               'Create tests',                        'ACADEMICS'),
    ('TEST_MARKS_ENTRY',          'Enter test marks',                    'ACADEMICS'),
    ('TEST_PUBLISH',              'Review and publish test results',     'ACADEMICS'),
    ('EXAM_VIEW',                 'View exams',                          'ACADEMICS'),
    ('EXAM_CREATE',               'Create exams',                        'ACADEMICS'),
    ('EXAM_MARKS_ENTRY',          'Enter exam marks',                    'ACADEMICS'),
    ('EXAM_PUBLISH',              'Review and publish exam results',     'ACADEMICS'),
    ('PERFORMANCE_VIEW',          'View performance',                    'ACADEMICS'),
    ('PROGRESS_CARD_VIEW',        'View progress cards',                 'ACADEMICS'),
    ('PROGRESS_CARD_CREATE',      'Create progress cards',               'ACADEMICS'),
    ('PROGRESS_CARD_PUBLISH',     'Review and publish progress cards',   'ACADEMICS'),
    ('SYLLABUS_VIEW',             'View syllabus progress',              'ACADEMICS'),
    ('SYLLABUS_MANAGE',           'Manage syllabus topics',              'ACADEMICS'),
    ('SYLLABUS_UPDATE',           'Update syllabus progress',            'ACADEMICS'),
    ('PARENT_MEETING_VIEW',       'View parent meetings',                'ACADEMICS'),
    ('PARENT_MEETING_CREATE',     'Record parent meetings',              'ACADEMICS'),
    ('CLASS_REGISTER_VIEW',       'View class register',                 'ACADEMICS'),
    ('CLASS_REGISTER_CREATE',     'Record class register',               'ACADEMICS'),
    ('FACULTY_ENTRY_EXIT_VIEW',   'View faculty entry and exit',         'ACADEMICS'),
    ('FACULTY_ENTRY_EXIT_CREATE', 'Record faculty entry and exit',       'ACADEMICS'),
    -- Fees
    ('FEE_VIEW',                  'View fees',                           'FEES'),
    ('FEE_MANAGE',                'Create and update fee plans',         'FEES'),
    ('PAYMENT_CREATE',            'Record payments',                     'FEES'),
    -- Notifications
    ('NOTIFICATION_VIEW',         'View message log',                    'NOTIFICATIONS'),
    ('NOTIFICATION_MANAGE',       'Retry and test messages',             'NOTIFICATIONS'),
    -- Reports
    ('REPORT_VIEW',               'View reports',                        'REPORTS'),
    -- Student portal (own data only)
    ('MY_PROFILE_VIEW',           'View own profile',                    'STUDENT_PORTAL'),
    ('MY_ATTENDANCE_VIEW',        'View own attendance',                 'STUDENT_PORTAL'),
    ('MY_TEST_RESULTS_VIEW',      'View own test results',               'STUDENT_PORTAL'),
    ('MY_EXAM_RESULTS_VIEW',      'View own exam results',               'STUDENT_PORTAL'),
    ('MY_PERFORMANCE_VIEW',       'View own performance',                'STUDENT_PORTAL'),
    ('MY_PROGRESS_CARD_VIEW',     'View own progress cards',             'STUDENT_PORTAL'),
    ('MY_DISCIPLINE_VIEW',        'View own discipline records',         'STUDENT_PORTAL'),
    ('MY_FINE_VIEW',              'View own fines',                      'STUDENT_PORTAL'),
    ('MY_FEE_VIEW',               'View own fees',                       'STUDENT_PORTAL'),
    ('MY_SYLLABUS_VIEW',          'View own batch syllabus',             'STUDENT_PORTAL'),
    ('MY_NOTIFICATION_VIEW',      'View own notifications',              'STUDENT_PORTAL');

-- ADMINISTRATIVE: everything except the self-service ("MY_") permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'ADMINISTRATIVE' AND p.code NOT LIKE 'MY\_%';

-- DIRECTORS: read-only across the centre.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'DIRECTORS'
  AND p.code NOT LIKE 'MY\_%'
  AND (p.code LIKE '%\_VIEW' OR p.code = 'DASHBOARD_VIEW');

-- ACADEMICS: all academic administration plus read access to related areas.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'ACADEMICS'
  AND ((p.module_code = 'ACADEMICS' AND p.code NOT IN ('MY_BATCH_VIEW', 'MY_SCHEDULE_VIEW'))
       OR p.code IN ('DASHBOARD_VIEW', 'PARENT_VIEW', 'PARENT_MANAGE', 'FEE_VIEW',
                     'NOTIFICATION_VIEW', 'REPORT_VIEW'));

-- ACCOUNTS: fees, payments and fine collection.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'ACCOUNTS'
  AND p.code IN ('DASHBOARD_VIEW', 'STUDENT_VIEW', 'PARENT_VIEW', 'COURSE_VIEW', 'BATCH_VIEW',
                 'ACADEMIC_YEAR_VIEW', 'FEE_VIEW', 'FEE_MANAGE', 'PAYMENT_CREATE', 'FINE_VIEW',
                 'FINE_UPDATE', 'REPORT_VIEW', 'NOTIFICATION_VIEW');

-- SALES: admissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SALES'
  AND p.code IN ('DASHBOARD_VIEW', 'STUDENT_VIEW', 'STUDENT_CREATE', 'STUDENT_UPDATE', 'PARENT_VIEW',
                 'PARENT_MANAGE', 'COURSE_VIEW', 'SUBJECT_VIEW', 'BATCH_VIEW', 'ACADEMIC_YEAR_VIEW',
                 'FEE_VIEW');

-- MENTORS: academic operations for their assigned batches (enforced by data scope).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'MENTORS'
  AND p.code IN ('DASHBOARD_VIEW', 'ASSIGNED_STUDENT_VIEW', 'MY_BATCH_VIEW', 'BATCH_VIEW',
                 'COURSE_VIEW', 'SUBJECT_VIEW', 'ACADEMIC_YEAR_VIEW', 'SCHEDULE_VIEW',
                 'ATTENDANCE_VIEW', 'ATTENDANCE_CREATE', 'ATTENDANCE_UPDATE',
                 'DISCIPLINE_VIEW', 'DISCIPLINE_CREATE', 'DISCIPLINE_UPDATE',
                 'FINE_VIEW', 'FINE_CREATE',
                 'TEST_VIEW', 'TEST_CREATE', 'TEST_MARKS_ENTRY', 'TEST_PUBLISH',
                 'EXAM_VIEW', 'PERFORMANCE_VIEW',
                 'PROGRESS_CARD_VIEW', 'PROGRESS_CARD_CREATE', 'PROGRESS_CARD_PUBLISH',
                 'PARENT_MEETING_VIEW', 'PARENT_MEETING_CREATE', 'SYLLABUS_VIEW');

-- FACULTY: teaching operations for their assigned batches and subjects.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'FACULTY'
  AND p.code IN ('DASHBOARD_VIEW', 'MY_SCHEDULE_VIEW', 'MY_BATCH_VIEW', 'ASSIGNED_STUDENT_VIEW',
                 'BATCH_VIEW', 'COURSE_VIEW', 'SUBJECT_VIEW', 'SCHEDULE_VIEW',
                 'CLASS_REGISTER_VIEW', 'CLASS_REGISTER_CREATE',
                 'FACULTY_ENTRY_EXIT_VIEW', 'FACULTY_ENTRY_EXIT_CREATE',
                 'TEST_VIEW', 'TEST_MARKS_ENTRY', 'EXAM_VIEW', 'EXAM_MARKS_ENTRY',
                 'SYLLABUS_VIEW', 'SYLLABUS_UPDATE');

-- STUDENTS: read-only access to their own records.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'STUDENTS'
  AND p.module_code = 'STUDENT_PORTAL';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'STUDENTS' AND p.code = 'MY_SCHEDULE_VIEW';
