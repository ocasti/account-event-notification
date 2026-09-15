INSERT INTO subscriptions (id, client_id, event_keys, url, event_signature_key, active, created_at) VALUES
    ('sub_client001', 'CLIENT001', ARRAY['*'], '${flyway.placeholders.webhookUrl}', 'local-signing-key-client001', TRUE, NOW()),
    ('sub_client002', 'CLIENT002', ARRAY['*'], '${flyway.placeholders.webhookUrl}', 'local-signing-key-client002', TRUE, NOW()),
    ('sub_client003', 'CLIENT003', ARRAY['*'], '${flyway.placeholders.webhookUrl}', 'local-signing-key-client003', TRUE, NOW());
