-- Northwind Route 2: 600 USDT outbound on Polygon Amoy after every inbound credit
-- Amount stored in USD cents (60,000 = $600). PolygonOutboundAdapter converts
-- to USDT minor units (× 10,000) before broadcasting.
INSERT INTO routes (account_id, name, is_active, destination_type, destination_ref,
                    amount_strategy, amount_value, provider, currency)
VALUES ('00000000-0000-0000-0000-000000000011',
        'Auto-USDT to Polygon Vendor', true, 'crypto_polygon',
        '0x742d35Cc6634C0532925a3b8D4C9A1B742d35Cc',
        'fixed', 60000, 'polygon', 'USD');
