-- cleanup_duplicates.sql
-- Run: Get-Content cleanup_duplicates.sql -Encoding UTF8 | sqlite3 output/faculty.db

-- 1. Remove duplicates, keep the row with the lowest id
DELETE FROM teachers WHERE id NOT IN (
    SELECT MIN(id) FROM teachers GROUP BY name, university
);

-- 2. Remove dirty data where university name was scraped as teacher name
DELETE FROM teachers WHERE name = university;
DELETE FROM teachers WHERE name LIKE '%大学' OR name LIKE '%学院'
    OR name LIKE '%研究所' OR name LIKE '%研究院' OR name LIKE '%实验室';

-- 3. Add unique index to prevent future duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_teacher_name_univ ON teachers(name, university);

-- 4. Verify
SELECT 'Total after cleanup: ' || COUNT(*) FROM teachers;
SELECT 'Remaining duplicates: ' || COUNT(*) FROM (
    SELECT name, university FROM teachers GROUP BY name, university HAVING COUNT(*) > 1
);