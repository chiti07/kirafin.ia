-- Kira system accounts (fixed UUIDs for deterministic reference)
INSERT INTO accounts (id, is_omnibus, client_name, currency) VALUES
  ('00000000-0000-0000-0000-000000000001', false, 'Kira Platform Fees',    'USD'),
  ('00000000-0000-0000-0000-000000000002', false, 'Kira Liquidity Pool',   'USD'),
  ('00000000-0000-0000-0000-000000000003', false, 'Kira Crypto Suspense',  'USD');

-- Northwind Coffee Co. — omnibus + main sub-client
INSERT INTO accounts (id, is_omnibus, client_name, currency) VALUES
  ('00000000-0000-0000-0000-000000000010', true, 'Northwind Coffee Co.', 'USD');

INSERT INTO accounts (id, parent_account_id, is_omnibus, client_name, currency) VALUES
  ('00000000-0000-0000-0000-000000000011',
   '00000000-0000-0000-0000-000000000010',
   false, 'Northwind Coffee Co. - Main', 'USD');

-- Northwind Route 1: auto-ACH $4,200 to vendor on any inbound credit
INSERT INTO routes (account_id, name, is_active, destination_type, destination_ref,
                    amount_strategy, amount_value, provider, currency)
VALUES ('00000000-0000-0000-0000-000000000011',
        'Auto-ACH to Vendor', true, 'fiat_ach',
        'routing=021000021;account=987654321',
        'fixed', 420000, 'simple_rail', 'USD');
