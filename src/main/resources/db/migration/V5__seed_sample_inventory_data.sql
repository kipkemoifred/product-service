-- =========================================================================
-- V5__seed_sample_inventory_data.sql
-- Seed baseline sample products and initial warehouse inventory stocks
-- Compatible with both PostgreSQL and H2 standard SQL
-- =========================================================================

-- Seed Product 1: Fresh Milk 500ml
INSERT INTO products (name, product_code, description, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Fresh Milk 500ml', 'MILK-500', 'Pasteurized whole fresh milk 500ml pouch', 'Dairy', 'PACKETS', 65.00, 'ZERO_RATED', FALSE, 0.00, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'MILK-500');

-- Seed Product 2: White Bread 400g
INSERT INTO products (name, product_code, description, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'White Bread 400g', 'BREAD-400', 'Sliced white sandwich loaf 400g', 'Bakery', 'PACKETS', 60.00, 'ZERO_RATED', FALSE, 0.00, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'BREAD-400');

-- Seed Product 3: Mineral Water 1L
INSERT INTO products (name, product_code, description, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Mineral Water 1L', 'WATER-1000', 'Bottled natural spring water 1000ml', 'Beverages', 'BOTTLES', 50.00, 'STANDARD', TRUE, 5.00, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'WATER-1000');

-- Seed Product 4: Cooking Oil 1L
INSERT INTO products (name, product_code, description, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Cooking Oil 1L', 'OIL-1000', 'Refined pure vegetable cooking oil 1 Litre', 'Groceries', 'LITRES', 240.00, 'STANDARD', FALSE, 0.00, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'OIL-1000');

-- Seed Initial Stock for Products
INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold, last_updated)
SELECT p.id, 100.00, 0.00, 20.00, CURRENT_TIMESTAMP
FROM products p
WHERE p.product_code = 'MILK-500'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks WHERE product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold, last_updated)
SELECT p.id, 150.00, 0.00, 30.00, CURRENT_TIMESTAMP
FROM products p
WHERE p.product_code = 'BREAD-400'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks WHERE product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold, last_updated)
SELECT p.id, 200.00, 0.00, 50.00, CURRENT_TIMESTAMP
FROM products p
WHERE p.product_code = 'WATER-1000'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks WHERE product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold, last_updated)
SELECT p.id, 80.00, 0.00, 15.00, CURRENT_TIMESTAMP
FROM products p
WHERE p.product_code = 'OIL-1000'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks WHERE product_id = p.id);
