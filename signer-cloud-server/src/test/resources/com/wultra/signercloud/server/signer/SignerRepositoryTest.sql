INSERT INTO sc_signer (id, timestamp_created, external_signer_id, user_id, csr, certificate, timestamp_certificate_expiration, status) VALUES
    (1, '2020-01-01 00:00:00', '3', 1, '1', '1', '2099-01-01 00:00:00', 'ACTIVE'),
    (2, '2020-01-01 00:00:00', '2', 1, '1', '1', '2020-01-01 00:00:00', 'BLOCKED'),
    (3, '2020-01-01 00:00:00', '1', 1, '1', '1', '2020-01-01 00:00:00', 'ACTIVE'),
    (4, '2020-01-01 00:00:00', '4', 1, '1', '1', '2020-01-01 00:00:00', 'REMOVED');
