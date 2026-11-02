-- Runs once when the postgres volume is first created. The lead API uses the 'orchbiz' database
-- (created via POSTGRES_DB); n8n keeps its own data in a separate database.
CREATE DATABASE n8n;
