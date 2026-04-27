CREATE KEYSPACE IF NOT EXISTS online_shopping_order
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

USE online_shopping_order;

CREATE TYPE IF NOT EXISTS address_snapshots (
  recipient_name text,
  address_line1 text,
  address_line2 text,
  city text,
  state text,
  postal_code text,
  country text,
  phone_number text
);

CREATE TYPE IF NOT EXISTS order_line_items (
  item_id text,
  sku text,
  item_name text,
  upc text,
  quantity int,
  unit_price decimal,
  line_total decimal
);

CREATE TYPE IF NOT EXISTS cart_items (
  item_id text,
  sku text,
  item_name text,
  upc text,
  quantity int,
  unit_price decimal
);

CREATE TABLE IF NOT EXISTS shopping_orders (
  customer_id bigint,
  created_at timestamp,
  order_id uuid,
  order_number text,
  status text,
  status_reason text,
  currency_code text,
  subtotal_amount decimal,
  tax_amount decimal,
  shipping_amount decimal,
  discount_amount decimal,
  total_amount decimal,
  shipping_address frozen<address_snapshots>,
  billing_address frozen<address_snapshots>,
  items list<frozen<order_line_items>>,
  create_request_id text,
  last_update_request_id text,
  cancel_request_id text,
  payment_reference text,
  completion_reference text,
  version int,
  updated_at timestamp,
  cancelled_at timestamp,
  paid_at timestamp,
  completed_at timestamp,
  PRIMARY KEY ((customer_id), created_at, order_id)
) WITH CLUSTERING ORDER BY (created_at DESC, order_id ASC);

CREATE TABLE IF NOT EXISTS shopping_carts (
  customer_id bigint PRIMARY KEY,
  cart_id text,
  status text,
  items list<frozen<cart_items>>,
  created_at timestamp,
  updated_at timestamp,
  checked_out_at timestamp
);
