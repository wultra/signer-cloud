INSERT INTO sc_signer (id, timestamp_created, external_signer_id, user_id, csr, certificate, timestamp_certificate_expiration, status) VALUES
    (1, '2020-01-01 00:00:00', 'signer1', 'user1', '1', '1', '2020-01-01 00:00:00', 'ACTIVE');

INSERT INTO sc_callback(id, callback_url, callback_type) VALUES
    (1, 'https://www.example.com', 'EXPIRED');