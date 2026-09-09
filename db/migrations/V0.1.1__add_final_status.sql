ALTER TABLE IF EXISTS pools ADD COLUMN IF NOT EXISTS final_status INTEGER;
ALTER TABLE IF EXISTS lists ADD COLUMN IF NOT EXISTS final_status INTEGER;
UPDATE pools SET final_status = 1;
UPDATE lists SET final_status = 1;
