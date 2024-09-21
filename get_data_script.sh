#!/bin/bash

DB_HOST="phone_shop_db"
DB_PORT="5432"
DB_NAME="PhoneShop"
DB_USER="postgres"
DB_PASSWORD="0000"

QUERY="SELECT
    CONCAT(
        b.name, ' ',
        pd.name, ' ',
        pd.series, ' - ',
        p.amount_of_built_in_memory, 'GB ROM, ',
        p.amount_of_ram, 'GB RAM'
    ) AS phone_name,
    c.description, c.created, c.grade
FROM
    public.phone p
JOIN
    public.phone_description pd ON p.phone_description_id = pd.id
JOIN
    public.brand b ON pd.brand_id = b.id
LEFT JOIN
    public.comment c ON p.id = c.phone_id
WHERE
    c.description IS NOT NULL
ORDER BY
    phone_name"

OUTPUT_FILE="output.csv"

PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -U "$DB_USER" -c "\copy ($QUERY) TO '$OUTPUT_FILE' WITH CSV"