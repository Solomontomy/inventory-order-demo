CREATE TABLE products (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 sku VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK (price > 0),
 stock INTEGER NOT NULL CHECK (stock >= 0)
);
CREATE TABLE orders (
 id UUID PRIMARY KEY,
 reference VARCHAR(80) NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL CHECK (status IN ('CONFIRMED','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_lines (
 order_id UUID NOT NULL REFERENCES orders(id),
 product_id BIGINT NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL CHECK (quantity > 0),
 unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
 PRIMARY KEY(order_id,product_id)
);
CREATE INDEX order_lines_product_idx ON order_lines(product_id);
